package com.ironbook.matching_engine.web;

import com.ironbook.matching_engine.MatchingEngine;
import com.ironbook.matching_engine.Network.TCPServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

@SpringBootApplication
public class MatchingEngineWebApplication {

    private static final int DEFAULT_PORT = 9999;
    private static final String DEFAULT_LOG_FILE = "orders.log";

    public static void main(String[] args) {
        SpringApplication.run(MatchingEngineWebApplication.class, args);
    }

    @Bean(destroyMethod = "shutdown")
    public MatchingEngine matchingEngine() throws IOException {
        System.out.println("Starting IronBook Matching Engine...");
        MatchingEngine engine = new MatchingEngine(DEFAULT_LOG_FILE);
        System.out.println("Engine started. WAL initialized from: " + DEFAULT_LOG_FILE);
        return engine;
    }

    @Bean(destroyMethod = "stop")
    public TCPServer tcpServer(MatchingEngine engine) throws IOException {
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
        System.out.println("TCP server listening on port " + DEFAULT_PORT);
        return server;
    }
}
