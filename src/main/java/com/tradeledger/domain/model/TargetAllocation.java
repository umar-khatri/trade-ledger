package com.tradeledger.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "target_allocation", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"portfolio_id", "symbol"})
})
public class TargetAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "target_weight", nullable = false, precision = 7, scale = 6)
    private BigDecimal targetWeight;

    protected TargetAllocation() {
    }

    public TargetAllocation(UUID portfolioId, String symbol, BigDecimal targetWeight) {
        this.portfolioId = portfolioId;
        this.symbol = symbol;
        this.targetWeight = targetWeight;
    }

    // ---- Getters & Setters ----

    public UUID getId() {
        return id;
    }

    public UUID getPortfolioId() {
        return portfolioId;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getTargetWeight() {
        return targetWeight;
    }

    public void setTargetWeight(BigDecimal targetWeight) {
        this.targetWeight = targetWeight;
    }
}

