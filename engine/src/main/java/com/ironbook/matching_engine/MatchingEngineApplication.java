package com.ironbook.matching_engine;

import com.ironbook.matching_engine.Network.TCPServer;

import java.io.IOException;

/**
 * Entry point for the IronBook Matching Engine.
 *
 * Starts the MatchingEngine (sequencer + WAL + crash recovery),
 * then boots a TCPServer so external clients (including the
 * LoadGenerator) can connect over the network and submit orders.
 *
 * The server stays alive until you press Ctrl+C, at which point
 * the shutdown hook cleanly stops the server and flushes the WAL.
 */
public class MatchingEngineApplication {

    private static final int DEFAULT_PORT = 9999;
    private static final String DEFAULT_LOG_FILE = "orders.log";

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Starting IronBook Matching Engine...");

        // 1. Initialize the engine (replays WAL, starts sequencer thread)
        MatchingEngine engine = new MatchingEngine(DEFAULT_LOG_FILE);
        System.out.println("Engine started. WAL initialized from: " + DEFAULT_LOG_FILE);

        // 2. Start the TCP server on a background thread so main()
        //    doesn't block forever inside the accept() loop.
        TCPServer server = new TCPServer(DEFAULT_PORT, engine);
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                if (e.getMessage() == null || !e.getMessage().contains("closed")) {
                    System.err.println("TCP server error: " + e.getMessage());
                }
            }
        }, "tcp-server");
        serverThread.setDaemon(true);
        serverThread.start();

        // 3. Register a shutdown hook so Ctrl+C cleanly stops everything.
        //    Without this, the WAL file might not flush its last writes,
        //    and the server socket would leak.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            try {
                server.stop();
            } catch (IOException e) {
                // best-effort during shutdown
            }
            engine.shutdown();
            System.out.println("Engine stopped cleanly.");
        }, "shutdown-hook"));

        System.out.println("TCP server listening on port " + DEFAULT_PORT);
        System.out.println("Ready for connections. Press Ctrl+C to stop.");

        // 4. Keep the main thread alive. Without this, main() would
        //    return immediately and the JVM might exit (the server
        //    thread is a daemon, so it won't keep the JVM alive alone).
        serverThread.join();
    }
}
