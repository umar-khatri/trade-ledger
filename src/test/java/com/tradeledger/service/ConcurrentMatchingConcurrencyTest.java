package com.tradeledger.service;

import com.tradeledger.domain.model.Order;
import com.tradeledger.domain.model.OrderSide;
import com.tradeledger.domain.model.OrderStatus;
import com.tradeledger.domain.model.OrderType;
import com.tradeledger.domain.model.Portfolio;
import com.tradeledger.domain.model.Position;
import com.tradeledger.domain.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency load test simulating high-throughput concurrent order placement and matching.
 * Validates critical financial invariants:
 *  1. No negative cash balances on any portfolio
 *  2. No negative share quantities (no oversold positions)
 *  3. Conservation of value (zero lost updates or slippage leakage)
 */
class ConcurrentMatchingConcurrencyTest {

    private OrderBook orderBook;
    private final Map<UUID, Portfolio> portfolios = new ConcurrentHashMap<>();
    private final Map<String, Position> positions = new ConcurrentHashMap<>();
    private final List<Trade> executedTrades = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger matchCounter = new AtomicInteger();

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook("AAPL");
        portfolios.clear();
        positions.clear();
        executedTrades.clear();
        matchCounter.set(0);
    }

    private String posKey(UUID portfolioId, String symbol) {
        return portfolioId + ":" + symbol;
    }

    @Test
    @DisplayName("Place 100 concurrent orders across multiple threads and verify financial invariants")
    void concurrentOrders_shouldPreserveFinancialInvariants() throws InterruptedException {
        int numThreads = 10;
        int ordersPerThread = 10;
        int totalOrders = numThreads * ordersPerThread;

        // Initialize 5 Buyer portfolios with 100,000 cash each
        List<UUID> buyers = new ArrayList<>();
        BigDecimal totalStartingBuyerCash = BigDecimal.ZERO;
        for (int i = 0; i < 5; i++) {
            UUID buyerId = UUID.randomUUID();
            Portfolio buyer = new Portfolio("Buyer-" + i);
            buyer.depositCash(new BigDecimal("100000.00"));
            portfolios.put(buyerId, buyer);
            buyers.add(buyerId);
            totalStartingBuyerCash = totalStartingBuyerCash.add(new BigDecimal("100000.00"));
        }

        // Initialize 5 Seller portfolios with 0 cash and 500 AAPL shares each
        List<UUID> sellers = new ArrayList<>();
        BigDecimal totalStartingShares = BigDecimal.ZERO;
        for (int i = 0; i < 5; i++) {
            UUID sellerId = UUID.randomUUID();
            Portfolio seller = new Portfolio("Seller-" + i);
            portfolios.put(sellerId, seller);
            sellers.add(sellerId);

            Position sellerPos = new Position(sellerId, "AAPL");
            sellerPos.addShares(new BigDecimal("500"), new BigDecimal("150.00"));
            positions.put(posKey(sellerId, "AAPL"), sellerPos);
            totalStartingShares = totalStartingShares.add(new BigDecimal("500"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        for (int threadIdx = 0; threadIdx < numThreads; threadIdx++) {
            final int tId = threadIdx;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Simultaneous burst start

                    for (int o = 0; o < ordersPerThread; o++) {
                        boolean isBuy = (tId % 2 == 0);
                        UUID pid = isBuy ? buyers.get(o % buyers.size()) : sellers.get(o % sellers.size());

                        // Prices oscillate around 150.00
                        BigDecimal price = new BigDecimal("150.00");
                        BigDecimal qty = new BigDecimal("2"); // 2 shares per order

                        Order order = new Order(
                                pid,
                                "AAPL",
                                isBuy ? OrderSide.BUY : OrderSide.SELL,
                                OrderType.LIMIT,
                                qty,
                                price,
                                "concurrent-" + tId + "-" + o
                        );
                        order.setId(UUID.randomUUID());

                        // Thread-safe order book matching & synchronized ledger settlement
                        List<OrderBook.Match> matches = orderBook.match(order);

                        for (OrderBook.Match match : matches) {
                            UUID buyOrderId = order.getSide() == OrderSide.BUY ? order.getId() : match.restingOrder().getId();
                            UUID sellOrderId = order.getSide() == OrderSide.SELL ? order.getId() : match.restingOrder().getId();

                            UUID buyerPid = order.getSide() == OrderSide.BUY ? order.getPortfolioId() : match.restingOrder().getPortfolioId();
                            UUID sellerPid = order.getSide() == OrderSide.SELL ? order.getPortfolioId() : match.restingOrder().getPortfolioId();

                            settleTrade(buyerPid, sellerPid, buyOrderId, sellOrderId, match.matchPrice(), match.matchQuantity());
                        }

                        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                            orderBook.addOrder(order);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // ==================== INVARIANT ASSERTIONS ====================

        // Invariant 1: No negative cash balances on any portfolio
        for (Portfolio p : portfolios.values()) {
            assertThat(p.getCashBalance())
                    .as("Portfolio %s has negative cash balance!", p.getName())
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }

        // Invariant 2: No negative share quantities (no oversold positions)
        for (Position pos : positions.values()) {
            assertThat(pos.getQuantity())
                    .as("Position for %s has negative shares!", pos.getPortfolioId())
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }

        // Invariant 3: Conservation of shares
        BigDecimal totalEndingShares = BigDecimal.ZERO;
        for (Position pos : positions.values()) {
            totalEndingShares = totalEndingShares.add(pos.getQuantity());
        }
        assertThat(totalEndingShares)
                .as("Total shares across the system must remain strictly conserved")
                .isEqualByComparingTo(totalStartingShares);

        // Invariant 4: Conservation of cash
        BigDecimal totalEndingCash = BigDecimal.ZERO;
        for (Portfolio p : portfolios.values()) {
            totalEndingCash = totalEndingCash.add(p.getCashBalance());
        }
        assertThat(totalEndingCash)
                .as("Total cash across the system must remain strictly conserved")
                .isEqualByComparingTo(totalStartingBuyerCash);

        // Invariant 5: Successful executions occurred
        assertThat(executedTrades).isNotEmpty();
    }

    private synchronized void settleTrade(
            UUID buyerPid,
            UUID sellerPid,
            UUID buyOrderId,
            UUID sellOrderId,
            BigDecimal price,
            BigDecimal qty) {
        Portfolio buyer = portfolios.get(buyerPid);
        Portfolio seller = portfolios.get(sellerPid);

        BigDecimal tradeAmount = price.multiply(qty);

        // Deduct from buyer, credit to seller
        buyer.deductCash(tradeAmount);
        seller.depositCash(tradeAmount);

        // Update positions
        Position buyerPos = positions.computeIfAbsent(posKey(buyerPid, "AAPL"), k -> new Position(buyerPid, "AAPL"));
        buyerPos.addShares(qty, price);

        Position sellerPos = positions.get(posKey(sellerPid, "AAPL"));
        sellerPos.removeShares(qty);

        Trade trade = new Trade(buyOrderId, sellOrderId, "AAPL", price, qty);
        executedTrades.add(trade);
        matchCounter.incrementAndGet();
    }
}

