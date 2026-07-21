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
}
