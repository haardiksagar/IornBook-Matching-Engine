package com.ironbook.matching_engine.web.controller;

import com.ironbook.matching_engine.MatchingEngine;
import com.ironbook.matching_engine.Model.Snapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/book")
public class OrderBookController {

    private final MatchingEngine matchingEngine;

    public OrderBookController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @GetMapping
    public Snapshot getOrderBookSnapshot(@RequestParam(defaultValue = "20") int levels) {
        return matchingEngine.getSnapshot(levels);
    }
}
