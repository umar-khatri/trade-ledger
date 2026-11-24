package com.tradeledger.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TargetAllocationResponse(
        UUID id,
        UUID portfolioId,
        String symbol,
        BigDecimal targetWeight
) {
}
