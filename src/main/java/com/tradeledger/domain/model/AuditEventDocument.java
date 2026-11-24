package com.tradeledger.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "audit_events")
@CompoundIndex(name = "portfolio_type_time_idx",
        def = "{'portfolioId': 1, 'eventType': 1, 'timestamp': -1}")
public class AuditEventDocument {

    @Id
    private String id;

    private String eventType;

    private String portfolioId;

    private Map<String, Object> payload;

    private Instant timestamp;

    public AuditEventDocument() {
    }

    public AuditEventDocument(String eventType, String portfolioId,
                              Map<String, Object> payload, Instant timestamp) {
        this.eventType = eventType;
        this.portfolioId = portfolioId;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    // ---- Getters & Setters ----

    public String getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(String portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

