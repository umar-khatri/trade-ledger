package com.tradeledger.service;

import com.tradeledger.dto.response.TradeResponse;
import com.tradeledger.exception.ResourceNotFoundException;
import com.tradeledger.repository.PortfolioRepository;
import com.tradeledger.repository.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TradeService {

    private final TradeRepository tradeRepository;
    private final PortfolioRepository portfolioRepository;

    public TradeService(TradeRepository tradeRepository, PortfolioRepository portfolioRepository) {
        this.tradeRepository = tradeRepository;
        this.portfolioRepository = portfolioRepository;
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> getTradesForPortfolio(UUID portfolioId) {
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", portfolioId));

        return tradeRepository.findByPortfolioId(portfolioId).stream()
                .map(t -> new TradeResponse(
                        t.getId(),
                        t.getBuyOrderId(),
                        t.getSellOrderId(),
                        t.getSymbol(),
                        t.getPrice(),
                        t.getQuantity(),
                        t.getExecutedAt()
                ))
                .toList();
    }
}

