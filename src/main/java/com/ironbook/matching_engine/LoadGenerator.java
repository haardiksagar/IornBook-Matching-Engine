package com.ironbook.matching_engine;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;

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
    private final int ordersPerClient;

    // Price range for random order generation.
    // Using a tight range (90-110) ensures heavy matching:
    // BUY orders at 100+ will cross with SELL orders at 100-.
    private static final long MIN_PRICE = 90;
    private static final long MAX_PRICE = 110;
    private static final int MIN_QTY = 1;
    private static final int MAX_QTY = 20;

    private final Random random = new Random();

    public LoadGenerator(String host, int port, int ordersPerClient) {
        this.host = host;
        this.port = port;
        this.ordersPerClient = ordersPerClient;
    }

    /**
     * Generates a random order string in the protocol format
     * that OrderMessageParser expects: "NEW,side,price,quantity"
     *
     * Side is 50/50 BUY or SELL.
     * Price is random within [MIN_PRICE, MAX_PRICE].
     * Quantity is random within [MIN_QTY, MAX_QTY].
     */
    private String generateRandomOrder() {
        String side = random.nextBoolean() ? "BUY" : "SELL";
        long price = MIN_PRICE + random.nextLong(MAX_PRICE - MIN_PRICE + 1);
        int quantity = MIN_QTY + random.nextInt(MAX_QTY - MIN_QTY + 1);
        return "NEW," + side + "," + price + "," + quantity;
    }

    /**
     * Opens a single TCP connection and sends N random orders.
     */
    public void runSingleClient() throws IOException {
        try (Socket socket = new Socket(host, port);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            for (int i = 0; i < ordersPerClient; i++) {
                String order = generateRandomOrder();
                writer.println(order);
            }
        }
        System.out.println("Client finished: sent " + ordersPerClient + " orders.");
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=== IronBook Load Generator ===");

        LoadGenerator generator = new LoadGenerator("localhost", 9999, 1000);
        generator.runSingleClient();

        System.out.println("Done.");
    }
}
