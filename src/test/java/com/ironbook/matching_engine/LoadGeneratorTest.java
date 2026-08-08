package com.ironbook.matching_engine;

import com.ironbook.matching_engine.LoadGen.LoadGenerator;
import com.ironbook.matching_engine.Network.TCPServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the LoadGenerator.
 *
 * Boots a real MatchingEngine + TCPServer, runs the LoadGenerator
 * with a small number of orders, and verifies that orders actually
 * made it through the full pipeline:
 *
 *   LoadGenerator → TCP socket → TCPServer → OrderMessageParser
 *   → MatchingEngine sequencer → OrderBook
 *
 * This proves the LoadGenerator correctly speaks the protocol and
 * the whole system works end-to-end under multi-client load.
 */
class LoadGeneratorTest {

    @TempDir
    Path tempDir;

    private static final int TEST_PORT = 9998; // different from TCPServerTest's 9999

    private MatchingEngine engine;
    private TCPServer server;
    private Thread serverThread;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        String logFilePath = tempDir.resolve("load-gen-test.log").toString();
        engine = new MatchingEngine(logFilePath);
        server = new TCPServer(TEST_PORT, engine);

        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                // expected when stop() closes the socket
            }
        });
        serverThread.start();

        assertTrue(server.awaitReady(2, TimeUnit.SECONDS),
                "Server should have started within 2 seconds");
    }

    @AfterEach
    void tearDown() throws IOException, InterruptedException {
        server.stop();
        serverThread.join(1000);
        engine.shutdown();
    }

    @Test
    void loadGenerator_sendsOrdersThatReachTheEngine() throws InterruptedException {
        // 3 clients × 100 orders each = 300 total orders,
        // small enough to finish fast, large enough to prove concurrency.
        LoadGenerator generator = new LoadGenerator("localhost", TEST_PORT, 3, 100, 0);
        generator.run();

        // Wait for the sequencer to drain everything
        engine.awaitIdle(5, TimeUnit.SECONDS);

        // The order book should have received orders. With random BUY/SELL
        // at prices 90-110, some will match and some will rest. We just
        // need to prove the pipeline didn't silently drop everything.
        //
        // At least ONE side should have resting orders (it's statistically
        // impossible for 300 random orders to perfectly match to zero).
        boolean hasBids = engine.getOrderBook().bestBid() != null;
        boolean hasAsks = engine.getOrderBook().bestAsk() != null;

        assertTrue(hasBids || hasAsks,
                "After 300 random orders, the book should have at least some "
                + "resting orders. If it's completely empty, orders may not "
                + "have reached the engine.");
    }
}
