package com.tradeledger.domain.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all domain events. Each subclass carries event-specific
 * payload and is published via Spring's ApplicationEventPublisher, then
 * persisted asynchronously to MongoDB by DomainEventListener.
 */
public abstract class DomainEvent {

    private final String eventType;
    private final UUID portfolioId;
    private final Instant timestamp;

    protected DomainEvent(String eventType, UUID portfolioId) {
        this.eventType = eventType;
        this.portfolioId = portfolioId;
        this.timestamp = Instant.now();
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getPortfolioId() {
        return portfolioId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Returns event-specific data as a map, stored as the JSON payload
     * in the MongoDB audit_events collection.
     */
    public abstract Map<String, Object> toPayload();
}
