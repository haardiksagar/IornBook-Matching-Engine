package com.ironbook.matching_engine;

import com.ironbook.matching_engine.Book.OrderBook;
import com.ironbook.matching_engine.Log.LogReplayer;
import com.ironbook.matching_engine.Log.WriteAheadLog;
import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.Side;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The central orchestrator. Ties OrderBook, WriteAheadLog, and
 * LogReplayer together behind a SINGLE-THREADED SEQUENCER.
 *
 * ARCHITECTURE (the "LMAX Pattern"):
 * -----------------------------------
 * Multiple TCP client threads can call submitNewOrder() and
 * cancelOrder() simultaneously. But NONE of them touch the
 * OrderBook or WriteAheadLog directly. Instead, each call:
 *
 * 1) Builds a lightweight EngineCommand object
 * 2) Drops it into a shared LinkedBlockingQueue
 * 3) Returns immediately (fire-and-forget)
 *
 * A single dedicated "sequencer" thread sits in a tight loop,
 * pulling commands from the queue one at a time, in strict FIFO
 * order, and executing them
 *
 * command = queue.take(); // blocks until something arrives
 * log(command); // WAL write
 * execute(command); // OrderBook mutation
 *
 * Because only ONE thread ever reads or writes the OrderBook,
 * there is ZERO lock contention on the hot path. No synchronized,
 * no ConcurrentHashMap, no race conditions. Order of processing
 * is identical to order of logging, which is identical to replay
 * order on crash recovery. Determinism is automatic.
 */
public class MatchingEngine {

    private final OrderBook orderBook;
    private final WriteAheadLog writeAheadLog;
    private final AtomicLong sequenceCounter;

    // The shared queue: TCP threads PUT commands in, the sequencer
    // thread TAKEs them out. LinkedBlockingQueue is thread-safe by
    // design - multiple producers, single consumer.
    private final LinkedBlockingQueue<EngineCommand> commandQueue;
    /*
     * Think of a LinkedBlockingQueue as a Thread-Safe Conveyor Belt or Inbox Tray.
     * 
     * Here is what the three words mean in simple terms:
     * 
     * 1. Queue (First-In, First-Out)
     * Like a line at a grocery store checkout. Whoever drops an order ticket into
     * the box first gets
     * their order processed first.
     * 
     * 2. Linked (Expandable)
     * It uses a flexible chain in memory, meaning the inbox can stretch and grow to
     * hold thousands
     * of orders without running out of space.
     * 
     * 3. Blocking (The Superpower!)
     * This is why we use it instead of a normal list:
     * 
     * (a). No Fighting: 10 worker threads can drop tickets into it at the exact
     * same millisecond without
     * crashing or corrupting data.
     * 
     * (b). Smart Sleep (take()): If the box is completely empty, the Sequencer
     * thread automatically
     * pauses ("blocks") and goes to sleep! The exact millisecond a worker thread
     * drops a new order
     * into the box, the queue wakes the Sequencer up. This saves CPU power because
     * the thread isn't
     * spinning in an empty loop!
     */

    // The single sequencer thread
    private final Thread sequencerThread;
    private volatile boolean running = false;

    public MatchingEngine(String logFilePath) throws IOException {
        this.orderBook = new OrderBook();
        this.commandQueue = new LinkedBlockingQueue<>();

        // STEP 1: Replay history FIRST, before starting the sequencer.
        // This runs on the constructor's thread (single-threaded) and
        // rebuilds the OrderBook state from the WAL. No queue involved.
        LogReplayer replayer = new LogReplayer();
        long maxSequenceSeen = replayer.replay(logFilePath, orderBook);

        // STEP 2: Seed the counter past any historical sequence numbers.
        this.sequenceCounter = new AtomicLong(maxSequenceSeen + 1);

        // STEP 3: Open the WAL for new writes going forward.
        this.writeAheadLog = new WriteAheadLog(logFilePath);

        // STEP 4: Start the sequencer thread. From this moment on,
        // ALL mutations to the OrderBook happen on this one thread.
        this.running = true;
        this.sequencerThread = new Thread(this::sequencerLoop, "sequencer");
        this.sequencerThread.setDaemon(true); // won't prevent JVM shutdown
        this.sequencerThread.start();

        /* ****this.sequencerThread.setDaemon(true);****
        
         * In Java, there are two types of threads: User Threads (the VIPs) and
         * Daemon Threads (the background helpers).
         * 
         * By default, Java will absolutely refuse to close your program if there
         * is even a single User Thread still running.
         * 
         * Because our Sequencer Thread runs in an infinite loop (while(running)),
         * if we left it as a normal User Thread, the program would hang forever
         * and refuse to shut down when you try to exit!
         * 
         * By calling setDaemon(true), we are telling Java:
         * "This thread is just a background helper. If the main program finishes
         * or we try to close the app, do not wait for this loop to finish! Just
         * kill it immediately and let the program shut down cleanly."
         */
    }

    // ================================================================
    // PUBLIC API — called by TCP threads (or tests)
    // These methods NEVER touch OrderBook or WAL directly.
    // They just build a command and drop it in the queue.
    // ================================================================

