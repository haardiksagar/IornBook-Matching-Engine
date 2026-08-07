package com.ironbook.matching_engine;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * A standalone "Fake Trader Bot" that connects to the running
 * MatchingEngine's TCP server and fires orders over the network.
 *
 * Usage:
 *   1. Start MatchingEngineApplication (boots engine + TCP server)
 *   2. Run LoadGenerator (connects as multiple clients and sends orders)
 *
 * This is NOT part of the engine itself — it's an external tool
 * that simulates real trading clients for benchmarking and demos.
 */
public class LoadGenerator {

    private final String host;
    private final int port;
    private final int numClients;
    private final int ordersPerClient;

    // Price range for random order generation.
    // Using a tight range (90-110) ensures heavy matching:
    // BUY orders at 100+ will cross with SELL orders at 100-.
    private static final long MIN_PRICE = 90;
    private static final long MAX_PRICE = 110;
    private static final int MIN_QTY = 1;
    private static final int MAX_QTY = 20;

    public LoadGenerator(String host, int port, int numClients, int ordersPerClient) {
        this.host = host;
        this.port = port;
        this.numClients = numClients;
        this.ordersPerClient = ordersPerClient;
    }

    /**
     * Generates a random order string in the protocol format
     * that OrderMessageParser expects: "NEW,side,price,quantity"
     *
     * Each thread gets its own Random instance to avoid contention
     * on a shared Random (which would serialize our parallel clients).
     */
    private String generateRandomOrder(Random random) {
        String side = random.nextBoolean() ? "BUY" : "SELL";
        long price = MIN_PRICE + random.nextLong(MAX_PRICE - MIN_PRICE + 1);
        int quantity = MIN_QTY + random.nextInt(MAX_QTY - MIN_QTY + 1);
        return "NEW," + side + "," + price + "," + quantity;
    }

    /**
     * Spins up N client threads, each opening its own TCP socket.
     * All clients wait behind a CountDownLatch starting gun so they
     * begin sending at the exact same instant — maximizing concurrent
     * load on the server (same pattern as ConcurrencyStressTest).
     */
    public void run() throws InterruptedException {
        System.out.println("Connecting " + numClients + " clients to " + host + ":" + port + "...");
        System.out.println("Each client will send " + ordersPerClient + " random orders.");
        System.out.println("Total orders: " + (numClients * ordersPerClient));
        System.out.println();

        // Starting gun: all client threads wait on this latch,
        // then fire simultaneously when countDown() is called.
        CountDownLatch startGun = new CountDownLatch(1);
        List<Thread> clientThreads = new ArrayList<>();

        for (int c = 0; c < numClients; c++) {
            final int clientId = c;
            Thread clientThread = new Thread(() -> {
                // Each thread gets its own Random to avoid lock contention
                Random random = new Random();
                try {
                    startGun.await(); // wait for the starting gun

                    try (Socket socket = new Socket(host, port);
                         PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                        for (int i = 0; i < ordersPerClient; i++) {
                            writer.println(generateRandomOrder(random));
                        }
                    }
                    System.out.println("  Client " + clientId + " finished (" + ordersPerClient + " orders).");

                } catch (IOException e) {
                    System.err.println("  Client " + clientId + " error: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "load-client-" + clientId);

            clientThreads.add(clientThread);
            clientThread.start();
        }

        // FIRE! All clients begin sending simultaneously.
        System.out.println("All clients connected. Firing!");
        startGun.countDown();

        // Wait for every client to finish
        for (Thread t : clientThreads) {
            t.join(60_000); // 60 second timeout per client
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== IronBook Load Generator ===");
        System.out.println();

        LoadGenerator generator = new LoadGenerator("localhost", 9999, 5, 1000);
        generator.run();

        System.out.println();
        System.out.println("Done.");
    }
}
