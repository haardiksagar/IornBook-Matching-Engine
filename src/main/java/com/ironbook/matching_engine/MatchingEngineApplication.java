package com.ironbook.matching_engine;

import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.Side;
import java.io.IOException;

public class MatchingEngineApplication {
    public static void main(String[] args) throws IOException {
        System.out.println("Starting Matching Engine...");
        
        // 1. Initialize the engine and specify where the log file should live
        MatchingEngine engine = new MatchingEngine("orders.log");
        
        System.out.println("Engine started successfully. Crash recovery log initialized.");

        // 2. Create some fake orders to test the system
        Order buyOrder = new Order("buy-1", Side.BUY, 10050, 10, System.currentTimeMillis(), 1);
        Order sellOrder = new Order("sell-1", Side.SELL, 10000, 5, System.currentTimeMillis(), 2);
        
        // 3. Submit them to the engine
        System.out.println("Submitting Buy Order: " + buyOrder.getOrderId());
        engine.handleNewOrder(buyOrder);
        
        System.out.println("Submitting Sell Order: " + sellOrder.getOrderId());
        engine.handleNewOrder(sellOrder);
        
        System.out.println("Orders processed! If you look in the root folder, you will see 'orders.log' has been updated.");
    }
}
