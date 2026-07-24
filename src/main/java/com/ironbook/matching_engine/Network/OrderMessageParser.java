package com.ironbook.matching_engine.Network;

import com.ironbook.matching_engine.Model.Side;

/**
 * Parses raw text lines from a client into a structured message.
 *
 * Format:
 *   NEW,side,price,quantity     e.g. "NEW,BUY,150,10"
 *   CANCEL,orderId               e.g. "CANCEL,O-247"
 *
 * Deliberately does NOT touch OrderBook, MatchingEngine, or sockets -
 * its only job is text-in, structured-object-out. This keeps it easy
 * to unit test on its own.
 */
public class OrderMessageParser {

    // An enum here works like a fixed, named set of options - similar
    // to how Side is only ever BUY or SELL, a message can only ever
    // be one of these two kinds, nothing else is valid.
    public enum MessageType {
        NEW_ORDER,
        CANCEL
    }

}