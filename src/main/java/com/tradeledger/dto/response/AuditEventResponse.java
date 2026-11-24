package com.tradeledger.dto.response;

import java.time.Instant;
import java.util.Map;

public record AuditEventResponse(
        String id,
        String eventType,
        String portfolioId,
        Map<String, Object> payload,
        Instant timestamp
) {
}
