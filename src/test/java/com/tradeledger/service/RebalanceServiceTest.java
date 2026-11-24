package com.tradeledger.service;

import com.tradeledger.domain.model.OrderSide;
import com.tradeledger.domain.model.OrderStatus;
import com.tradeledger.domain.model.OrderType;
import com.tradeledger.domain.model.Portfolio;
import com.tradeledger.domain.model.Position;
import com.tradeledger.domain.model.TargetAllocation;
import com.tradeledger.dto.request.SetTargetAllocationRequest;
import com.tradeledger.dto.response.OrderResponse;
import com.tradeledger.dto.response.RebalancePreviewResponse;
import com.tradeledger.dto.response.TargetAllocationResponse;
import com.tradeledger.exception.InvalidAllocationException;
import com.tradeledger.repository.PortfolioRepository;
import com.tradeledger.repository.PositionRepository;
import com.tradeledger.repository.TargetAllocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RebalanceServiceTest {

    @Mock
    private TargetAllocationRepository targetAllocationRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private PriceService priceService;

    @Mock
    private OrderService orderService;

    @Mock
    private DomainEventPublisher eventPublisher;

    private RebalanceService rebalanceService;

    private UUID portfolioId;

    @BeforeEach
    void setUp() {
        rebalanceService = new RebalanceService(
                targetAllocationRepository,
                portfolioRepository,
                positionRepository,
                priceService,
                orderService,
                eventPublisher,
                new BigDecimal("0.02"), // 2% drift threshold
                new BigDecimal("100.00") // 100 min cash buffer
        );
        portfolioId = UUID.randomUUID();
    }

    @Test
    void setTargetAllocations_shouldThrowWhenSumNotOne() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(new Portfolio("Test")));

        SetTargetAllocationRequest request = new SetTargetAllocationRequest(List.of(
                new SetTargetAllocationRequest.AllocationItem("AAPL", new BigDecimal("0.50")),
                new SetTargetAllocationRequest.AllocationItem("GOOGL", new BigDecimal("0.40")) // sums to 0.90!
        ));

        assertThatThrownBy(() -> rebalanceService.setTargetAllocations(portfolioId, request))
                .isInstanceOf(InvalidAllocationException.class)
                .hasMessageContaining("must sum to 1.0");
    }

    @Test
    void setTargetAllocations_shouldSaveWhenSumIsOne() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(new Portfolio("Test")));

        SetTargetAllocationRequest request = new SetTargetAllocationRequest(List.of(
                new SetTargetAllocationRequest.AllocationItem("AAPL", new BigDecimal("0.60")),
                new SetTargetAllocationRequest.AllocationItem("GOOGL", new BigDecimal("0.40"))
        ));

        TargetAllocation t1 = new TargetAllocation(portfolioId, "AAPL", new BigDecimal("0.60"));
        TargetAllocation t2 = new TargetAllocation(portfolioId, "GOOGL", new BigDecimal("0.40"));
        when(targetAllocationRepository.saveAll(any())).thenReturn(List.of(t1, t2));

        List<TargetAllocationResponse> responses = rebalanceService.setTargetAllocations(portfolioId, request);

        assertThat(responses).hasSize(2);
        verify(targetAllocationRepository).deleteByPortfolioId(portfolioId);
    }

    @Test
    void previewRebalance_whenDriftExceedsThreshold_shouldGenerateOrdersWithSellsFirst() {
        // Portfolio cash: 1,000
        Portfolio portfolio = new Portfolio("Test Portfolio");
        portfolio.depositCash(new BigDecimal("1000.00"));
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Targets: AAPL 50%, GOOGL 50%
        TargetAllocation tAapl = new TargetAllocation(portfolioId, "AAPL", new BigDecimal("0.500000"));
        TargetAllocation tGoogl = new TargetAllocation(portfolioId, "GOOGL", new BigDecimal("0.500000"));
        when(targetAllocationRepository.findByPortfolioId(portfolioId)).thenReturn(List.of(tAapl, tGoogl));

        // Current holdings: AAPL: 50 shares @ 100 = 5000, GOOGL: 0
        // Total value = 1,000 cash + 5,000 stock = 6,000
        Position pAapl = new Position(portfolioId, "AAPL");
        pAapl.setQuantity(new BigDecimal("50"));
        when(positionRepository.findByPortfolioId(portfolioId)).thenReturn(List.of(pAapl));

        when(priceService.getPrice("AAPL")).thenReturn(new BigDecimal("100.0000"));
        when(priceService.getPrice("GOOGL")).thenReturn(new BigDecimal("100.0000"));

        RebalancePreviewResponse preview = rebalanceService.previewRebalance(portfolioId);

        assertThat(preview.rebalanceRequired()).isTrue();
        assertThat(preview.proposedOrders()).isNotEmpty();

        // Target for AAPL: 50% of (6000 - 100 buffer = 5900) = 2950 (or ~29.5 shares).
        // AAPL currently has 5000 -> Overweight by ~2050 -> SELL orders should come first!
        RebalancePreviewResponse.ProposedOrder firstOrder = preview.proposedOrders().getFirst();
        assertThat(firstOrder.side()).isEqualTo(OrderSide.SELL);
        assertThat(firstOrder.symbol()).isEqualTo("AAPL");

        // Followed by BUY for GOOGL
        RebalancePreviewResponse.ProposedOrder secondOrder = preview.proposedOrders().get(1);
        assertThat(secondOrder.side()).isEqualTo(OrderSide.BUY);
        assertThat(secondOrder.symbol()).isEqualTo("GOOGL");

        // Verify quantities are integers (whole shares)
        assertThat(firstOrder.quantity().stripTrailingZeros().scale()).isLessThanOrEqualTo(0);
        assertThat(secondOrder.quantity().stripTrailingZeros().scale()).isLessThanOrEqualTo(0);
    }

    @Test
    void executeRebalance_shouldSubmitOrdersThroughOrderService() {
        Portfolio portfolio = new Portfolio("Test Portfolio");
        portfolio.depositCash(new BigDecimal("1000.00"));
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        TargetAllocation tAapl = new TargetAllocation(portfolioId, "AAPL", new BigDecimal("0.500000"));
        TargetAllocation tGoogl = new TargetAllocation(portfolioId, "GOOGL", new BigDecimal("0.500000"));
        when(targetAllocationRepository.findByPortfolioId(portfolioId)).thenReturn(List.of(tAapl, tGoogl));

        Position pAapl = new Position(portfolioId, "AAPL");
        pAapl.setQuantity(new BigDecimal("50"));
        when(positionRepository.findByPortfolioId(portfolioId)).thenReturn(List.of(pAapl));

        when(priceService.getPrice("AAPL")).thenReturn(new BigDecimal("100.0000"));
        when(priceService.getPrice("GOOGL")).thenReturn(new BigDecimal("100.0000"));

        OrderResponse dummyResponse = new OrderResponse(
                UUID.randomUUID(), portfolioId, "AAPL", OrderSide.SELL, OrderType.MARKET,
                new BigDecimal("20"), null, BigDecimal.ZERO, new BigDecimal("20"),
                OrderStatus.NEW, null, LocalDateTime.now());
        when(orderService.placeOrder(eq(portfolioId), any(), any())).thenReturn(dummyResponse);

        List<OrderResponse> result = rebalanceService.executeRebalance(portfolioId);

        assertThat(result).isNotEmpty();
        verify(orderService, org.mockito.Mockito.atLeastOnce()).placeOrder(eq(portfolioId), any(), any());
    }
}
