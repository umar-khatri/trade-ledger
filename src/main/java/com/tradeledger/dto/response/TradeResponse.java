package com.tradeledger.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TradeResponse(
        UUID id,
        UUID buyOrderId,
        UUID sellOrderId,
        String symbol,
        BigDecimal price,
        BigDecimal quantity,
        LocalDateTime executedAt
) {
}

