package com.tradeledger.service;

import com.tradeledger.domain.model.AuditEventDocument;
import com.tradeledger.domain.model.Portfolio;
import com.tradeledger.dto.response.AuditEventResponse;
import com.tradeledger.repository.AuditEventRepository;
import com.tradeledger.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private AuditEventService auditEventService;

    private UUID portfolioId;

    @BeforeEach
    void setUp() {
        auditEventService = new AuditEventService(
                auditEventRepository, portfolioRepository, mongoTemplate);
        portfolioId = UUID.randomUUID();
    }

    @Test
    void getAuditEvents_shouldReturnFilteredEvents() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(new Portfolio("Test")));

        AuditEventDocument doc = new AuditEventDocument(
                "ORDER_CREATED",
                portfolioId.toString(),
                Map.of("symbol", "AAPL"),
                Instant.now()
        );
        when(mongoTemplate.find(any(Query.class), eq(AuditEventDocument.class)))
                .thenReturn(List.of(doc));

        List<AuditEventResponse> responses = auditEventService.getAuditEvents(
                portfolioId, "ORDER_CREATED", Instant.now().minusSeconds(3600), Instant.now());

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().eventType()).isEqualTo("ORDER_CREATED");
        assertThat(responses.getFirst().portfolioId()).isEqualTo(portfolioId.toString());
        assertThat(responses.getFirst().payload()).containsEntry("symbol", "AAPL");
    }
}
