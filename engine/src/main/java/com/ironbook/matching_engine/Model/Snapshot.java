package com.ironbook.matching_engine.Model;

import java.util.List;
import java.util.Map;

/**
 * A read-only snapshot of the OrderBook at a specific point in time.
 * Used to expose state to the web layer without thread-safety issues.
 */
public record Snapshot(
        List<Map.Entry<Long, Integer>> topBids,
        List<Map.Entry<Long, Integer>> topAsks
) {}
