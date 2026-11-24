package com.tradeledger.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    private final BigDecimal required;
    private final BigDecimal available;

    public InsufficientFundsException(BigDecimal required, BigDecimal available) {
        super(String.format("Insufficient cash balance. Required: %s, Available: %s",
                required.toPlainString(), available.toPlainString()));
        this.required = required;
        this.available = available;
    }

    public BigDecimal getRequired() {
        return required;
    }

    public BigDecimal getAvailable() {
        return available;
    }
}
