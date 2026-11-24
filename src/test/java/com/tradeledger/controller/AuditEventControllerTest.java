package com.tradeledger.controller;

import com.tradeledger.dto.response.AuditEventResponse;
import com.tradeledger.service.AuditEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditEventController.class)
class AuditEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditEventService auditEventService;

    private final UUID portfolioId = UUID.randomUUID();

    @Test
    void getAuditEvents_shouldReturnList() throws Exception {
        AuditEventResponse event = new AuditEventResponse(
                "evt-123",
                "ORDER_CREATED",
                portfolioId.toString(),
                Map.of("symbol", "AAPL"),
                Instant.parse("2026-11-24T12:00:00Z")
        );
        when(auditEventService.getAuditEvents(eq(portfolioId), any(), any(), any()))
                .thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/audit-events")
                        .param("type", "ORDER_CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventType", is("ORDER_CREATED")))
                .andExpect(jsonPath("$[0].portfolioId", is(portfolioId.toString())));
    }
}
