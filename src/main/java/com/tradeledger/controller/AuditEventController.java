package com.tradeledger.controller;

import com.tradeledger.dto.response.AuditEventResponse;
import com.tradeledger.service.AuditEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/portfolios/{portfolioId}/audit-events", "/portfolios/{portfolioId}/audit-events"})
@Tag(name = "Audit Events", description = "Query immutable event trails stored in MongoDB")
public class AuditEventController {

    private final AuditEventService auditEventService;

    public AuditEventController(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @GetMapping
    @Operation(summary = "Query audit and domain events for a portfolio",
            description = "Retrieves immutable events (ORDER_CREATED, ORDER_FILLED, ORDER_CANCELLED, REBALANCE_EXECUTED, CASH_DEPOSITED) filtered by event type and ISO-8601 date range.")
    public ResponseEntity<List<AuditEventResponse>> getAuditEvents(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false)
            @Parameter(description = "Filter by event type e.g. ORDER_CREATED, ORDER_FILLED")
            String type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "Start timestamp (ISO-8601 format)")
            Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "End timestamp (ISO-8601 format)")
            Instant to) {
        List<AuditEventResponse> events = auditEventService.getAuditEvents(portfolioId, type, from, to);
        return ResponseEntity.ok(events);
    }
}

