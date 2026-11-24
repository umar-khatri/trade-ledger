package com.tradeledger.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record SetTargetAllocationRequest(
        @NotEmpty(message = "Allocations list cannot be empty")
        @Valid
        List<AllocationItem> allocations
) {
    public record AllocationItem(
            @NotBlank(message = "Symbol is required")
            String symbol,

            @NotNull(message = "Target weight is required")
            @DecimalMin(value = "0.000001", message = "Target weight must be greater than zero")
            @DecimalMax(value = "1.000000", message = "Target weight cannot exceed 1.0")
            BigDecimal targetWeight
    ) {
    }
}

