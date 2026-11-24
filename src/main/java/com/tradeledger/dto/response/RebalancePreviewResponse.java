package com.tradeledger.dto.response;

import com.tradeledger.domain.model.OrderSide;
import com.tradeledger.domain.model.OrderType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RebalancePreviewResponse(
        UUID portfolioId,
        BigDecimal totalPortfolioValue,
        BigDecimal currentCash,
        BigDecimal minCashBuffer,
        BigDecimal driftThreshold,
        BigDecimal maxDrift,
        boolean rebalanceRequired,
        List<AssetDrift> assetDrifts,
        List<ProposedOrder> proposedOrders
) {
    public record AssetDrift(
            String symbol,
            BigDecimal targetWeight,
            BigDecimal currentWeight,
            BigDecimal drift,
            BigDecimal currentQuantity,
            BigDecimal currentPrice,
            BigDecimal currentMarketValue
    ) {
    }

    public record ProposedOrder(
            String symbol,
            OrderSide side,
            OrderType type,
            BigDecimal quantity,
            BigDecimal estimatedPrice,
            BigDecimal estimatedAmount,
            String reason
    ) {
    }
}

