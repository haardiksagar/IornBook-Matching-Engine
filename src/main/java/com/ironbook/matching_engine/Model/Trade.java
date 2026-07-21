package com.ironbook.matching_engine.Model;

public class Trade {
    private final String tradeId;        // unique trade identifier
    private final String buyOrderId;     // which buy order was involved
    private final String sellOrderId;    // which sell order was involved
    private final long price;            // the price this specific match executed at
    private final int quantity;          // matched quantity
    private final long timestamp;        // when the trade happened

    public Trade(String tradeId, String buyOrderId, String sellOrderId,
                 long price, int quantity, long timestamp) {
        this.tradeId = tradeId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public String getTradeId() {
        return tradeId;
    }

    public String getBuyOrderId() {
        return buyOrderId;
    }

    public String getSellOrderId() {
        return sellOrderId;
    }

    public long getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Trade{" +
                "tradeId='" + tradeId + '\'' +
                ", buyOrderId='" + buyOrderId + '\'' +
                ", sellOrderId='" + sellOrderId + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", timestamp=" + timestamp +
                '}';
    }

/*
toString() overrides the default Java behavior of printing an object.

Without it, if you ever do System.out.println(trade), you get something useless like:

com.ironbook.matching_engine.Model.Trade@4a574795
With it, you get something human-readable:

Trade{tradeId='T-1', buyOrderId='ORD-42', sellOrderId='ORD-17', 
price=15000, quantity=10, timestamp=1721612345678}
It's purely for debugging and logging convenience. It has zero effect on 
your matching engine's logic or performance. If you don't plan on printing
trades to the console or logs, you can safely remove it.
*/
}
