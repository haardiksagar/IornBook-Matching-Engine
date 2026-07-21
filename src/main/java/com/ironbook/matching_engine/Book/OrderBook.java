package com.ironbook.matching_engine.Book;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.OrderStatus;
import com.ironbook.matching_engine.Model.Side;

public class OrderBook {
    private final ConcurrentSkipListMap<Long, Queue<Order>> bidBook = new ConcurrentSkipListMap<>(
            java.util.Collections.reverseOrder());

    private final ConcurrentSkipListMap<Long, Queue<Order>> askBook = new ConcurrentSkipListMap<>();

    private final ConcurrentHashMap<String, Order> orderIndex = new ConcurrentHashMap<>();

    /**
     * Adds a new resting order to the correct book, at the correct price level.
     * Does NOT attempt to match it - matching is a separate step (next ticket),
     * so this method assumes the caller already tried to match and this order
     * still has quantity remaining.
     */
    public void addOrder(Order order) {
        ConcurrentSkipListMap<Long, Queue<Order>> book = bookFor(order.getSide());

        // computeIfAbsent is atomic - if two threads insert the first order
        // at a brand-new price level at the same time, only one queue gets
        // created and both orders land in it safely.
        Queue<Order> level = book.computeIfAbsent(
                order.getPrice(),
                price -> new ConcurrentLinkedQueue<>()// the queue is concurrent
        );

        level.add(order);
        orderIndex.put(order.getOrderId(), order);
    }

    /**
     * Cancels a resting order by ID.
     * Returns true if it was found and removed, false if it no longer
     * exists (already fully filled, or already cancelled).
     */
    public boolean cancelOrder(String orderId) {
        Order order = orderIndex.remove(orderId);
        if (order == null) {
            return false; // nothing to cancel - already gone
        }

        // 1. get the right SkipListMap
        ConcurrentSkipListMap<Long, Queue<Order>> book = bookFor(order.getSide());
        // 2. get the right queue using price
        Queue<Order> level = book.get(order.getPrice());

        if (level == null) {
            return false; // shouldn't normally happen, but don't blow up
        }

        // Note: ConcurrentLinkedQueue.remove(Object) is O(n) WITHIN this one
        // price level's queue - not O(n) across the whole book. Order objects
        // should rely on identity/orderId equality here, not full field equality.

        // 3.remove the value
        level.remove(order);

        // Clean up empty price levels so the book doesn't accumulate
        // dead entries forever.
        if (level.isEmpty()) {
            book.remove(order.getPrice(), level);
        }

        order.setStatus(OrderStatus.CANCELLED);
        return true;
    }

    /**
     * Returns the best (lowest) ask price level, or null if the ask
     * book is empty. O(log n) via the skip list's firstEntry().
     */
    public Map.Entry<Long, Queue<Order>> bestAsk() {
        return askBook.firstEntry();
    }

    /**
     * Returns the best (highest) bid price level, or null if the bid
     * book is empty.
     */
    public Map.Entry<Long, Queue<Order>> bestBid() {
        return bidBook.firstEntry();
    }

    private ConcurrentSkipListMap<Long, Queue<Order>> bookFor(Side side) {
        return side == Side.BUY ? bidBook : askBook;
    }
}
