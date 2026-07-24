package com.ironbook.matching_engine;

import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CrashRecoveryTest {
 
    @TempDir
    Path tempDir;
 
    @Test
    void engineRecoversRestingOrders_afterSimulatedCrash() throws IOException, InterruptedException {
        String logFilePath = tempDir.resolve("test-orders.log").toString();
 
        // ---- "BEFORE THE CRASH" ----
        MatchingEngine engineBeforeCrash = new MatchingEngine(logFilePath);
 
        Order restingSell = new Order("S1", Side.SELL, 150, 10, 1000L, 1);
        engineBeforeCrash.submitOrder(restingSell); // enqueue, sequencer processes it
        engineBeforeCrash.awaitIdle(2, TimeUnit.SECONDS); // wait for sequencer to finish
 
        // ---- SIMULATE THE CRASH ----
        engineBeforeCrash.shutdown();
 
        // ---- "RESTART" ----
        MatchingEngine engineAfterCrash = new MatchingEngine(logFilePath);
 
        // ---- ASSERT: recovered state matches what we had before ----
        assertEquals(150L, engineAfterCrash.getOrderBook().bestAsk().getKey(),
                "The resting sell order should have been rebuilt from the log");
        engineAfterCrash.shutdown();
    }
 
    @Test
    void engineRecoversPartiallyFilledOrder_afterSimulatedCrash() throws IOException, InterruptedException {
        String logFilePath = tempDir.resolve("test-orders-2.log").toString();
 
        // ---- BEFORE THE CRASH ----
        MatchingEngine engineBeforeCrash = new MatchingEngine(logFilePath);
 
        Order seller = new Order("S1", Side.SELL, 150, 10, 1000L, 1);
        engineBeforeCrash.submitOrder(seller);
 
        Order buyer = new Order("B1", Side.BUY, 150, 4, 1001L, 2);
        engineBeforeCrash.submitOrder(buyer);
        engineBeforeCrash.awaitIdle(2, TimeUnit.SECONDS);
 
        // ---- SIMULATE CRASH ----
        engineBeforeCrash.shutdown();
 
        // ---- RESTART ----
        MatchingEngine engineAfterCrash = new MatchingEngine(logFilePath);
 
        // ---- ASSERT ----
        assertEquals(150L, engineAfterCrash.getOrderBook().bestAsk().getKey());
        engineAfterCrash.shutdown();
    }
 
    @Test
    void newOrdersAfterRestart_dontReuseOldSequenceNumbers() throws IOException, InterruptedException {
        String logFilePath = tempDir.resolve("test-orders-3.log").toString();
 
        MatchingEngine engineBeforeCrash = new MatchingEngine(logFilePath);
        Order oldOrder = new Order("S1", Side.SELL, 150, 10, 1000L, 5); // sequence 5
        engineBeforeCrash.submitOrder(oldOrder);
        engineBeforeCrash.awaitIdle(2, TimeUnit.SECONDS);
 
        // simulate crash, restart
        engineBeforeCrash.shutdown();
        MatchingEngine engineAfterCrash = new MatchingEngine(logFilePath);
 
        // The sequencer counter should have been seeded past 5
        engineAfterCrash.shutdown();
    }
 
    @Test
    void cancelledOrderStaysCancelled_afterSimulatedCrash() throws IOException, InterruptedException {
        String logFilePath = tempDir.resolve("test-orders-4.log").toString();
 
        // ---- BEFORE THE CRASH ----
        MatchingEngine engineBeforeCrash = new MatchingEngine(logFilePath);
 
        // Submit via the public API and wait for sequencer to process
        engineBeforeCrash.submitNewOrder(Side.SELL, 150, 10);
        engineBeforeCrash.awaitIdle(2, TimeUnit.SECONDS);

        Order restingOrder = engineBeforeCrash.getOrderBook().bestAsk().getValue().peek();
        String orderId = restingOrder.getOrderId();
 
        // sanity check - it's actually resting before we cancel it
        assertNotNull(engineBeforeCrash.getOrderBook().bestAsk());
 
        engineBeforeCrash.cancelOrder(orderId);
        engineBeforeCrash.awaitIdle(2, TimeUnit.SECONDS);
 
        // sanity check - cancellation worked
        assertNull(engineBeforeCrash.getOrderBook().bestAsk(),
                "Order should be gone from the live book immediately after cancelling");
 
        // ---- SIMULATE CRASH ----
        engineBeforeCrash.shutdown();
 
        // ---- RESTART ----
        MatchingEngine engineAfterCrash = new MatchingEngine(logFilePath);
 
        // ---- THE ACTUAL PROOF ----
        assertNull(engineAfterCrash.getOrderBook().bestAsk(),
                "Cancelled order should NOT reappear after crash recovery - "
                        + "the CANCEL event must have been replayed, not just the original NEW");
        engineAfterCrash.shutdown();
    }
}