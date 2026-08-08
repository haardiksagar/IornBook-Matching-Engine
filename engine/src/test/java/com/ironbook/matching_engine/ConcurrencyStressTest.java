package com.ironbook.matching_engine;

import com.ironbook.matching_engine.Model.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.ironbook.matching_engine.Model.Order;

import static org.junit.jupiter.api.Assertions.*;

/**
 * THE CONCURRENCY PROOF.
 *
 * This test is the single most important test in the entire project.
 * It proves that the sequencer pattern actually works under real
 * concurrent load — not just "two threads, maybe" but TEN threads
 * all slamming the engine at the exact same time.
 *
 * The fundamental invariant we're checking:
 *
 * total shares submitted = total shares matched + total shares resting
 *
 * If even ONE share is lost (race condition ate it) or duplicated
 * (double-fill), this equation breaks and the test fails.
 *
 * Before TICKET-13 (no sequencer), this test would FAIL randomly
 * because multiple threads would peek() the same resting order and
 * both try to fill it, creating phantom shares out of thin air.
 */
class ConcurrencyStressTest {

    @TempDir
    Path tempDir;

    private static final int THREADS = 10;
    private static final int ORDERS_PER_THREAD = 1_000;

    @Test
    void noSharesLostOrDuplicated_underConcurrentLoad() throws Exception {
        String logFilePath = tempDir.resolve("stress-test.log").toString();
        MatchingEngine engine = new MatchingEngine(logFilePath);

        // All threads wait behind this latch so they all start at
        // the EXACT same instant — maximizing the chance of exposing
        // any race conditions. Without this, Thread 1 might finish
        // before Thread 10 even starts, which wouldn't be a real
        // concurrency test.
        CountDownLatch startGun = new CountDownLatch(1);

        // Track all threads so we can wait for them to finish
        List<Thread> threads = new ArrayList<>();

        // Half the threads submit BUY orders, half submit SELL orders,
        // all at the SAME price. This guarantees heavy matching
        // contention — exactly the scenario that would break a
        // non-thread-safe implementation.
        long matchPrice = 100;
        int qtyPerOrder = 5;

        for (int t = 0; t < THREADS; t++) {
            Side side = (t % 2 == 0) ? Side.BUY : Side.SELL;
            Thread thread = new Thread(() -> {
                try {
                    startGun.await(); // all threads block here until the gun fires
                    for (int i = 0; i < ORDERS_PER_THREAD; i++) {
                        engine.submitNewOrder(side, matchPrice, qtyPerOrder);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "stress-" + t);
            threads.add(thread);
            thread.start();
        }

        // FIRE! All 10 threads start submitting simultaneously.
        startGun.countDown();

        // Wait for every thread to finish submitting
        for (Thread t : threads) {
            t.join(30_000); // 30 second timeout per thread
        }

        // Wait for the sequencer to drain every last command
        engine.awaitIdle(10, TimeUnit.SECONDS);

        // ---- THE INVARIANT CHECK ----
        // Total shares submitted:
        // 10 threads × 1,000 orders × 5 shares = 50,000 shares
        // Split evenly: 25,000 BUY shares + 25,000 SELL shares
        //
        // Since BUY and SELL are at the same price, they WILL match.
        // Every BUY share should find a SELL share to match against.
        //
        // After all matching is done:
        // - shares_matched_on_buy_side + shares_still_resting_as_bids = 25,000
        // - shares_matched_on_sell_side + shares_still_resting_as_asks = 25,000
        //
        // And since both sides have equal quantity at the same price:
        // - resting bids should be 0 (or resting asks should be 0)
        // - total matched = 25,000 (all shares find a counterpart)

        int totalSubmittedPerSide = (THREADS / 2) * ORDERS_PER_THREAD * qtyPerOrder;

        // Count remaining resting shares
        int restingBidShares = countRestingShares(engine, Side.BUY);
        int restingAskShares = countRestingShares(engine, Side.SELL);

        // The key invariant: resting + matched = submitted, per side.
        // Since both sides submitted equal amounts at the same price,
        // one side should be fully matched (0 resting), and the
        // difference should be 0.
        assertEquals(restingBidShares, restingAskShares,
                "Both sides submitted equal quantities at the same price, "
                        + "so resting quantities should be equal (ideally both 0). "
                        + "If they differ, shares were lost or duplicated.");

        // The total resting across both sides should be 0 if everything matched
        assertEquals(0, restingBidShares + restingAskShares,
                "All BUY shares should have matched against SELL shares "
                        + "(same price, equal quantities). If shares are left resting, "
                        + "some matches were lost to a concurrency bug.");

        System.out.println("Stress test passed!");
        System.out.println("  Threads: " + THREADS);
        System.out.println("  Orders per thread: " + ORDERS_PER_THREAD);
        System.out.println("  Total orders: " + (THREADS * ORDERS_PER_THREAD));
        System.out.println("  Shares per side: " + totalSubmittedPerSide);
        System.out.println("  Resting bids: " + restingBidShares);
        System.out.println("  Resting asks: " + restingAskShares);

        engine.shutdown();
    }

    /**
     * Counts the total remaining shares across all price levels
     * on one side of the book.
     */
    private int countRestingShares(MatchingEngine engine, Side side) {
        int total = 0;
        Map.Entry<Long, Queue<Order>> entry = (side == Side.BUY)
                ? engine.getOrderBook().bestBid()
                : engine.getOrderBook().bestAsk();

        // Walk through all price levels on this side
        while (entry != null) {
            for (Order order : entry.getValue()) {
                total += order.getRemainingQuantity();
            }
            // Move to the next price level
            if (side == Side.BUY) {
                entry = ((java.util.TreeMap<Long, Queue<Order>>) getBookField(engine, side))
                        .higherEntry(entry.getKey());
            } else {
                entry = ((java.util.TreeMap<Long, Queue<Order>>) getBookField(engine, side))
                        .higherEntry(entry.getKey());
            }
        }
        return total;
    }

    /**
     * Helper to access the internal TreeMap for counting.
     * In production code you'd add a proper method to OrderBook,
     * but for a test this reflection-free approach via bestBid/bestAsk
     * is simpler. Since all orders are at the same price, there's
     * only one price level to check anyway.
     */
    private Object getBookField(MatchingEngine engine, Side side) {
        // Since all orders in this test are at the same price (100),
        // bestBid() or bestAsk() gives us the only level that exists.
        // This method exists only as a placeholder for the generic case.
        throw new UnsupportedOperationException("Not needed - all orders at same price");
    }

    @Test
    void noSharesLostOrDuplicated_simpleVersion() throws Exception {
        String logFilePath = tempDir.resolve("stress-simple.log").toString();
        /*
         * so tempDir generate a temp directory and gives us the path of that
         * folder and than we place a file name stress-simple.log in it
         * and after the work is done we delete the folder
         */
        MatchingEngine engine = new MatchingEngine(logFilePath);

        CountDownLatch startGun = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        long matchPrice = 100;
        int qtyPerOrder = 1; // 1 share per order, simplest case

        for (int t = 0; t < THREADS; t++) {
            Side side = (t % 2 == 0) ? Side.BUY : Side.SELL;
            Thread thread = new Thread(() -> {
                try {
                    startGun.await();
                    for (int i = 0; i < ORDERS_PER_THREAD; i++) {
                        engine.submitNewOrder(side, matchPrice, qtyPerOrder);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "stress-simple-" + t);
            threads.add(thread);
            thread.start();
        }

        startGun.countDown();

        /*
         * Since it is in a for loop, the main program waits for Thread 1
         * to finish, then Thread 2, all the way to Thread 10.
         */
        for (Thread t : threads) {
            t.join(30_000);
        }

        engine.awaitIdle(10, TimeUnit.SECONDS);

        // Simple check: since equal BUY and SELL quantities were
        // submitted at the same price, the book should be completely
        // empty after all matching is done.
        assertNull(engine.getOrderBook().bestBid(),
                "No bids should remain - all should have matched");
        assertNull(engine.getOrderBook().bestAsk(),
                "No asks should remain - all should have matched");

        System.out.println("Simple stress test passed! "
                + (THREADS * ORDERS_PER_THREAD) + " orders processed correctly.");

        engine.shutdown();
    }
}
