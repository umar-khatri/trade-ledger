package com.tradeledger.listener;

import com.tradeledger.domain.event.CashDepositedEvent;
import com.tradeledger.domain.model.AuditEventDocument;
import com.tradeledger.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DomainEventListenerTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    private DomainEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new DomainEventListener(auditEventRepository);
    }

    @Test
    void onDomainEvent_shouldSaveAuditEventDocument() {
        UUID portfolioId = UUID.randomUUID();
        CashDepositedEvent event = new CashDepositedEvent(
                portfolioId, new BigDecimal("1000.00"), new BigDecimal("5000.00"));

        listener.onDomainEvent(event);

        ArgumentCaptor<AuditEventDocument> captor = ArgumentCaptor.forClass(AuditEventDocument.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEventDocument saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("CASH_DEPOSITED");
        assertThat(saved.getPortfolioId()).isEqualTo(portfolioId.toString());
        assertThat(saved.getPayload()).containsEntry("amount", "1000.00");
        assertThat(saved.getPayload()).containsEntry("newBalance", "5000.00");
    }

    @Test
    void onDomainEvent_whenMongoFails_shouldNotThrow() {
        doThrow(new RuntimeException("Mongo connection refused"))
                .when(auditEventRepository).save(any());

        CashDepositedEvent event = new CashDepositedEvent(
                UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("100.00"));

        // Must not throw uncaught exception
        listener.onDomainEvent(event);
    }
}

