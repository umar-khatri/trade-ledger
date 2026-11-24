package com.tradeledger.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PortfolioResponse(
        UUID id,
        String name,
        BigDecimal cashBalance,
        LocalDateTime createdAt
) {
}

