package com.tradeledger.domain.event;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OrderCancelledEvent extends DomainEvent {

    private final UUID orderId;
    private final String symbol;
    private final BigDecimal unfilledQuantity;

    public OrderCancelledEvent(UUID portfolioId, UUID orderId, String symbol,
                               BigDecimal unfilledQuantity) {
        super("ORDER_CANCELLED", portfolioId);
        this.orderId = orderId;
        this.symbol = symbol;
        this.unfilledQuantity = unfilledQuantity;
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("symbol", symbol);
        payload.put("unfilledQuantity", unfilledQuantity.toPlainString());
        return payload;
    }
}
