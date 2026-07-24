package com.ironbook.matching_engine.Network;

import com.ironbook.matching_engine.MatchingEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Listens for incoming TCP client connections and hands each one off
 * to a worker thread. This is TICKET-10 only: accepting connections
 * and reading raw lines. Turning those lines into real Order objects
 * and calling the engine is TICKET-11's job, wired in loosely here
 * for now as a placeholder.
 */
public class TCPServer {

    private final int port;
    private final MatchingEngine engine;
    private final ExecutorService clientThreadPool;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    // Starts at 1; drops to 0 the instant the socket is actually bound.
    // Anyone calling awaitReady() blocks until that exact moment — no
    // guessing, no sleeping, no hoping.
    private final CountDownLatch readyLatch = new CountDownLatch(1);

    public TCPServer(int port, MatchingEngine engine) {
        this.port = port;
        this.engine = engine;
        // same idea as PeerLink's FixedThreadPool - a bounded set of
        // worker threads, reused across clients, instead of spawning
        // an unbounded number of raw threads.
        this.clientThreadPool = Executors.newFixedThreadPool(10);
    }

    /**
     * Starts the server. This method itself runs an infinite accept
     * loop, so call it from its own thread (e.g. in main()) unless
     * you want it to block whatever thread calls it.
     */
    public void start() throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.running = true;
        readyLatch.countDown(); // signal: "I am NOW accepting connections"
        System.out.println("TcpServer listening on port " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept(); // blocks here until someone connects
                clientThreadPool.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running) {
                    // only a real problem if we weren't the ones who
                    // closed the socket on purpose during stop()
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Runs on a worker thread, one per connected client. Reads lines
     * from that one client until they disconnect.
     */
    private final OrderMessageParser parser = new OrderMessageParser();

    private void handleClient(Socket clientSocket) {
        try (
                clientSocket;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    OrderMessageParser.ParsedMessage message = parser.parse(line);

                    if (message.type == OrderMessageParser.MessageType.NEW_ORDER) {
                        // THIS is the line - this is the moment a parsed
                        // message actually reaches MatchingEngine.

                        //IMPORTANT: this is what calls the matching Engine
                        engine.submitNewOrder(message.side, message.price, message.quantity);
                    } else if (message.type == OrderMessageParser.MessageType.CANCEL) {
                        engine.cancelOrder(message.orderId);
                    }

                } catch (IllegalArgumentException e) {
                    // one bad line from this client shouldn't kill their
                    // whole connection - log it and keep reading
                    System.err.println("Bad message from client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        }
    }

    /**
     * Blocks the calling thread until the server socket is actually
     * bound and accepting connections — or until the timeout expires.
     * Returns true if the server became ready, false if it timed out.
     *
     * This replaces the old Thread.sleep() hack: instead of guessing
     * how long to wait, the caller blocks on a CountDownLatch that
     * the server counts down the exact instant it's truly ready.
     */
    public boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
        return readyLatch.await(timeout, unit);
    }

    public void stop() throws IOException {
        running = false;
        if (serverSocket != null) {
            serverSocket.close(); // unblocks accept() so the loop can exit
        }
        clientThreadPool.shutdown();
    }
}