package com.tradeledger.domain.event;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OrderFilledEvent extends DomainEvent {

    private final UUID orderId;
    private final UUID tradeId;
    private final String symbol;
    private final BigDecimal fillPrice;
    private final BigDecimal fillQuantity;
    private final BigDecimal totalFilledQuantity;
    private final boolean fullyFilled;

    public OrderFilledEvent(UUID portfolioId, UUID orderId, UUID tradeId,
                            String symbol, BigDecimal fillPrice,
                            BigDecimal fillQuantity, BigDecimal totalFilledQuantity,
                            boolean fullyFilled) {
        super("ORDER_FILLED", portfolioId);
        this.orderId = orderId;
        this.tradeId = tradeId;
        this.symbol = symbol;
        this.fillPrice = fillPrice;
        this.fillQuantity = fillQuantity;
        this.totalFilledQuantity = totalFilledQuantity;
        this.fullyFilled = fullyFilled;
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("tradeId", tradeId.toString());
        payload.put("symbol", symbol);
        payload.put("fillPrice", fillPrice.toPlainString());
        payload.put("fillQuantity", fillQuantity.toPlainString());
        payload.put("totalFilledQuantity", totalFilledQuantity.toPlainString());
        payload.put("fullyFilled", fullyFilled);
        return payload;
    }
}
