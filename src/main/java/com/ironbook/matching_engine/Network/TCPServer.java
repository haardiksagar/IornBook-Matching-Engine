package com.ironbook.matching_engine.Network;

import com.ironbook.matching_engine.MatchingEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class TCPServer {
 
    private final int port;
    private final MatchingEngine engine;
    private final ExecutorService clientThreadPool;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
 
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
    private void handleClient(Socket clientSocket) {
        try (
                clientSocket;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                // TICKET-11 will replace this with real parsing +
                // engine.submitNewOrder(...). For now, just prove
                // connections and messages are actually arriving.
                System.out.println("Received from client: " + line);
            }
        } catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        }
    }
 
    public void stop() throws IOException {
        running = false;
        if (serverSocket != null) {
            serverSocket.close(); // unblocks accept() so the loop can exit
        }
        clientThreadPool.shutdown();
    }
}