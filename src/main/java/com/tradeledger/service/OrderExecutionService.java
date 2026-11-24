package com.tradeledger.service;

import com.tradeledger.domain.model.Order;
import com.tradeledger.domain.model.OrderStatus;
import com.tradeledger.domain.model.Portfolio;
import com.tradeledger.domain.model.Position;
import com.tradeledger.domain.model.Trade;
import com.tradeledger.exception.InsufficientFundsException;
import com.tradeledger.exception.InsufficientSharesException;
import com.tradeledger.exception.ResourceNotFoundException;
import com.tradeledger.repository.OrderRepository;
import com.tradeledger.repository.PortfolioRepository;
import com.tradeledger.repository.PositionRepository;
import com.tradeledger.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Handles the transactional execution of matches, ensuring atomic cash and share movements
 * with optimistic locking retry capabilities.
 */
@Service
public class OrderExecutionService {

    private static final Logger log = LoggerFactory.getLogger(OrderExecutionService.class);

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;

    public OrderExecutionService(
            PortfolioRepository portfolioRepository,
            PositionRepository positionRepository,
            OrderRepository orderRepository,
            TradeRepository tradeRepository) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
    }

    /**
     * Executes settlement for a single match with optimistic locking retries.
     */
    public Trade settleMatchWithRetry(
            UUID buyOrderId,
            UUID sellOrderId,
            String symbol,
            BigDecimal matchPrice,
            BigDecimal matchQty,
            int maxRetries) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return executeSettlement(buyOrderId, sellOrderId, symbol, matchPrice, matchQty);
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Optimistic locking conflict on match execution (attempt {}/{}): {}",
                        attempts, maxRetries, ex.getMessage());
                if (attempts >= maxRetries) {
                    throw ex;
                }
                try {
                    Thread.sleep(50L * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted during retry", ie);
                }
            }
        }
    }

    @Transactional
    public Trade executeSettlement(
            UUID buyOrderId,
            UUID sellOrderId,
            String symbol,
            BigDecimal matchPrice,
            BigDecimal matchQty) {
        Order buyOrder = orderRepository.findById(buyOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", buyOrderId));
        Order sellOrder = orderRepository.findById(sellOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", sellOrderId));

        Portfolio buyer = portfolioRepository.findById(buyOrder.getPortfolioId())
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", buyOrder.getPortfolioId()));
        Portfolio seller = portfolioRepository.findById(sellOrder.getPortfolioId())
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", sellOrder.getPortfolioId()));

        BigDecimal tradeAmount = matchPrice.multiply(matchQty);

        // Deduct cash from buyer
        if (buyer.getCashBalance().compareTo(tradeAmount) < 0) {
            throw new InsufficientFundsException(tradeAmount, buyer.getCashBalance());
        }
        buyer.deductCash(tradeAmount);

        // Deposit cash to seller
        seller.depositCash(tradeAmount);

        // Update positions
        Position buyerPos = positionRepository.findByPortfolioIdAndSymbol(buyer.getId(), symbol)
                .orElseGet(() -> new Position(buyer.getId(), symbol));
        buyerPos.addShares(matchQty, matchPrice);

        Position sellerPos = positionRepository.findByPortfolioIdAndSymbol(seller.getId(), symbol)
                .orElseThrow(() -> new InsufficientSharesException(symbol, matchQty, BigDecimal.ZERO));
        if (sellerPos.getQuantity().compareTo(matchQty) < 0) {
            throw new InsufficientSharesException(symbol, matchQty, sellerPos.getQuantity());
        }
        sellerPos.removeShares(matchQty);

        // Update orders
        buyOrder.fill(matchQty);
        sellOrder.fill(matchQty);

        // Persist all updates atomically
        portfolioRepository.save(buyer);
        portfolioRepository.save(seller);
        positionRepository.save(buyerPos);
        positionRepository.save(sellerPos);
        orderRepository.save(buyOrder);
        orderRepository.save(sellOrder);

        // Record Trade
        Trade trade = new Trade(buyOrder.getId(), sellOrder.getId(), symbol, matchPrice, matchQty);
        return tradeRepository.save(trade);
    }
}
