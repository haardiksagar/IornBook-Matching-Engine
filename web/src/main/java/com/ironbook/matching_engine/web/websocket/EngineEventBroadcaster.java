package com.ironbook.matching_engine.web.websocket;

import com.ironbook.matching_engine.EngineEventListener;
import com.ironbook.matching_engine.MatchingEngine;
import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.Trade;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class EngineEventBroadcaster implements EngineEventListener {

    private final MatchingEngine matchingEngine;
    private final SimpMessagingTemplate messagingTemplate;

    public EngineEventBroadcaster(MatchingEngine matchingEngine, SimpMessagingTemplate messagingTemplate) {
        this.matchingEngine = matchingEngine;
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void register() {
        // Register this broadcaster with the engine
        this.matchingEngine.addListener(this);
    }

    @Override
    public void onTrade(Trade trade) {
        // Broadcast the trade to all clients subscribed to /topic/trades
        messagingTemplate.convertAndSend("/topic/trades", trade);
    }

    @Override
    public void onOrderAdded(Order order) {
        // For a high-throughput engine, you might want to throttle these,
        // but for the demo we'll broadcast every order event.
        messagingTemplate.convertAndSend("/topic/orders", order);
    }

    @Override
    public void onOrderCanceled(String orderId) {
        messagingTemplate.convertAndSend("/topic/cancels", orderId);
    }
}
