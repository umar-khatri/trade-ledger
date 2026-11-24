package com.tradeledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeledger.dto.request.CreatePortfolioRequest;
import com.tradeledger.dto.request.DepositRequest;
import com.tradeledger.dto.response.PortfolioResponse;
import com.tradeledger.dto.response.PositionResponse;
import com.tradeledger.exception.ResourceNotFoundException;
import com.tradeledger.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PortfolioService portfolioService;

    private final UUID portfolioId = UUID.randomUUID();

    @Test
    void createPortfolio_shouldReturn201() throws Exception {
        PortfolioResponse response = new PortfolioResponse(
                portfolioId, "Test Portfolio", BigDecimal.ZERO, LocalDateTime.now());
        when(portfolioService.createPortfolio(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePortfolioRequest("Test Portfolio"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Test Portfolio")));
    }

    @Test
    void createPortfolio_shouldReturn400WhenNameBlank() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePortfolioRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPortfolio_shouldReturn200() throws Exception {
        PortfolioResponse response = new PortfolioResponse(
                portfolioId, "My Portfolio", new BigDecimal("5000.00"), LocalDateTime.now());
        when(portfolioService.getPortfolio(portfolioId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("My Portfolio")))
                .andExpect(jsonPath("$.cashBalance").value(5000.00));
    }

    @Test
    void getPortfolio_shouldReturn404WhenNotFound() throws Exception {
        when(portfolioService.getPortfolio(portfolioId))
                .thenThrow(new ResourceNotFoundException("Portfolio", portfolioId));

        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void listPortfolios_shouldReturnList() throws Exception {
        PortfolioResponse p1 = new PortfolioResponse(
                UUID.randomUUID(), "P1", BigDecimal.ZERO, LocalDateTime.now());
        PortfolioResponse p2 = new PortfolioResponse(
                UUID.randomUUID(), "P2", BigDecimal.ZERO, LocalDateTime.now());
        when(portfolioService.listPortfolios()).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void deposit_shouldReturn200() throws Exception {
        PortfolioResponse response = new PortfolioResponse(
                portfolioId, "My Portfolio", new BigDecimal("5000.00"), LocalDateTime.now());
        when(portfolioService.deposit(eq(portfolioId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DepositRequest(new BigDecimal("5000.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(5000.00));
    }

    @Test
    void deposit_shouldReturn400WhenAmountNegative() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DepositRequest(new BigDecimal("-100.00")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPositions_shouldReturnPositionList() throws Exception {
        PositionResponse pos = new PositionResponse(
                UUID.randomUUID(), portfolioId, "AAPL",
                new BigDecimal("10"), new BigDecimal("150.00"),
                new BigDecimal("160.00"), new BigDecimal("1600.00"),
                new BigDecimal("100.00"), new BigDecimal("0.5"));
        when(portfolioService.getPositions(portfolioId)).thenReturn(List.of(pos));

        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol", is("AAPL")))
                .andExpect(jsonPath("$[0].marketValue").value(1600.00))
                .andExpect(jsonPath("$[0].unrealizedPnL").value(100.00));
    }
}