    /**
     * Convenience method for brand-new orders arriving from the network.
     * Generates the sequence number, orderId, and timestamp, then
     * enqueues the command. Returns immediately — the sequencer will
     * process it in FIFO order.
     */
    public void submitNewOrder(Side side, long price, int quantity) {
        long seq = sequenceCounter.incrementAndGet();
        long timestamp = System.currentTimeMillis();
        String orderId = "O-" + seq;

        Order order = new Order(orderId, side, price, quantity, timestamp, seq);
        enqueueNewOrder(order);
    }

    /**
     * Submit a pre-built Order (used by tests that need specific
     * orderId/sequenceNumber values, or by any caller that already
     * has a fully constructed Order).
     */
    public void submitOrder(Order order) {
        enqueueNewOrder(order);
    }

    /**
     * Cancel a resting order by ID. Generates its own sequence number
     * (cancels are events too — they need unique sequence numbers for
     * the WAL). Enqueues and returns immediately.
     */
    public void cancelOrder(String orderId) {
        long seq = sequenceCounter.incrementAndGet();
        long timestamp = System.currentTimeMillis();

        CountDownLatch done = new CountDownLatch(1);
        try {
            commandQueue.put(new EngineCommand.CancelCommand(orderId, seq, timestamp, done));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Blocks until every command currently in the queue has been
     * fully processed by the sequencer thread. Used by tests to
     * replace Thread.sleep() with a deterministic wait.
     *
     * How it works: we enqueue a special "no-op" NewOrderCommand
     * with a null order — wait, that would crash. Instead, we use
     * a simpler trick: enqueue a dummy CancelCommand for an orderId
     * that doesn't exist ("__idle_check__"). The sequencer will
     * process everything before it, then hit this dummy, call
     * cancelOrder("__idle_check__") which harmlessly returns false,
     * and count down the latch. At that point, we KNOW everything
     * before it has been fully processed.
     */
    public void awaitIdle(long timeout, TimeUnit unit) throws InterruptedException {
        CountDownLatch idle = new CountDownLatch(1);
        commandQueue.put(new EngineCommand.CancelCommand("__idle_check__", 0, 0, idle));
        idle.await(timeout, unit);
    }
    /*
     * What this does: The thread that called this method (like our unit
     * test) freezes right here at line 176 and waits!
     * 
     * It sleeps until one of two things happens:
     * Success: The Sequencer thread finishes processing all the customer
     * orders ahead in line, opens our fake envelope at the very back,
     * and presses the buzzer (idle.countDown()). The thread wakes up instantly!
     * 
     * Timeout: The clock runs out of time ( timeout,unit), and the thread wakes
     * up and stops waiting.
     */

    public OrderBook getOrderBook() {
        return orderBook;
    }

    /**
     * Graceful shutdown: stop the sequencer, then close the WAL.
     */
    public void shutdown() {
        running = false;
        sequencerThread.interrupt(); // unblock queue.take() if it's waiting
        try {
            sequencerThread.join(2000); // wait up to 2s for it to finish
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        writeAheadLog.close();
    }

    // ================================================================
    // PRIVATE — the sequencer loop (runs on a single dedicated thread)
    // ================================================================

    private void enqueueNewOrder(Order order) {
        CountDownLatch done = new CountDownLatch(1);
        try {
            commandQueue.put(new EngineCommand.NewOrderCommand(order, done));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The heart of the engine. This tight loop runs on the single
     * "sequencer" thread. It pulls one command at a time from the
     * queue and executes it. Because it's the ONLY thread that ever
     * touches the OrderBook or WAL, there are zero race conditions.
     */
    private void sequencerLoop() {
        while (running) {
            try {
                // take(), blocks until a command is available — no busy-spinning,
                // no polling, no CPU waste. The thread sleeps until woken.
                EngineCommand command = commandQueue.take();

                // Execute the command: log first, then mutate the book.
                executeCommand(command);

                // Signal anyone waiting on this specific command's latch.
                command.getDoneLatch().countDown();

            } catch (InterruptedException e) {
                // shutdown() interrupts this thread to break out of take().
                // If running is now false, the loop exits cleanly.
                if (!running) {
                    break;
                }
            }
        }

        // Drain any remaining commands before exiting, so nothing is lost.
        EngineCommand remaining;
        while ((remaining = commandQueue.poll()) != null) {
            executeCommand(remaining);
            remaining.getDoneLatch().countDown();
        }
    }

    /**
     * Dispatches a single command. This is the ONLY place in the
     * entire codebase where OrderBook is mutated during live operation.
     * (LogReplayer also calls orderBook.submitOrder(), but only during
     * startup replay, before the sequencer thread is even started.)
     */
    private void executeCommand(EngineCommand command) {
        switch (command) {
            case EngineCommand.NewOrderCommand cmd -> {
                writeAheadLog.append(cmd.order());
                orderBook.submitOrder(cmd.order());
            }
            case EngineCommand.CancelCommand cmd -> {
                // Skip the dummy idle-check commands (no real cancel to log)
                if (!"__idle_check__".equals(cmd.orderId())) {
                    writeAheadLog.appendCancel(cmd.orderId(), cmd.timestamp(), cmd.sequenceNumber());
                    orderBook.cancelOrder(cmd.orderId());
                }
            }
        }
    }
}
