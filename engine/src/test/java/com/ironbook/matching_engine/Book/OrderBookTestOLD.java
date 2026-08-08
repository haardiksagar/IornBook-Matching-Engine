package com.ironbook.matching_engine.Book;

import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.OrderStatus;
import com.ironbook.matching_engine.Model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

public class OrderBookTestOLD {

    private OrderBook orderBook;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook();
    }

    @Test
    void testAddBuyOrder() {
        Order buyOrder = new Order("buy1", Side.BUY, 10000, 10, System.currentTimeMillis(), 1);
        
        orderBook.addOrder(buyOrder);
        
        Map.Entry<Long, Queue<Order>> bestBid = orderBook.bestBid();
        assertNotNull(bestBid, "Best bid should not be null after adding a buy order");
        assertEquals(10000L, bestBid.getKey(), "Best bid price should be 10000");
        assertEquals(1, bestBid.getValue().size(), "There should be 1 order in the queue");
        assertEquals("buy1", bestBid.getValue().peek().getOrderId(), "The order should be buy1");
    }

    @Test
    void testAddSellOrder() {
        Order sellOrder = new Order("sell1", Side.SELL, 10500, 10, System.currentTimeMillis(), 1);
        
        orderBook.addOrder(sellOrder);
        
        Map.Entry<Long, Queue<Order>> bestAsk = orderBook.bestAsk();
        assertNotNull(bestAsk, "Best ask should not be null after adding a sell order");
        assertEquals(10500L, bestAsk.getKey(), "Best ask price should be 10500");
        assertEquals(1, bestAsk.getValue().size(), "There should be 1 order in the queue");
        assertEquals("sell1", bestAsk.getValue().peek().getOrderId(), "The order should be sell1");
    }

    @Test
    void testCancelOrder() {
        Order order = new Order("order1", Side.BUY, 10000, 10, System.currentTimeMillis(), 1);
        orderBook.addOrder(order);
        
        boolean cancelled = orderBook.cancelOrder("order1");
        
        assertTrue(cancelled, "Cancel should return true for an existing order");
        assertEquals(OrderStatus.CANCELLED, order.getStatus(), "Order status should be updated to CANCELLED");
        
        Map.Entry<Long, Queue<Order>> bestBid = orderBook.bestBid();
        assertNull(bestBid, "Book should be empty after the only order is cancelled");
    }

    @Test
    void testBestBidOrdering() {
        // Adding a lower price bid (9900)
        orderBook.addOrder(new Order("buy1", Side.BUY, 9900, 10, System.currentTimeMillis(), 1));
        
        // Adding a higher price bid (10000)
        orderBook.addOrder(new Order("buy2", Side.BUY, 10000, 10, System.currentTimeMillis(), 2));
        
        Map.Entry<Long, Queue<Order>> bestBid = orderBook.bestBid();
        assertNotNull(bestBid);
        
        // The best bid should be the HIGHEST price (10000), not the lowest (9900)
        assertEquals(10000L, bestBid.getKey(), "Best bid should return the HIGHEST buy price");
    }

    @Test
    void testBestAskOrdering() {
        // Adding a lower price ask (10100)
        orderBook.addOrder(new Order("sell1", Side.SELL, 10100, 10, System.currentTimeMillis(), 1));
        
        // Adding a higher price ask (10200)
        orderBook.addOrder(new Order("sell2", Side.SELL, 10200, 10, System.currentTimeMillis(), 2));
        
        Map.Entry<Long, Queue<Order>> bestAsk = orderBook.bestAsk();
        assertNotNull(bestAsk);
        
        // The best ask should be the LOWEST price (10100)
        assertEquals(10100L, bestAsk.getKey(), "Best ask should return the LOWEST sell price");
    }
}



