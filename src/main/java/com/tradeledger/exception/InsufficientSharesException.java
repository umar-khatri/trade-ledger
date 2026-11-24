package com.tradeledger.exception;

import java.math.BigDecimal;

public class InsufficientSharesException extends RuntimeException {

    private final String symbol;
    private final BigDecimal requested;
    private final BigDecimal available;

    public InsufficientSharesException(String symbol, BigDecimal requested, BigDecimal available) {
        super(String.format("Insufficient shares for %s. Requested: %s, Available: %s",
                symbol, requested.toPlainString(), available.toPlainString()));
        this.symbol = symbol;
        this.requested = requested;
        this.available = available;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getRequested() {
        return requested;
    }

    public BigDecimal getAvailable() {
        return available;
    }
}

