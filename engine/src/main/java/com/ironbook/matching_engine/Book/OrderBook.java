package com.ironbook.matching_engine.Book;

import java.util.Map;
import java.util.Queue;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeMap;
import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.OrderStatus;
import com.ironbook.matching_engine.Model.Side;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import com.ironbook.matching_engine.Model.Trade;
import com.ironbook.matching_engine.Model.Snapshot;
import com.ironbook.matching_engine.Model.Snapshot.PriceLevel;
import java.util.AbstractMap;
import java.util.List;

/**
 * The core order book: two sorted price-level maps (bids descending,
 * asks ascending) and a fast orderId lookup index.
 *
 * THREAD SAFETY NOTE (TICKET-13):
 * ================================
 * This class uses PLAIN (non-concurrent) collections: TreeMap,
 * LinkedList, HashMap. This is safe — and intentionally faster —
 * because the MatchingEngine's sequencer pattern guarantees that
 * only ONE thread (the sequencer) ever reads or writes this class.
 *
 * Before TICKET-13, we used ConcurrentSkipListMap, ConcurrentLinkedQueue,
 * and ConcurrentHashMap "just in case." Those are slower because they
 * add internal synchronization overhead on every read and write.
 * Now that we have an architectural guarantee of single-threaded
 * access, we can safely use the faster plain versions.
 *
 * TreeMap replaces ConcurrentSkipListMap — same O(log n) sorted
 * access, but without lock overhead.
 * LinkedList replaces ConcurrentLinkedQueue — same FIFO queue
 * behavior, but without CAS overhead.
 * HashMap replaces ConcurrentHashMap — same O(1) lookup, but
 * without segment locking overhead.
 */
public class OrderBook {

    // Bids: highest price first (buyer willing to pay the most gets priority)
    private final TreeMap<Long, Queue<Order>> bidBook = new TreeMap<>(
            java.util.Collections.reverseOrder());

    // Asks: lowest price first (seller willing to accept the least gets priority)
    private final TreeMap<Long, Queue<Order>> askBook = new TreeMap<>();

    // Fast O(1) lookup by orderId — used for cancellation
    private final HashMap<String, Order> orderIndex = new HashMap<>();

