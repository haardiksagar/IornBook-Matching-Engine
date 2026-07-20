package com.ironbook.matching_engine.Model;

public class Order {
    private final String orderId;        // unique ID — required for the ConcurrentHashMap lookup/cancellation
    private final Side side;             // BUY or SELL — decides which book it routes into
    private final long price;            // use long (e.g. cents/paise) not double — avoids floating-point rounding bugs in money math
    private final int originalQuantity;  // what the order asked for — needed for accurate trade logging later
    private int remainingQuantity;       // mutable — decreases as partial fills happen
    private final long timestamp;        // arrival time — drives price-time priority ordering
    private final long sequenceNumber;   // tie-breaker — see below
    private OrderStatus status;          // NEW, PARTIALLY_FILLED, FILLED, CANCELLED 
}
