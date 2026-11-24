package com.tradeledger.domain.event;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CashDepositedEvent extends DomainEvent {

    private final BigDecimal amount;
    private final BigDecimal newBalance;

    public CashDepositedEvent(UUID portfolioId, BigDecimal amount, BigDecimal newBalance) {
        super("CASH_DEPOSITED", portfolioId);
        this.amount = amount;
        this.newBalance = newBalance;
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amount.toPlainString());
        payload.put("newBalance", newBalance.toPlainString());
        return payload;
    }
}