    /**
     * Adds a new resting order to the correct book, at the correct price level.
     * Does NOT attempt to match it - matching is a separate step (next ticket),
     * so this method assumes the caller already tried to match and this order
     * still has quantity remaining.
     */
    public void addOrder(Order order) {
        TreeMap<Long, Queue<Order>> book = bookFor(order.getSide());

        // computeIfAbsent: if no queue exists yet at this price level,
        // create one. If one already exists, reuse it.
        Queue<Order> level = book.computeIfAbsent(
                order.getPrice(),
                price -> new LinkedList<>()
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

        // 1. get the right TreeMap
        TreeMap<Long, Queue<Order>> book = bookFor(order.getSide());
        // 2. get the right queue using price
        Queue<Order> level = book.get(order.getPrice());

        if (level == null) {
            return false; // shouldn't normally happen, but don't blow up
        }

        // 3. remove the order from its price level
        level.remove(order);

        // Clean up empty price levels so the book doesn't accumulate
        // dead entries forever.
        if (level.isEmpty()) {
            book.remove(order.getPrice());
        }

        order.setStatus(OrderStatus.CANCELLED);
        return true;
    }

    /**
     * Returns the best (lowest) ask price level, or null if the ask
     * book is empty. O(log n) via the TreeMap's firstEntry().
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

    private TreeMap<Long, Queue<Order>> bookFor(Side side) {
        return side == Side.BUY ? bidBook : askBook;
    }


    // simple unique trade ID source - swap for UUID if you prefer
    private final AtomicLong tradeSequence = new AtomicLong(0);

    /**
     * Attempts to match an incoming order against the OPPOSITE book.
     * Keeps matching (possibly against multiple resting orders) until
     * either:
     *   1) the incoming order's remaining quantity hits zero, or
     *   2) the opposite book has nothing left, or
     *   3) the next best opposite price no longer crosses.
     *
     * Any quantity left over after that gets added to this order's OWN
     * book via addOrder(), to rest and wait for a future match.
     *
     * Returns the list of trades produced (could be empty, one, or many).
     */
    public List<Trade> submitOrder(Order incoming) {
        List<Trade> trades = new ArrayList<>();
        TreeMap<Long, Queue<Order>> oppositeBook =
                incoming.getSide() == Side.BUY ? askBook : bidBook;
 
        while (incoming.getRemainingQuantity() > 0) {
            Map.Entry<Long, Queue<Order>> bestEntry = oppositeBook.firstEntry();
            if (bestEntry == null) {
                break; // stopping condition #2 - nothing left to match against
            }
 
            long bestPrice = bestEntry.getKey();
            boolean crosses = incoming.getSide() == Side.BUY
                    ? incoming.getPrice() >= bestPrice   // buyer willing to pay at least the ask
                    : incoming.getPrice() <= bestPrice;  // seller willing to accept at most the bid
 
            if (!crosses) {
                break; // stopping condition #3 - best opposite price is no longer acceptable
            }
 
            Queue<Order> level = bestEntry.getValue();
            Order resting = level.peek(); // earliest arrival at this price - time priority
 
            if (resting == null) {
                // level exists but is empty (rare edge case) - clean up and retry
                oppositeBook.remove(bestPrice);
                continue;
            }
 
            int matchedQty = Math.min(incoming.getRemainingQuantity(), resting.getRemainingQuantity());
 
            // Trade executes at the RESTING order's price, not the incoming
            // order's price - the resting order already committed to a price
            // and waited; the incoming order only had to cross it, not set it.
            String buyOrderId = incoming.getSide() == Side.BUY ? incoming.getOrderId() : resting.getOrderId();
            String sellOrderId = incoming.getSide() == Side.SELL ? incoming.getOrderId() : resting.getOrderId();
 
            Trade trade = new Trade(
                    "T-" + tradeSequence.incrementAndGet(),
                    buyOrderId,
                    sellOrderId,
                    bestPrice,
                    matchedQty,
                    System.currentTimeMillis()
            );
            trades.add(trade);
 
            incoming.reduceRemainingQuantity(matchedQty);
            resting.reduceRemainingQuantity(matchedQty);
 
            if (resting.getRemainingQuantity() == 0) {
                level.poll(); // remove from the front of the queue - it's done
                orderIndex.remove(resting.getOrderId());
                resting.setStatus(OrderStatus.FILLED);
 
                if (level.isEmpty()) {
                    oppositeBook.remove(bestPrice);
                }
            } else {
                resting.setStatus(OrderStatus.PARTIALLY_FILLED);
            }
        }
 
        if (incoming.getRemainingQuantity() > 0) {
            incoming.setStatus(OrderStatus.NEW);
            addOrder(incoming);
        } else {
            incoming.setStatus(OrderStatus.FILLED);
        }
 
        return trades;
    }

    public Snapshot getSnapshot(int levels) {
        List<PriceLevel> bids = new ArrayList<>();
        List<PriceLevel> asks = new ArrayList<>();

        int count = 0;
        for (Map.Entry<Long, Queue<Order>> entry : bidBook.entrySet()) {
            if (count++ >= levels) break;
            int totalVolume = entry.getValue().stream().mapToInt(Order::getRemainingQuantity).sum();
            bids.add(new PriceLevel(entry.getKey(), totalVolume));
        }

        count = 0;
        for (Map.Entry<Long, Queue<Order>> entry : askBook.entrySet()) {
            if (count++ >= levels) break;
            int totalVolume = entry.getValue().stream().mapToInt(Order::getRemainingQuantity).sum();
            asks.add(new PriceLevel(entry.getKey(), totalVolume));
        }

        return new Snapshot(bids, asks);
    }
}
