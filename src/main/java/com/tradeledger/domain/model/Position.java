package com.tradeledger.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "position", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"portfolio_id", "symbol"})
})
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "avg_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal avgCost = BigDecimal.ZERO;

    @Version
    private Long version;

    protected Position() {
    }

    public Position(UUID portfolioId, String symbol) {
        this.portfolioId = portfolioId;
        this.symbol = symbol;
        this.quantity = BigDecimal.ZERO;
        this.avgCost = BigDecimal.ZERO;
    }

    /**
     * Adds shares to this position, updating the weighted-average cost.
     */
    public void addShares(BigDecimal qty, BigDecimal pricePerShare) {
        BigDecimal totalExistingCost = this.quantity.multiply(this.avgCost);
        BigDecimal totalNewCost = qty.multiply(pricePerShare);
        this.quantity = this.quantity.add(qty);
        if (this.quantity.compareTo(BigDecimal.ZERO) > 0) {
            this.avgCost = totalExistingCost.add(totalNewCost)
                    .divide(this.quantity, 4, RoundingMode.HALF_UP);
        }
    }

    /**
     * Removes shares from this position (avg cost stays unchanged).
     */
    public void removeShares(BigDecimal qty) {
        this.quantity = this.quantity.subtract(qty);
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public void setAvgCost(BigDecimal avgCost) {
        this.avgCost = avgCost;
    }

    public Long getVersion() {
        return version;
    }
}

