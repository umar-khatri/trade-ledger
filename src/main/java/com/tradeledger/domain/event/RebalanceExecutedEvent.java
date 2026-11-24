package com.tradeledger.domain.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RebalanceExecutedEvent extends DomainEvent {

    private final int ordersGenerated;
    private final List<String> orderIds;

    public RebalanceExecutedEvent(UUID portfolioId, int ordersGenerated,
                                  List<String> orderIds) {
        super("REBALANCE_EXECUTED", portfolioId);
        this.ordersGenerated = ordersGenerated;
        this.orderIds = orderIds;
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ordersGenerated", ordersGenerated);
        payload.put("orderIds", orderIds);
        return payload;
    }
}
