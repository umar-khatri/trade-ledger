package com.tradeledger.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PositionResponse(
        UUID id,
        UUID portfolioId,
        String symbol,
        BigDecimal quantity,
        BigDecimal avgCost,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnL,
        BigDecimal weight
) {
}

