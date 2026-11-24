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
import com.tradeledger.repository.OrderRepository;
import com.tradeledger.repository.PortfolioRepository;
import com.tradeledger.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private MatchingEngine matchingEngine;

    @Mock
    private OrderExecutionService orderExecutionService;

    @Mock
    private PriceService priceService;

    @Mock
    private DomainEventPublisher eventPublisher;

    private OrderService orderService;

    private UUID portfolioId;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                portfolioRepository,
                positionRepository,
                matchingEngine,
                orderExecutionService,
                priceService,
                eventPublisher,
                3
        );
        portfolioId = UUID.randomUUID();
    }

    @Test
    void placeOrder_shouldReturnExistingIfIdempotencyKeyMatches() {
        Order existing = new Order(portfolioId, "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("10"), new BigDecimal("150.00"), "idem-key-1");
        existing.setId(UUID.randomUUID());

        when(orderRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.of(existing));

        PlaceOrderRequest request = new PlaceOrderRequest(
                "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("10"), new BigDecimal("150.00"));

        OrderResponse response = orderService.placeOrder(portfolioId, request, "idem-key-1");

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.idempotencyKey()).isEqualTo("idem-key-1");
    }

    @Test
    void placeOrder_insufficientFunds_shouldThrow() {
        Portfolio portfolio = new Portfolio("Test Portfolio");
        portfolio.depositCash(new BigDecimal("500.00")); // only 500

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Attempting to buy 10 shares @ 150 = 1500 required
        PlaceOrderRequest request = new PlaceOrderRequest(
                "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("10"), new BigDecimal("150.00"));

        assertThatThrownBy(() -> orderService.placeOrder(portfolioId, request, null))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void placeOrder_insufficientShares_shouldThrow() {
        Portfolio portfolio = new Portfolio("Test Portfolio");
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Position position = new Position(portfolioId, "AAPL");
        position.setQuantity(new BigDecimal("5")); // only 5 shares

        when(positionRepository.findByPortfolioIdAndSymbol(portfolioId, "AAPL"))
                .thenReturn(Optional.of(position));

        // Attempting to sell 10 shares
        PlaceOrderRequest request = new PlaceOrderRequest(
                "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("10"), new BigDecimal("150.00"));

        assertThatThrownBy(() -> orderService.placeOrder(portfolioId, request, null))
                .isInstanceOf(InsufficientSharesException.class);
    }

    @Test
    void placeOrder_limitOrderWithoutPrice_shouldThrow() {
        Portfolio portfolio = new Portfolio("Test Portfolio");
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        PlaceOrderRequest request = new PlaceOrderRequest(
                "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("10"), null);

        assertThatThrownBy(() -> orderService.placeOrder(portfolioId, request, null))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void placeOrder_limitOrderNoMatch_shouldRestInBook() {
        Portfolio portfolio = new Portfolio("Test Portfolio");
        portfolio.depositCash(new BigDecimal("5000.00"));
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        ReentrantLock lock = new ReentrantLock();
        when(matchingEngine.getLock("AAPL")).thenReturn(lock);

        Order savedOrder = new Order(portfolioId, "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("10"), new BigDecimal("150.00"), null);
        savedOrder.setId(UUID.randomUUID());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderRepository.findById(savedOrder.getId())).thenReturn(Optional.of(savedOrder));
        when(matchingEngine.match(any(Order.class))).thenReturn(List.of());

        PlaceOrderRequest request = new PlaceOrderRequest(
                "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("10"), new BigDecimal("150.00"));

        OrderResponse response = orderService.placeOrder(portfolioId, request, null);

        assertThat(response.status()).isEqualTo(OrderStatus.NEW);
        verify(matchingEngine).addOrder(savedOrder);
    }

    @Test
    void cancelOrder_shouldCancelActiveOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(portfolioId, "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("10"), new BigDecimal("150.00"), null);
        order.setId(orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(matchingEngine.getLock("AAPL")).thenReturn(new ReentrantLock());
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.cancelOrder(orderId);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(matchingEngine).cancelOrder(eq("AAPL"), eq(orderId));
    }
}

