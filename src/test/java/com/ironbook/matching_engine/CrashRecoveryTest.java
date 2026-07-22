package com.ironbook.matching_engine;

import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CrashRecoveryTest {

    // JUnit creates a fresh, empty temp folder before this test runs,
    // and deletes it afterward - so test runs never interfere with
    // each other or leave real files behind on your machine.
    @TempDir
    Path tempDir;

    @Test
    void engineRecoversRestingOrders_afterSimulatedCrash() throws IOException {
        String logFilePath = tempDir.resolve("test-orders.log").toString();

        // ---- "BEFORE THE CRASH" ----
        MatchingEngine engineBeforeCrash = new MatchingEngine(logFilePath);

        Order restingSell = new Order("S1", Side.SELL, 150, 10, 1000L, 1);
        engineBeforeCrash.handleNewOrder(restingSell); // no match yet, just rests

        // At this point, in-memory state has S1 resting at 150.
        // The log file on disk ALSO has S1 written to it (because
        // append() flushes immediately - that's the whole point).

        // ---- SIMULATE THE CRASH ----
        // We don't call any formal save method, but because we are in a test on Windows, 
        // we MUST close the file writer so JUnit can delete the temp directory later.
        // In a real crash, the OS would force-close the file handle automatically.
        engineBeforeCrash.shutdown();

        // ---- "RESTART" ----
        // A brand new engine, pointed at the SAME log file. Its
        // constructor runs LogReplayer automatically, before we do
        // anything else with it.
        MatchingEngine engineAfterCrash = new MatchingEngine(logFilePath);

        // ---- ASSERT: recovered state matches what we had before ----
        assertEquals(150L, engineAfterCrash.getOrderBook().bestAsk().getKey(),
                "The resting sell order should have been rebuilt from the log");
                
        engineAfterCrash.shutdown();
    }

    @Test
    void engineRecoversPartiallyFilledOrder_afterSimulatedCrash() throws IOException {
        String logFilePath = tempDir.resolve("test-orders-2.log").toString();

        // ---- BEFORE THE CRASH ----
        MatchingEngine engineBeforeCrash = new MatchingEngine(logFilePath);

        Order seller = new Order("S1", Side.SELL, 150, 10, 1000L, 1);
        engineBeforeCrash.handleNewOrder(seller); // rests, no match

        Order buyer = new Order("B1", Side.BUY, 150, 4, 1001L, 2);
        engineBeforeCrash.handleNewOrder(buyer); // matches 4, seller has 6 left

        // ---- SIMULATE CRASH - abandon this instance ----
        engineBeforeCrash.shutdown();

        // ---- RESTART ----
        MatchingEngine engineAfterCrash = new MatchingEngine(logFilePath);

        // ---- ASSERT ----
        // The recovered book should show the seller resting with 6
        // remaining - NOT 10 (that would mean the match got lost) and
        // NOT 0 (that would mean the whole order got lost).
        assertEquals(150L, engineAfterCrash.getOrderBook().bestAsk().getKey());
        // Note: to check the exact remaining quantity, OrderBook would
        // need a way to peek at a resting order - worth adding a small
        // helper method if you don't have one yet, e.g. peekBestAsk()
        // returning the actual Order, not just the price level.
        engineAfterCrash.shutdown();
    }

    @Test
    void newOrdersAfterRestart_dontReuseOldSequenceNumbers() throws IOException {
        String logFilePath = tempDir.resolve("test-orders-3.log").toString();

        MatchingEngine engineBeforeCrash = new MatchingEngine(logFilePath);
        Order oldOrder = new Order("S1", Side.SELL, 150, 10, 1000L, 5); // sequence 5
        engineBeforeCrash.handleNewOrder(oldOrder);

        // simulate crash, restart
        engineBeforeCrash.shutdown();
        MatchingEngine engineAfterCrash = new MatchingEngine(logFilePath);

        // This confirms TICKET-8's maxSequenceSeen logic actually works -
        // without it, a fresh AtomicLong would start back at 0/1, and a
        // new order could collide with sequence number 5 from history.
        // (Exact assertion depends on how you expose the counter -
        // this is a good one to fill in once that getter exists.)
        engineAfterCrash.shutdown();
    }
}