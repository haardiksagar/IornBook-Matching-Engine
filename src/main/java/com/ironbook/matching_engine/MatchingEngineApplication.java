package com.ironbook.matching_engine;

import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.Side;
import java.io.IOException;

public class MatchingEngineApplication {
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Starting Matching Engine...");
        
        // 1. Initialize the engine and specify where the log file should live
        MatchingEngine engine = new MatchingEngine("orders.log");
        
        System.out.println("Engine started successfully. Crash recovery log initialized.");

        // 2. Submit some orders through the public API (goes through the sequencer)
        System.out.println("Submitting Buy Order...");
        engine.submitNewOrder(Side.BUY, 10050, 10);
        
        System.out.println("Submitting Sell Order...");
        engine.submitNewOrder(Side.SELL, 10000, 5);

        // Wait for the sequencer to process both
        engine.awaitIdle(2, java.util.concurrent.TimeUnit.SECONDS);
        
        System.out.println("Orders processed! If you look in the root folder, you will see 'orders.log' has been updated.");
        engine.shutdown();
    }
}
