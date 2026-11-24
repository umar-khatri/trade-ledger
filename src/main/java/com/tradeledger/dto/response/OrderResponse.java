package com.tradeledger.dto.response;

import com.tradeledger.domain.model.OrderSide;
import com.tradeledger.domain.model.OrderStatus;
import com.tradeledger.domain.model.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID portfolioId,
        String symbol,
        OrderSide side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal filledQuantity,
        BigDecimal remainingQuantity,
        OrderStatus status,
        String idempotencyKey,
        LocalDateTime createdAt
) {
}

