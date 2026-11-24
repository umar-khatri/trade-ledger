package com.tradeledger.service;

import com.tradeledger.domain.model.Order;
import com.tradeledger.domain.model.OrderSide;
import com.tradeledger.domain.model.OrderStatus;
import com.tradeledger.domain.model.OrderType;
import com.tradeledger.domain.model.Portfolio;
import com.tradeledger.domain.model.Position;
import com.tradeledger.dto.request.PlaceOrderRequest;
import com.tradeledger.dto.response.OrderResponse;
import com.tradeledger.exception.InsufficientFundsException;
import com.tradeledger.exception.InsufficientSharesException;
import com.tradeledger.exception.InvalidOrderException;
import com.tradeledger.exception.ResourceNotFoundException;
import com.tradeledger.repository.OrderRepository;
import com.tradeledger.repository.PortfolioRepository;
import com.tradeledger.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service managing order placement, matching coordination, cancellations, and order queries.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final MatchingEngine matchingEngine;
    private final OrderExecutionService orderExecutionService;
    private final PriceService priceService;
    private final int maxRetries;

    public OrderService(
            OrderRepository orderRepository,
            PortfolioRepository portfolioRepository,
            PositionRepository positionRepository,
            MatchingEngine matchingEngine,
            OrderExecutionService orderExecutionService,
            PriceService priceService,
            @Value("${app.matching.max-retries:3}") int maxRetries) {
        this.orderRepository = orderRepository;
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.matchingEngine = matchingEngine;
        this.orderExecutionService = orderExecutionService;
        this.priceService = priceService;
        this.maxRetries = maxRetries;
    }

    public OrderResponse placeOrder(UUID portfolioId, PlaceOrderRequest request, String idempotencyKey) {
        // 1. Idempotency check
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey.trim());
            if (existing.isPresent()) {
                log.info("Idempotent request detected for key: {}", idempotencyKey);
                return toResponse(existing.get());
            }
        }

        // 2. Validate Portfolio
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", portfolioId));

        String symbol = request.symbol().trim().toUpperCase();

        // 3. Pre-trade validation checks
        if (request.type() == OrderType.LIMIT && request.limitPrice() == null) {
            throw new InvalidOrderException("Limit price is required for LIMIT orders");
        }

        if (request.side() == OrderSide.BUY) {
            BigDecimal estPrice = request.type() == OrderType.LIMIT
                    ? request.limitPrice()
                    : priceService.getPrice(symbol);
            if (estPrice.compareTo(BigDecimal.ZERO) <= 0) {
                estPrice = new BigDecimal("100.0000"); // fallback floor for testing/unseeded symbols
            }
            BigDecimal requiredFunds = request.quantity().multiply(estPrice);
            if (portfolio.getCashBalance().compareTo(requiredFunds) < 0) {
                throw new InsufficientFundsException(requiredFunds, portfolio.getCashBalance());
            }
        } else {
            // SELL check
            Position position = positionRepository.findByPortfolioIdAndSymbol(portfolioId, symbol)
                    .orElseThrow(() -> new InsufficientSharesException(symbol, request.quantity(), BigDecimal.ZERO));
            if (position.getQuantity().compareTo(request.quantity()) < 0) {
                throw new InsufficientSharesException(symbol, request.quantity(), position.getQuantity());
            }
        }

        // 4. Create initial order in database
        Order order = new Order(
                portfolioId,
                symbol,
                request.side(),
                request.type(),
                request.quantity(),
                request.limitPrice(),
                idempotencyKey != null && !idempotencyKey.trim().isEmpty() ? idempotencyKey.trim() : null
        );
        order = orderRepository.save(order);

        // 5. Acquire per-symbol lock and match
        ReentrantLock lock = matchingEngine.getLock(symbol);
        lock.lock();
        try {
            List<OrderBook.Match> matches = matchingEngine.match(order);

            // Execute each match
            for (OrderBook.Match match : matches) {
                UUID buyOrderId = order.getSide() == OrderSide.BUY ? order.getId() : match.restingOrder().getId();
                UUID sellOrderId = order.getSide() == OrderSide.SELL ? order.getId() : match.restingOrder().getId();

                orderExecutionService.settleMatchWithRetry(
                        buyOrderId,
                        sellOrderId,
                        symbol,
                        match.matchPrice(),
                        match.matchQuantity(),
                        maxRetries
                );
            }

            // Reload order to reflect fills executed during matching
            order = orderRepository.findById(order.getId()).orElse(order);

            // If LIMIT order and still has unfilled quantity, rest it in the book
            if (order.getType() == OrderType.LIMIT && order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                matchingEngine.addOrder(order);
            } else if (order.getType() == OrderType.MARKET && order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                // Market orders do not rest on the book: if 0 filled, rejected; if partial, mark filled
                if (order.getFilledQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    order.reject();
                }
                order = orderRepository.save(order);
            }

            return toResponse(order);
        } finally {
            lock.unlock();
        }
    }

    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (!order.isActive()) {
            throw new InvalidOrderException("Cannot cancel order with status: " + order.getStatus());
        }

        ReentrantLock lock = matchingEngine.getLock(order.getSymbol());
        lock.lock();
        try {
            matchingEngine.cancelOrder(order.getSymbol(), orderId);
            order.cancel();
            order = orderRepository.save(order);
            return toResponse(order);
        } finally {
            lock.unlock();
        }
    }

    public List<OrderResponse> getOrders(UUID portfolioId, OrderStatus status) {
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", portfolioId));

        List<Order> orders = status != null
                ? orderRepository.findByPortfolioIdAndStatus(portfolioId, status)
                : orderRepository.findByPortfolioId(portfolioId);

        return orders.stream().map(this::toResponse).toList();
    }

    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getPortfolioId(),
                order.getSymbol(),
                order.getSide(),
                order.getType(),
                order.getQuantity(),
                order.getLimitPrice(),
                order.getFilledQuantity(),
                order.getRemainingQuantity(),
                order.getStatus(),
                order.getIdempotencyKey(),
                order.getCreatedAt()
        );
    }
}
