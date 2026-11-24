package com.tradeledger.listener;

import com.tradeledger.domain.event.DomainEvent;
import com.tradeledger.domain.model.AuditEventDocument;
import com.tradeledger.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Asynchronously persists all emitted domain events into MongoDB
 * for compliance, auditing, and immutable event replay.
 */
@Component
public class DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

    private final AuditEventRepository auditEventRepository;

    public DomainEventListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Async("eventExecutor")
    @EventListener
    public void onDomainEvent(DomainEvent event) {
        try {
            AuditEventDocument document = new AuditEventDocument(
                    event.getEventType(),
                    event.getPortfolioId() != null ? event.getPortfolioId().toString() : null,
                    event.toPayload(),
                    event.getTimestamp()
            );

            auditEventRepository.save(document);
            log.debug("Persisted audit event [{}] for portfolio [{}]",
                    event.getEventType(), event.getPortfolioId());
        } catch (Exception ex) {
            log.error("Failed to persist audit event [{}]: {}. Fallback log: payload={}",
                    event.getEventType(), ex.getMessage(), event.toPayload(), ex);
        }
    }
}
