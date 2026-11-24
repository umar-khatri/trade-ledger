package com.tradeledger.domain.event;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OrderCreatedEvent extends DomainEvent {

    private final UUID orderId;
    private final String symbol;
    private final String side;
    private final String type;
    private final BigDecimal quantity;
    private final BigDecimal limitPrice;

    public OrderCreatedEvent(UUID portfolioId, UUID orderId, String symbol,
                             String side, String type, BigDecimal quantity,
                             BigDecimal limitPrice) {
        super("ORDER_CREATED", portfolioId);
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("symbol", symbol);
        payload.put("side", side);
        payload.put("type", type);
        payload.put("quantity", quantity.toPlainString());
        if (limitPrice != null) {
            payload.put("limitPrice", limitPrice.toPlainString());
        }
        return payload;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
