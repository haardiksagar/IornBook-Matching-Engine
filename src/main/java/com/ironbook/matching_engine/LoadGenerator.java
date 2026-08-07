package com.ironbook.matching_engine;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A standalone "Fake Trader Bot" that connects to the running
 * MatchingEngine's TCP server and fires orders over the network.
 *
 * Usage:
 * 1. Start MatchingEngineApplication (boots engine + TCP server)
 * 2. Run LoadGenerator (connects as multiple clients and sends orders)
 *
 * This is NOT part of the engine itself — it's an external tool
 * that simulates real trading clients for benchmarking and demos.
 */
public class LoadGenerator {

    private final String host;
    private final int port;
    private final int numClients;
    private final int ordersPerClient;
    private final int ordersPerSecond; // 0 = unlimited (full throttle)

    // Price range for random order generation.
    // Using a tight range (90-110) ensures heavy matching:
    // BUY orders at 100+ will cross with SELL orders at 100-.
    private static final long MIN_PRICE = 90;
    private static final long MAX_PRICE = 110;
    private static final int MIN_QTY = 1;
    private static final int MAX_QTY = 20;

    public LoadGenerator(String host, int port, int numClients, int ordersPerClient, int ordersPerSecond) {
        this.host = host;
        this.port = port;
        this.numClients = numClients;
        this.ordersPerClient = ordersPerClient;
        this.ordersPerSecond = ordersPerSecond;
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
        System.out.println("Rate limit: "
                + (ordersPerSecond == 0 ? "UNLIMITED (full throttle)" : ordersPerSecond + " orders/sec per client"));
        System.out.println("Total orders: " + (numClients * ordersPerClient));
        System.out.println();

        // Calculate delay between orders for rate limiting.
        // If ordersPerSecond is 0, delayMs is 0 (no sleep = full speed).
        final long delayMs = (ordersPerSecond > 0) ? (1000L / ordersPerSecond) : 0;
        /*
         * There are 1,000 milliseconds in 1 second.
         * 
         * If you want to send 10 orders per second, you divide 1,000 by 10. 1000 / 10 =
         * 100.
         * 
         * This tells the thread to wait exactly 100 milliseconds between each order. By
         * sleeping for 100ms after every single order, it perfectly spaces out exactly
         * 10 orders across one full second!
         * 
         * (The L in 1000L just tells Java to treat the number as a long data type,
         * which is what Thread.sleep() requires).
         */

        // Shared counter across all threads for progress reporting.
        // AtomicLong is thread-safe so 5 threads can increment it
        // simultaneously without corrupting the count.
        final AtomicLong totalOrdersSent = new AtomicLong(0);
        final long totalExpected = (long) numClients * ordersPerClient;

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

                            // Live progress: print every 1,000 orders so
                            // the terminal feels alive during long runs.
                            long sent = totalOrdersSent.incrementAndGet();
                            if (sent % 1000 == 0) {
                                System.out.println("  Progress: " + sent + " / " + totalExpected + " orders sent...");
                            }

                            // Rate limiting: pause between orders if configured.
                            if (delayMs > 0) {
                                Thread.sleep(delayMs);
                            }
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
        long startTime = System.currentTimeMillis();
        startGun.countDown();

        // Wait for every client to finish
        for (Thread t : clientThreads) {
            t.join(60_000); // 60 second timeout per client
        }

        long endTime = System.currentTimeMillis();
        long elapsedMs = endTime - startTime;

        // ---- BENCHMARK SUMMARY ----
        System.out.println();
        System.out.println("========== BENCHMARK RESULTS ==========");
        System.out.println("  Total orders sent:  " + totalOrdersSent.get());
        System.out.println("  Total time:         " + elapsedMs + " ms");
        if (elapsedMs > 0) {
            long throughput = (totalOrdersSent.get() * 1000L) / elapsedMs;
            System.out.println("  Throughput:         " + throughput + " orders/sec");
            double avgLatencyUs = (elapsedMs * 1000.0) / totalOrdersSent.get();
            System.out.printf("  Avg latency:        %.1f µs/order%n", avgLatencyUs);
        }
        System.out.println("=======================================");
    }

    public static void main(String[] args) throws InterruptedException {
        // ---- Parse CLI arguments ----
        String host = "localhost";
        int port = 9999;
        int clients = 5;
        int orders = 1000;
        int rate = 0; // 0 = unlimited

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help" -> {
                    printUsage();
                    return;
                }
                case "--host" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--clients" -> clients = Integer.parseInt(args[++i]);
                case "--orders" -> orders = Integer.parseInt(args[++i]);
                case "--rate" -> rate = Integer.parseInt(args[++i]);
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    return;
                }
            }
        }

        System.out.println("=== IronBook Load Generator ===");
        System.out.println();

        LoadGenerator generator = new LoadGenerator(host, port, clients, orders, rate);
        generator.run();

        System.out.println();
        System.out.println("Done.");
    }

    private static void printUsage() {
        System.out.println("=== IronBook Load Generator ===");
        System.out.println();
        System.out.println("Usage: LoadGenerator [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --host <hostname>    Server host (default: localhost)");
        System.out.println("  --port <port>        Server port (default: 9999)");
        System.out.println("  --clients <n>        Number of simultaneous TCP clients (default: 5)");
        System.out.println("  --orders <n>         Orders per client (default: 1000)");
        System.out.println("  --rate <n>           Orders per second per client, 0 = unlimited (default: 0)");
        System.out.println("  --help               Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  LoadGenerator                              # 5 clients × 1000 orders, full speed");
        System.out.println("  LoadGenerator --clients 10 --orders 5000   # 10 clients × 5000 orders");
        System.out
                .println("  LoadGenerator --rate 100                   # 5 clients, throttled to 100 orders/sec each");
    }
}
