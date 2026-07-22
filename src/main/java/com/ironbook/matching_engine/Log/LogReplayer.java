package com.ironbook.matching_engine.Log;

import com.ironbook.matching_engine.Book.OrderBook;
import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.Side;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Reads a WriteAheadLog file from the top and replays every order
 * through the OrderBook, in the exact order they were originally
 * written.
 *
 * Important: this does NOT need to know which orders were already
 * matched before the crash. submitOrder() is deterministic - replaying
 * the same sequence of orders from an empty book reproduces the exact
 * same matches, automatically. There is no "already done" tracking
 * anywhere in this class, on purpose.
 */
public class LogReplayer {

    /**
     * Replays every order in the log file into the given OrderBook.
     * Returns the highest sequenceNumber seen, so the caller can
     * advance their live AtomicLong counter past it before accepting
     * new orders - otherwise a new order could reuse an old sequence
     * number.
     */
    public long replay(String filePath, OrderBook orderBook) throws IOException {
        File file = new File(filePath);

        if (!file.exists()) {
            return 0; // first-ever run - nothing to replay, counter starts at 0
        }

        long maxSequenceSeen = 0;
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isBlank()) {
                    continue; // skip stray empty lines silently
                }

                try {
                    Order order = parseLine(line);
                    orderBook.addOrder(order);
                    maxSequenceSeen = Math.max(maxSequenceSeen, order.getSequenceNumber());
                } catch (Exception e) {
                    // One corrupted line (e.g. from a crash mid-write) should
                    // NOT prevent the whole engine from starting up.
                    System.err.println("Skipping malformed log line " + lineNumber
                            + ": \"" + line + "\" (" + e.getMessage() + ")");
                }
            }
        }

        return maxSequenceSeen;
    }

    private Order parseLine(String line) {
        String[] parts = line.split(",");

        String orderId = parts[0];
        Side side = Side.valueOf(parts[1]);
        long price = Long.parseLong(parts[2]);
        int quantity = Integer.parseInt(parts[3]);
        long timestamp = Long.parseLong(parts[4]);
        long sequenceNumber = Long.parseLong(parts[5]);

        return new Order(orderId, side, price, quantity, timestamp, sequenceNumber);
    }
}