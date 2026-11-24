package com.tradeledger.service;

import com.tradeledger.dto.request.CreatePortfolioRequest;
import com.tradeledger.dto.request.DepositRequest;
import com.tradeledger.dto.response.PortfolioResponse;
import com.tradeledger.dto.response.PositionResponse;
import com.tradeledger.domain.model.Portfolio;
import com.tradeledger.domain.model.Position;
import com.tradeledger.exception.ResourceNotFoundException;
import com.tradeledger.repository.PortfolioRepository;
import com.tradeledger.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private PriceProvider priceProvider;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private PortfolioService portfolioService;

    private UUID portfolioId;

    @BeforeEach
    void setUp() {
        portfolioId = UUID.randomUUID();
    }

    @Test
    void createPortfolio_shouldReturnCreatedPortfolio() {
        CreatePortfolioRequest request = new CreatePortfolioRequest("Test Portfolio");
        Portfolio saved = new Portfolio("Test Portfolio");

        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(saved);

        PortfolioResponse response = portfolioService.createPortfolio(request);

        assertThat(response.name()).isEqualTo("Test Portfolio");
        assertThat(response.cashBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getPortfolio_shouldThrowWhenNotFound() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.getPortfolio(portfolioId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(portfolioId.toString());
    }

    @Test
    void getPortfolio_shouldReturnPortfolioWhenExists() {
        Portfolio portfolio = new Portfolio("My Portfolio");
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        PortfolioResponse response = portfolioService.getPortfolio(portfolioId);

        assertThat(response.name()).isEqualTo("My Portfolio");
    }

    @Test
    void deposit_shouldIncreaseCashBalance() {
        Portfolio portfolio = new Portfolio("My Portfolio");
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));

        DepositRequest request = new DepositRequest(new BigDecimal("5000.00"));
        PortfolioResponse response = portfolioService.deposit(portfolioId, request);

        assertThat(response.cashBalance()).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    void deposit_multipleTimes_shouldAccumulate() {
        Portfolio portfolio = new Portfolio("My Portfolio");
        portfolio.depositCash(new BigDecimal("1000.00"));
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));

        DepositRequest request = new DepositRequest(new BigDecimal("2500.00"));
        PortfolioResponse response = portfolioService.deposit(portfolioId, request);

        assertThat(response.cashBalance()).isEqualByComparingTo(new BigDecimal("3500.00"));
    }

    @Test
    void getPositions_shouldReturnEmptyListForNewPortfolio() {
        Portfolio portfolio = new Portfolio("Empty Portfolio");
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());

        List<PositionResponse> positions = portfolioService.getPositions(portfolioId);

        assertThat(positions).isEmpty();
    }

    @Test
    void getPositions_shouldComputeMarketValueAndPnL() {
        Portfolio portfolio = new Portfolio("Portfolio");
        portfolio.depositCash(new BigDecimal("10000.00"));
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Position pos = new Position(portfolioId, "AAPL");
        pos.addShares(new BigDecimal("10"), new BigDecimal("150.00"));

        when(positionRepository.findByPortfolioId(portfolioId)).thenReturn(List.of(pos));
        when(priceProvider.getPrice("AAPL")).thenReturn(new BigDecimal("160.00"));

        List<PositionResponse> positions = portfolioService.getPositions(portfolioId);

        assertThat(positions).hasSize(1);
        PositionResponse p = positions.getFirst();
        assertThat(p.symbol()).isEqualTo("AAPL");
        assertThat(p.quantity()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(p.avgCost()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(p.currentPrice()).isEqualByComparingTo(new BigDecimal("160.00"));
        // market value = 10 * 160 = 1600
        assertThat(p.marketValue()).isEqualByComparingTo(new BigDecimal("1600.00"));
        // unrealized P&L = 1600 - 1500 = 100
        assertThat(p.unrealizedPnL()).isEqualByComparingTo(new BigDecimal("100.00"));
        // weight = 1600 / (1600 + 10000) = 0.137931
        assertThat(p.weight()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void getPositions_shouldThrowWhenPortfolioNotFound() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.getPositions(portfolioId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listPortfolios_shouldReturnAllPortfolios() {
        Portfolio p1 = new Portfolio("Portfolio 1");
        Portfolio p2 = new Portfolio("Portfolio 2");
        when(portfolioRepository.findAll()).thenReturn(List.of(p1, p2));

        List<PortfolioResponse> result = portfolioService.listPortfolios();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Portfolio 1");
        assertThat(result.get(1).name()).isEqualTo("Portfolio 2");
    }
}

