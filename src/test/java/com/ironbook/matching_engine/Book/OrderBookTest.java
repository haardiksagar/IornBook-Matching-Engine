package com.ironbook.matching_engine.Book;

import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.OrderStatus;
import com.ironbook.matching_engine.Model.Side;
import com.ironbook.matching_engine.Model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private OrderBook book;

    // @BeforeEach runs before EVERY single @Test method below,
    // so each test starts with a completely fresh, empty order book.
    // This matters - if tests shared one book, one test's leftover
    // orders could silently affect another test's result.
    @BeforeEach
    void setUp() {
        book = new OrderBook();
    }

    @Test
    void restingOrderWithNoMatch_getsAddedToBook() {
        // Arrange: a sell order with nothing to match against
        Order sell = new Order("S1", Side.SELL, 150, 10, System.currentTimeMillis(), 1);

        // Act
        List<Trade> trades = book.submitOrder(sell);

        // Assert: no trade should have happened, and the order should
        // now be sitting at the best ask price
        assertTrue(trades.isEmpty(), "No opposite orders existed, so nothing should match");
        assertEquals(150L, book.bestAsk().getKey(), "The resting sell should now be the best ask");
        assertEquals(OrderStatus.NEW, sell.getStatus());
    }

    @Test
    void priceTimePriority_earlierOrderAtSamePriceMatchesFirst() {
        // Arrange: two sell orders at the SAME price, arriving in order
        Order firstSeller = new Order("S1", Side.SELL, 150, 10, 1000L, 1);  // arrives first
        Order secondSeller = new Order("S2", Side.SELL, 150, 10, 1001L, 2); // arrives second
        book.submitOrder(firstSeller);
        book.submitOrder(secondSeller);

        // Act: a buyer wants only 5 shares - not enough to need the second seller
        Order buyer = new Order("B1", Side.BUY, 150, 5, 1002L, 3);
        List<Trade> trades = book.submitOrder(buyer);

        // Assert: the trade must be against the FIRST seller, not the second,
        // even though both offered the identical price
        assertEquals(1, trades.size());
        assertEquals("S1", trades.get(0).getSellOrderId(),
                "Time priority violated - the earlier order at the same price should match first");
        assertEquals(5, firstSeller.getRemainingQuantity(),
                "First seller should be partially filled (10 - 5 = 5 left)");
        assertEquals(10, secondSeller.getRemainingQuantity(),
                "Second seller should be completely untouched");
    }

    @Test
    void partialFill_incomingOrderEatsThroughMultipleRestingOrders() {
        // Arrange: two resting sell orders, same price, different quantities
        Order firstSeller = new Order("S1", Side.SELL, 150, 10, 1000L, 1);
        Order secondSeller = new Order("S2", Side.SELL, 150, 5, 1001L, 2);
        book.submitOrder(firstSeller);
        book.submitOrder(secondSeller);

        // Act: buyer wants 12 - more than the first seller alone can offer
        Order buyer = new Order("B1", Side.BUY, 150, 12, 1002L, 3);
        List<Trade> trades = book.submitOrder(buyer);

        // Assert: this should produce TWO trades - 10 against the first
        // seller, then 2 more against the second seller
        assertEquals(2, trades.size());
        assertEquals(10, trades.get(0).getQuantity());
        assertEquals(2, trades.get(1).getQuantity());

        assertEquals(0, firstSeller.getRemainingQuantity());
        assertEquals(OrderStatus.FILLED, firstSeller.getStatus());

        assertEquals(3, secondSeller.getRemainingQuantity(),
                "Second seller had 5, gave up 2, should have 3 left");
        assertEquals(OrderStatus.PARTIALLY_FILLED, secondSeller.getStatus());

        assertEquals(0, buyer.getRemainingQuantity(), "Buyer's full 12 should be filled");
        assertEquals(OrderStatus.FILLED, buyer.getStatus());
    }

    @Test
    void tradeExecutesAtRestingOrderPrice_notIncomingOrderPrice() {
        // Arrange: seller resting at 149, willing to accept less than the buyer offers
        Order seller = new Order("S1", Side.SELL, 149, 10, 1000L, 1);
        book.submitOrder(seller);

        // Act: buyer is willing to pay UP TO 152, more than the ask
        Order buyer = new Order("B1", Side.BUY, 152, 10, 1001L, 2);
        List<Trade> trades = book.submitOrder(buyer);

        // Assert: trade price should be the RESTING order's price (149), not 152
        assertEquals(1, trades.size());
        assertEquals(149L, trades.get(0).getPrice(),
                "Trade should execute at the resting seller's price, not the aggressive buyer's price");
    }

    @Test
    void cancelOrder_removesRestingOrderSuccessfully() {
        // Arrange
        Order sell = new Order("S1", Side.SELL, 150, 10, System.currentTimeMillis(), 1);
        book.submitOrder(sell); // rests, since nothing to match against

        // Act
        boolean cancelled = book.cancelOrder("S1");

        // Assert: cancel should succeed, and the price level should be
        // completely gone since it was the only order there
        assertTrue(cancelled);
        assertNull(book.bestAsk(), "Book should be empty after cancelling the only resting order");
    }


}