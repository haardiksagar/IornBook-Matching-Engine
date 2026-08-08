package com.ironbook.matching_engine.Model;

import java.util.List;

/**
 * A read-only snapshot of the OrderBook at a specific point in time.
 * Used to expose state to the web layer without thread-safety issues.
 */
public record Snapshot(
        List<PriceLevel> topBids,
        List<PriceLevel> topAsks
) {
    public record PriceLevel(long price, int volume) {}
}
