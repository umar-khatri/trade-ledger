package com.tradeledger.repository;

import com.tradeledger.domain.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {

    List<Trade> findBySymbolOrderByExecutedAtDesc(String symbol);

    @Query("""
            SELECT t FROM Trade t
            WHERE t.buyOrderId IN (SELECT o.id FROM Order o WHERE o.portfolioId = :portfolioId)
               OR t.sellOrderId IN (SELECT o.id FROM Order o WHERE o.portfolioId = :portfolioId)
            ORDER BY t.executedAt DESC
            """)
    List<Trade> findByPortfolioId(@Param("portfolioId") UUID portfolioId);
}

