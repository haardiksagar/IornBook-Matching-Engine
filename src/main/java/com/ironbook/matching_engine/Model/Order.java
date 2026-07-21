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
    
    public Order(String orderId, Side side, long price, int originalQuantity, long timestamp, long sequenceNumber) {
        this.orderId = orderId;
        this.side = side;
        this.price = price;
        this.originalQuantity = originalQuantity;
        this.remainingQuantity = originalQuantity;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
        this.status = OrderStatus.NEW;
    }

    public String getOrderId() {
        return orderId;
    }
    public Side getSide() {
        return side;
    }
    public long getPrice() {
        return price;
    }
    public int getOriginalQuantity() {
        return originalQuantity;
    }
    public int getRemainingQuantity() {
        return remainingQuantity;
    }
    
    public void setRemainingQuantity(int remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public long getSequenceNumber() {
        return sequenceNumber;
    }
    public OrderStatus getStatus() {
        return status;
    }
    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
