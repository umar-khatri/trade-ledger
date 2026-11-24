package com.tradeledger.service;

import com.tradeledger.domain.model.Order;
import com.tradeledger.domain.model.OrderSide;
import com.tradeledger.domain.model.OrderStatus;
import com.tradeledger.domain.model.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookTest {

    private OrderBook orderBook;
    private UUID portfolioA;
    private UUID portfolioB;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook("AAPL");
        portfolioA = UUID.randomUUID();
        portfolioB = UUID.randomUUID();
    }

    @Test
    void addOrder_shouldRestInBook() {
        Order buy = new Order(portfolioA, "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("10"), new BigDecimal("150.00"), null);
        buy.setId(UUID.randomUUID());

        orderBook.addOrder(buy);

        assertThat(orderBook.getActiveOrderCount()).isEqualTo(1);
        assertThat(orderBook.getBestBid()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void match_priceTimePriority_shouldMatchBestPriceFirst() {
        // Resting asks: Ask1 at 155.00, Ask2 at 150.00
        Order ask1 = new Order(portfolioA, "AAPL", OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("5"), new BigDecimal("155.00"), null);
        ask1.setId(UUID.randomUUID());

        Order ask2 = new Order(portfolioA, "AAPL", OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("5"), new BigDecimal("150.00"), null);
        ask2.setId(UUID.randomUUID());

        orderBook.addOrder(ask1);
        orderBook.addOrder(ask2);

        // Incoming BUY limit at 160.00 for 5 shares -> must match Ask2 (150.00) first!
        Order incomingBuy = new Order(portfolioB, "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("5"), new BigDecimal("160.00"), null);
        incomingBuy.setId(UUID.randomUUID());

        List<OrderBook.Match> matches = orderBook.match(incomingBuy);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().restingOrder().getId()).isEqualTo(ask2.getId());
        assertThat(matches.getFirst().matchPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(matches.getFirst().matchQuantity()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(incomingBuy.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void match_fifoTimePriority_samePrice() {
        // Two asks at same price 150.00: askFirst added before askSecond
        Order askFirst = new Order(portfolioA, "AAPL", OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("5"), new BigDecimal("150.00"), null);
        askFirst.setId(UUID.randomUUID());

        Order askSecond = new Order(portfolioA, "AAPL", OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("5"), new BigDecimal("150.00"), null);
        askSecond.setId(UUID.randomUUID());

        orderBook.addOrder(askFirst);
        orderBook.addOrder(askSecond);

        // Incoming BUY for 5 shares -> must match askFirst
        Order incomingBuy = new Order(portfolioB, "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("5"), new BigDecimal("150.00"), null);
        incomingBuy.setId(UUID.randomUUID());

        List<OrderBook.Match> matches = orderBook.match(incomingBuy);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().restingOrder().getId()).isEqualTo(askFirst.getId());
    }

    @Test
    void match_partialFill_shouldLeaveRemainderInRestingOrder() {
        // Resting ask for 10 shares at 150.00
        Order restingAsk = new Order(portfolioA, "AAPL", OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("10"), new BigDecimal("150.00"), null);
        restingAsk.setId(UUID.randomUUID());
        orderBook.addOrder(restingAsk);

        // Incoming buy for 4 shares
        Order incomingBuy = new Order(portfolioB, "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("4"), new BigDecimal("150.00"), null);
        incomingBuy.setId(UUID.randomUUID());

        List<OrderBook.Match> matches = orderBook.match(incomingBuy);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().matchQuantity()).isEqualByComparingTo(new BigDecimal("4"));
        assertThat(restingAsk.getFilledQuantity()).isEqualByComparingTo(new BigDecimal("4"));
        assertThat(restingAsk.getRemainingQuantity()).isEqualByComparingTo(new BigDecimal("6"));
        assertThat(restingAsk.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        // Resting order should still be in the book
        assertThat(orderBook.getActiveOrderCount()).isEqualTo(1);
    }

    @Test
    void match_marketOrder_shouldSweepBestPrices() {
        // Resting asks: 5 shares @ 150.00, 5 shares @ 152.00
        Order ask1 = new Order(portfolioA, "AAPL", OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("5"), new BigDecimal("150.00"), null);
        ask1.setId(UUID.randomUUID());

        Order ask2 = new Order(portfolioA, "AAPL", OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("5"), new BigDecimal("152.00"), null);
        ask2.setId(UUID.randomUUID());

        orderBook.addOrder(ask1);
        orderBook.addOrder(ask2);

        // Incoming BUY MARKET for 8 shares
        Order incomingBuy = new Order(portfolioB, "AAPL", OrderSide.BUY, OrderType.MARKET,
                new BigDecimal("8"), null, null);
        incomingBuy.setId(UUID.randomUUID());

        List<OrderBook.Match> matches = orderBook.match(incomingBuy);

        assertThat(matches).hasSize(2);
        // Match 1: 5 shares @ 150.00
        assertThat(matches.get(0).matchQuantity()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(matches.get(0).matchPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
        // Match 2: 3 shares @ 152.00
        assertThat(matches.get(1).matchQuantity()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(matches.get(1).matchPrice()).isEqualByComparingTo(new BigDecimal("152.00"));

        assertThat(incomingBuy.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(orderBook.getActiveOrderCount()).isEqualTo(1); // ask2 remains with 2 shares
    }

    @Test
    void cancelOrder_shouldRemoveFromBook() {
        Order buy = new Order(portfolioA, "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("10"), new BigDecimal("150.00"), null);
        buy.setId(UUID.randomUUID());
        orderBook.addOrder(buy);

        Order cancelled = orderBook.cancelOrder(buy.getId());

        assertThat(cancelled).isNotNull();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderBook.getActiveOrderCount()).isEqualTo(0);
        assertThat(orderBook.getBestBid()).isNull();
    }
}
