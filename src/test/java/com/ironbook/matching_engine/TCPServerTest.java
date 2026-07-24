package com.ironbook.matching_engine;

import com.ironbook.matching_engine.MatchingEngine;
import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Network.TCPServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An INTEGRATION test - unlike OrderBookTest or CrashRecoveryTest,
 * this one goes through a REAL socket connection, exactly like an
 * actual client would. It proves the whole chain works together:
 * socket -> TcpServer -> OrderMessageParser -> MatchingEngine -> OrderBook.
 *
 * ZERO Thread.sleep() calls in this test. Instead of blindly sleeping
 * and hoping the system is ready, we use two proper synchronization
 * techniques:
 *
 * 1) CountDownLatch (for server startup) - the server signals the
 *    exact instant it's ready. The test blocks on the latch and
 *    unblocks the millisecond the socket is bound.
 *
 * 2) Polling with timeout (for message processing) - instead of
 *    sleeping a fixed duration and hoping the worker thread finished,
 *    we repeatedly check the actual condition we care about, with a
 *    hard deadline. If the state appears in 2ms, we continue in 2ms.
 *    If it never appears, we fail cleanly after the deadline.
 */
class TCPServerTest {

    @TempDir
    Path tempDir;

    private static final int TEST_PORT = 9999;

    private MatchingEngine engine;
    private TCPServer server;
    private Thread serverThread;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        String logFilePath = tempDir.resolve("integration-test.log").toString();
        engine = new MatchingEngine(logFilePath);
        server = new TCPServer(TEST_PORT, engine);

        // start() blocks forever in its accept loop, so it MUST run on
        // its own thread - otherwise this test would hang forever too.
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                // expected once stop() closes the socket during tearDown
            }
        });
        serverThread.start();

        // OLD WAY (bad):  Thread.sleep(100);
        // NEW WAY (good): block until the server signals it's truly ready.
        // awaitReady() uses a CountDownLatch internally - the server
        // counts it down the exact instant ServerSocket is bound.
        // If it takes 3ms, we wait 3ms. If something is broken and it
        // never binds, we fail after 2 seconds instead of hanging forever.
        assertTrue(server.awaitReady(2, TimeUnit.SECONDS),
                "Server should have started within 2 seconds");
    }

    @AfterEach
    void tearDown() throws IOException, InterruptedException {
        server.stop();
        serverThread.join(1000); // wait briefly for the thread to actually exit
        engine.shutdown();
    }

    @Test
    void newOrderMessage_actuallyReachesTheEngine() throws IOException, InterruptedException {
        // Connect as a real client would
        try (Socket clientSocket = new Socket("localhost", TEST_PORT);
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            writer.println("NEW,SELL,150,10");
        }

        // OLD WAY (bad):  Thread.sleep(200);
        // NEW WAY (good): poll the actual condition we care about, with
        // a hard deadline. The worker thread processes the message on its
        // own timeline - we don't guess how long that takes, we just
        // keep checking until the state we expect actually appears.
        awaitCondition(() -> engine.getOrderBook().bestAsk() != null,
                2000, "Order should have appeared in the book");

        assertEquals(150L, engine.getOrderBook().bestAsk().getKey());
    }

    @Test
    void cancelMessage_actuallyReachesTheEngine() throws IOException, InterruptedException {
        // First, get an order resting in the book directly through the
        // engine (not over the socket - simpler setup for this part)
        engine.submitNewOrder(com.ironbook.matching_engine.Model.Side.SELL, 150, 10);
        Order restingOrder = engine.getOrderBook().bestAsk().getValue().peek();
        String orderId = restingOrder.getOrderId();

        assertNotNull(engine.getOrderBook().bestAsk(), "Sanity check - order is resting");

        // NOW send the cancel over a REAL socket, exactly like a real client would
        try (Socket clientSocket = new Socket("localhost", TEST_PORT);
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            writer.println("CANCEL," + orderId);
        }

        // Poll until the order is actually gone - no sleep hacks
        awaitCondition(() -> engine.getOrderBook().bestAsk() == null,
                2000, "Order should be gone after CANCEL");
    }

    // ---- Helper: polling with timeout ----

    /**
     * Repeatedly checks a condition until it becomes true, or fails
     * after the deadline. Checks every 10ms - fast enough to catch
     * quick operations, slow enough to not burn the CPU.
     *
     * This is the proper replacement for Thread.sleep():
     * - If the condition is met in 2ms, we move on in ~10ms (next poll).
     * - If the system is slow, we keep trying up to the deadline.
     * - If something is genuinely broken, we fail with a clear message
     *   instead of silently passing after an arbitrary sleep.
     */
    private void awaitCondition(BooleanSupplier condition, long timeoutMs, String failureMessage)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail(failureMessage + " (timed out after " + timeoutMs + "ms)");
            }
            Thread.sleep(10); // tiny pause between polls - not a hack, just
                              // prevents busy-spinning from eating 100% CPU
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}