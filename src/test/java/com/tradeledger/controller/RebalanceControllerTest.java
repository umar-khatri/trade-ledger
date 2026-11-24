package com.tradeledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeledger.dto.request.SetTargetAllocationRequest;
import com.tradeledger.dto.response.RebalancePreviewResponse;
import com.tradeledger.dto.response.TargetAllocationResponse;
import com.tradeledger.service.RebalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RebalanceController.class)
class RebalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RebalanceService rebalanceService;

    private final UUID portfolioId = UUID.randomUUID();

    @Test
    void setTargetAllocations_shouldReturn200() throws Exception {
        SetTargetAllocationRequest request = new SetTargetAllocationRequest(List.of(
                new SetTargetAllocationRequest.AllocationItem("AAPL", new BigDecimal("0.600000")),
                new SetTargetAllocationRequest.AllocationItem("GOOGL", new BigDecimal("0.400000"))
        ));

        TargetAllocationResponse r1 = new TargetAllocationResponse(
                UUID.randomUUID(), portfolioId, "AAPL", new BigDecimal("0.600000"));
        TargetAllocationResponse r2 = new TargetAllocationResponse(
                UUID.randomUUID(), portfolioId, "GOOGL", new BigDecimal("0.400000"));

        when(rebalanceService.setTargetAllocations(eq(portfolioId), any())).thenReturn(List.of(r1, r2));

        mockMvc.perform(put("/api/v1/portfolios/" + portfolioId + "/target-allocation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol", is("AAPL")));
    }

    @Test
    void getTargetAllocations_shouldReturn200() throws Exception {
        TargetAllocationResponse r1 = new TargetAllocationResponse(
                UUID.randomUUID(), portfolioId, "AAPL", new BigDecimal("1.000000"));
        when(rebalanceService.getTargetAllocations(portfolioId)).thenReturn(List.of(r1));

        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/target-allocation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol", is("AAPL")));
    }

    @Test
    void previewRebalance_shouldReturn200() throws Exception {
        RebalancePreviewResponse response = new RebalancePreviewResponse(
                portfolioId,
                new BigDecimal("10000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("100.00"),
                new BigDecimal("0.02"),
                new BigDecimal("0.05"),
                true,
                List.of(),
                List.of()
        );
        when(rebalanceService.previewRebalance(portfolioId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/rebalance/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebalanceRequired", is(true)))
                .andExpect(jsonPath("$.driftThreshold").value(0.02));
    }

    @Test
    void executeRebalance_shouldReturn200() throws Exception {
        when(rebalanceService.executeRebalance(portfolioId)).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/rebalance/execute"))
                .andExpect(status().isOk());
    }
}
