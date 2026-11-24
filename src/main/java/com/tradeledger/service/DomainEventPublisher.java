package com.tradeledger.service;

import com.tradeledger.domain.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public DomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        log.debug("Publishing domain event: type={}, portfolioId={}",
                event.getEventType(), event.getPortfolioId());
        applicationEventPublisher.publishEvent(event);
    }
}

