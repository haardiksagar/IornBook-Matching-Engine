package com.ironbook.matching_engine.Log;


import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Appends every incoming order to a plain text file BEFORE it gets
 * processed by the OrderBook. This is what makes crash recovery
 * possible: on restart, this file can be replayed from the top to
 * rebuild the exact same state, order by order.
 *
 * Format: one order per line, comma-separated:
 * orderId,side,price,quantity,timestamp,sequenceNumber
 *
 * Every write is flushed immediately to disk - we deliberately trade
 * some speed for durability, since the whole point of this class is
 * "if it's not on disk, it doesn't count as having happened."
 */
public class WriteAheadLog {

    private final PrintWriter writer;

    public WriteAheadLog(String filePath) throws IOException {
        // 'true' here means APPEND mode - if this file already has
        // entries from a previous run, we add to it, we never overwrite it.
        FileWriter fileWriter = new FileWriter(filePath, true);
        this.writer = new PrintWriter(fileWriter);
    }

    /**
     * Writes one order to the log. Must be called BEFORE submitOrder()
     * is called on that same order - never after.
     *
     * synchronized: only one thread can be inside this method at a time,
     * so two concurrent orders can never have their lines interleaved
     * or partially overwrite each other.
     */
    public synchronized void append(Order order) {
        String line = String.join(",",
                order.getOrderId(),
                order.getSide().name(),
                String.valueOf(order.getPrice()),
                String.valueOf(order.getRemainingQuantity()), // original qty at time of arrival
                String.valueOf(order.getTimestamp()),
                String.valueOf(order.getSequenceNumber()));

        writer.println(line);

        // Force this line out of any in-memory buffer and onto disk
        // RIGHT NOW - not "eventually". This is the actual durability
        // guarantee. Without this, a crash could lose recently-written
        // lines that were still sitting in a buffer.
        writer.flush();
    }

    public synchronized void close() {
        writer.close();
    }
}
