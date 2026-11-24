package com.tradeledger.service;

import com.tradeledger.domain.model.Portfolio;
import com.tradeledger.domain.model.Position;
import com.tradeledger.dto.request.CreatePortfolioRequest;
import com.tradeledger.dto.request.DepositRequest;
import com.tradeledger.dto.response.PortfolioResponse;
import com.tradeledger.dto.response.PositionResponse;
import com.tradeledger.exception.ResourceNotFoundException;
import com.tradeledger.repository.PortfolioRepository;
import com.tradeledger.repository.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final PriceProvider priceProvider;
    private final DomainEventPublisher eventPublisher;

    public PortfolioService(PortfolioRepository portfolioRepository,
                            PositionRepository positionRepository,
                            PriceProvider priceProvider,
                            DomainEventPublisher eventPublisher) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.priceProvider = priceProvider;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PortfolioResponse createPortfolio(CreatePortfolioRequest request) {
        Portfolio portfolio = new Portfolio(request.name());
        portfolio = portfolioRepository.save(portfolio);
        return toResponse(portfolio);
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(UUID id) {
        Portfolio portfolio = findPortfolioOrThrow(id);
        return toResponse(portfolio);
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> listPortfolios() {
        return portfolioRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PortfolioResponse deposit(UUID portfolioId, DepositRequest request) {
        Portfolio portfolio = findPortfolioOrThrow(portfolioId);
        portfolio.depositCash(request.amount());
        portfolio = portfolioRepository.save(portfolio);
        if (eventPublisher != null) {
            eventPublisher.publish(new com.tradeledger.domain.event.CashDepositedEvent(
                    portfolioId, request.amount(), portfolio.getCashBalance()));
        }
        return toResponse(portfolio);
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(UUID portfolioId) {
        findPortfolioOrThrow(portfolioId);
        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);

        // Compute total portfolio market value for weight calculation
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        for (Position pos : positions) {
            BigDecimal price = getPrice(pos.getSymbol());
            totalMarketValue = totalMarketValue.add(pos.getQuantity().multiply(price));
        }

        // Also add cash to the total value for weight calculation
        Portfolio portfolio = findPortfolioOrThrow(portfolioId);
        BigDecimal totalPortfolioValue = totalMarketValue.add(portfolio.getCashBalance());

        final BigDecimal totalVal = totalPortfolioValue;
        return positions.stream()
                .map(pos -> toPositionResponse(pos, totalVal))
                .toList();
    }

    public Portfolio findPortfolioOrThrow(UUID id) {
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", id));
    }

    private PositionResponse toPositionResponse(Position position, BigDecimal totalPortfolioValue) {
        BigDecimal currentPrice = getPrice(position.getSymbol());
        BigDecimal marketValue = position.getQuantity().multiply(currentPrice);
        BigDecimal costBasis = position.getQuantity().multiply(position.getAvgCost());
        BigDecimal unrealizedPnL = marketValue.subtract(costBasis);

        BigDecimal weight = BigDecimal.ZERO;
        if (totalPortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
            weight = marketValue.divide(totalPortfolioValue, 6, RoundingMode.HALF_UP);
        }

        return new PositionResponse(
                position.getId(),
                position.getPortfolioId(),
                position.getSymbol(),
                position.getQuantity(),
                position.getAvgCost(),
                currentPrice,
                marketValue,
                unrealizedPnL,
                weight
        );
    }

    private BigDecimal getPrice(String symbol) {
        BigDecimal price = priceProvider.getPrice(symbol);
        return price != null ? price : BigDecimal.ZERO;
    }

    private PortfolioResponse toResponse(Portfolio portfolio) {
        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getCashBalance(),
                portfolio.getCreatedAt()
        );
    }
}

