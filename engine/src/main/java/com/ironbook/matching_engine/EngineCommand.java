package com.ironbook.matching_engine;

import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.Side;

import java.util.concurrent.CountDownLatch;

/**
 * Represents a single unit of work to be processed by the sequencer
 * thread. TCP client threads NEVER touch the OrderBook directly -
 * they construct an EngineCommand and drop it into the shared queue.
 * The sequencer pulls commands one at a time, in strict FIFO order,
 * and executes them. This guarantees:
 *
 *   1) No two threads ever modify the OrderBook simultaneously.
 *   2) Log order = processing order = replay order (determinism).
 *   3) Zero lock contention on the hot path.
 *
 * Each command carries a CountDownLatch so that callers who NEED to
 * wait for completion (e.g. tests, or synchronous API endpoints)
 * can block on it. Callers who don't care (e.g. fire-and-forget TCP)
 * simply ignore the latch.
 */
public sealed interface EngineCommand {

    /**
     * The latch that the sequencer counts down AFTER it finishes
     * executing this command. Callers can await() on this if they
     * need to block until the command is fully processed.
     */
    CountDownLatch getDoneLatch();

    // ---- The two concrete command types ----

    /**
     * "Submit this pre-built Order to the engine."
     * Used by:
     *   - submitNewOrder() (builds the Order, then wraps it in this)
     *   - Tests that need a specific orderId/sequenceNumber
     */
    record NewOrderCommand(Order order, CountDownLatch doneLatch) implements EngineCommand {
        @Override
        public CountDownLatch getDoneLatch() {
            return doneLatch;
        }
    }

    /**
     * "Cancel the resting order with this ID."
     */
    record CancelCommand(String orderId, long sequenceNumber, long timestamp,
                         CountDownLatch doneLatch) implements EngineCommand {
        @Override
        public CountDownLatch getDoneLatch() {
            return doneLatch;
        }
    }

    /**
     * "Get a snapshot of the OrderBook."
     * Runs on the sequencer thread to safely read state.
     */
    record SnapshotCommand(int levels,
                           java.util.concurrent.atomic.AtomicReference<com.ironbook.matching_engine.Model.Snapshot> resultRef,
                           CountDownLatch doneLatch) implements EngineCommand {
        @Override
        public CountDownLatch getDoneLatch() {
            return doneLatch;
        }
    }
}
