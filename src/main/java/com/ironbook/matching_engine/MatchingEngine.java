package com.ironbook.matching_engine;

import com.ironbook.matching_engine.Book.OrderBook;
import com.ironbook.matching_engine.Log.LogReplayer;
import com.ironbook.matching_engine.Log.WriteAheadLog;
import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.Side;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ties everything together. This is the ONE place that knows the log
 * file's path - OrderBook, WriteAheadLog, and LogReplayer never talk
 * to each other directly. They all only talk to MatchingEngine.
 */
public class MatchingEngine {

    private final OrderBook orderBook;
    private final WriteAheadLog writeAheadLog;
    private final AtomicLong sequenceCounter;

    public MatchingEngine(String logFilePath) throws IOException {
        this.orderBook = new OrderBook();

        // STEP 1: replay history FIRST, before we start accepting new
        // orders or even opening the log for new writes. This rebuilds
        // whatever state existed before a crash (or does nothing, on
        // a first-ever run where the file doesn't exist yet).
        LogReplayer replayer = new LogReplayer();
        long maxSequenceSeen = replayer.replay(logFilePath, orderBook);

        // STEP 2: now that replay is done, seed the counter so new
        // orders don't reuse sequence numbers that already exist in history.
        this.sequenceCounter = new AtomicLong(maxSequenceSeen + 1);

        // STEP 3: only now do we open the log for NEW writes going forward.
        // Same filePath as what replay just read from - this is the one
        // and only place that path is defined.
        this.writeAheadLog = new WriteAheadLog(logFilePath);
    }

    /**
     * This is what a client connection / API layer would actually call
     * for every new incoming order. Notice the order: log first, THEN
     * process - exactly the rule we established earlier.
     */
    public void handleNewOrder(Order order) {
        writeAheadLog.append(order); // durable record, BEFORE processing
        orderBook.submitOrder(order); // now actually match it
    }
/**
     * For a genuinely brand-new order arriving live (e.g. from a client
     * over the network) that does NOT have a sequence number yet.
     * This is the ONLY place a new sequence number should ever be
     * generated - using the engine's single shared counter, already
     * correctly seeded past history by replay in the constructor.
     *
     * The Order is only constructed once every field - including the
     * real sequenceNumber - is already known. No throwaway object,
     * no "build it wrong then copy it right" step.
     */
    public void submitNewOrder(String orderId, Side side, long price, int quantity) {
        long sequenceNumber = sequenceCounter.incrementAndGet();
        long timestamp = System.currentTimeMillis();
 
        Order order = new Order(orderId, side, price, quantity, timestamp, sequenceNumber);
 
        handleNewOrder(order); // reuses the same log-then-process logic above
    }
 
    public OrderBook getOrderBook() {
        return orderBook;
    }

    // added by antigravity
    public void shutdown() {
        writeAheadLog.close();
    }
}