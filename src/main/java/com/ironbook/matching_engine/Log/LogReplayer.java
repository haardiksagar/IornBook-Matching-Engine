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
        /*Line 42: BufferedReader is the fastest way to read large text files in Java. 
        The try (...) syntax is special: it guarantees that Java will automatically close the 
        file when it's done, even if a crash happens.*/
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isBlank()) {
                    continue; // skip stray empty lines silently
                }

                try {
                    long sequenceNumber = replayLine(line, orderBook);
                    maxSequenceSeen = Math.max(maxSequenceSeen, sequenceNumber);
                } catch (Exception e) {
                    // One corrupted line (e.g. from a crash mid-write) should
                    // NOT prevent the whole engine from starting up.
                    System.err.println("Skipping malformed log line " + lineNumber
                            + ": \"" + line + "\" (" + e.getMessage() + ")");
                }
            }

            /*
            Lines 47-49: Safety check. If there's an empty line, skip it.

            Line 51 (try): We put the processing inside an inner try block. This is excellent 
            defensive programming. If one single line is corrupted, it won't crash the entire 
            replay process.

            Line 52: Calls the helper method below to turn the comma-separated string into a real 
            Order object.

            Line 53: Pushes the order into the OrderBook. Because matching is deterministic, 
            pushing the exact same historical orders into the book will perfectly recreate the 
            exact same matches and final state.

            Line 54: Updates the maxSequenceSeen to be whichever is higher: the current max, or 
            the sequence number of this specific order.
            */
        }

        return maxSequenceSeen;
    }

    /**
     * Parses one line, applies it to the OrderBook, and returns the
     * sequenceNumber it carried - used by replay() to track the
     * highest one seen across the whole file.
     */
    private long replayLine(String line, OrderBook orderBook) {
        String[] parts = line.split(",");
        String lineType = parts[0];
 
        switch (lineType) {
            case "NEW":
                return replayNewOrder(parts, orderBook);
            case "CANCEL":
                return replayCancel(parts, orderBook);
            default:
                throw new IllegalArgumentException("Unknown log line type: " + lineType);
        }
    }

    private long replayNewOrder(String[] parts, OrderBook orderBook) {
        // NEW,orderId,side,price,quantity,timestamp,sequenceNumber
        String orderId = parts[1];
        Side side = Side.valueOf(parts[2]);
        long price = Long.parseLong(parts[3]);
        int quantity = Integer.parseInt(parts[4]);
        long timestamp = Long.parseLong(parts[5]);
        long sequenceNumber = Long.parseLong(parts[6]);
 
        Order order = new Order(orderId, side, price, quantity, timestamp, sequenceNumber);
        orderBook.submitOrder(order);
 
        return sequenceNumber;
    }
}