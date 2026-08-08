package com.ironbook.matching_engine;

import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.Trade;

/**
 * Interface for receiving events from the MatchingEngine.
 * Used by the web layer to broadcast real-time updates over WebSockets.
 */
public interface EngineEventListener {
    
    /**
     * Called when a trade occurs (two orders match).
     */
    default void onTrade(Trade trade) {}

    /**
     * Called when a new order is successfully added to the book.
     */
    default void onOrderAdded(Order order) {}

    /**
     * Called when an order is canceled.
     */
    default void onOrderCanceled(String orderId) {}
}
