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

    /**
     * A small, plain data holder representing "whatever the client
     * asked for, once parsed." Only the fields relevant to the actual
     * type will be meaningfully set - e.g. a CANCEL message has
     * orderId set, but side/price/quantity are left at defaults,
     * since they were never present in that kind of message.
     */
    public static class ParsedMessage {
        public final MessageType type;
        public final Side side;
        public final long price;
        public final int quantity;
        public final String orderId;

        private ParsedMessage(MessageType type, Side side, long price, int quantity, String orderId) {
            this.type = type;
            this.side = side;
            this.price = price;
            this.quantity = quantity;
            this.orderId = orderId;
        }

        static ParsedMessage newOrder(Side side, long price, int quantity) {
            return new ParsedMessage(MessageType.NEW_ORDER, side, price, quantity, null);
        }

        static ParsedMessage cancel(String orderId) {
            return new ParsedMessage(MessageType.CANCEL, null, 0, 0, orderId);
        }
    }

    /**
     * Parses one raw line into a ParsedMessage.
     * Throws IllegalArgumentException on anything malformed - the
     * caller (TcpServer) decides what to do with a bad message (e.g.
     * log it and skip, rather than crash the whole connection).
     */
    public ParsedMessage parse(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            throw new IllegalArgumentException("Empty message");
        }

        String[] parts = rawLine.split(",");

        if (parts.length == 0) {
            throw new IllegalArgumentException("Malformed message: " + rawLine);
        }

        String messageKind = parts[0].trim().toUpperCase();

        switch (messageKind) {
            case "NEW":
                return parseNewOrder(parts, rawLine);
            case "CANCEL":
                return parseCancel(parts, rawLine);
            default:
                throw new IllegalArgumentException("Unknown message type: " + messageKind);
        }
    }
}