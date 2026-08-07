package com.ironbook.matching_engine;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * A standalone "Fake Trader Bot" that connects to the running
 * MatchingEngine's TCP server and fires orders over the network.
 *
 * Usage:
 *   1. Start MatchingEngineApplication (boots engine + TCP server)
 *   2. Run LoadGenerator (connects as a client and sends orders)
 *
 * This is NOT part of the engine itself — it's an external tool
 * that simulates real trading clients for benchmarking and demos.
 */
public class LoadGenerator {

    private final String host;
    private final int port;

    public LoadGenerator(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Opens a single TCP connection to the server and sends
     * one hardcoded order. This is the simplest possible proof
     * that the LoadGenerator can talk to the engine over the wire.
     */
    public void sendSingleOrder() throws IOException {
        try (Socket socket = new Socket(host, port);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            String order = "NEW,BUY,100,10";
            writer.println(order);
            System.out.println("Sent: " + order);
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=== IronBook Load Generator ===");

        LoadGenerator generator = new LoadGenerator("localhost", 9999);
        generator.sendSingleOrder();

        System.out.println("Done.");
    }
}
