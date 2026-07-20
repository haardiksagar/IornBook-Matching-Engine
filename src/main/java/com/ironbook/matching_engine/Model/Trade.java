package com.ironbook.matching_engine.Model;

public class Trade {
    private final String tradeId;        // you had this
    private final String buyOrderId;     // missing — which buy order was involved
    private final String sellOrderId;    // missing — which sell order was involved
    private final long price;            // missing — the price this specific match executed at
    private final int quantity;          // you had this
    private final long timestamp;        // you had this
}
