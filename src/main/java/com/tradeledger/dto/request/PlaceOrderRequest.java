package com.tradeledger.dto.request;

import com.tradeledger.domain.model.OrderSide;
import com.tradeledger.domain.model.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlaceOrderRequest(
        @NotBlank(message = "Symbol is required")
        String symbol,

        @NotNull(message = "Side is required (BUY or SELL)")
        OrderSide side,

        @NotNull(message = "Type is required (MARKET or LIMIT)")
        OrderType type,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
        BigDecimal quantity,

        @DecimalMin(value = "0.0001", message = "Limit price must be greater than zero")
        BigDecimal limitPrice
) {
}
