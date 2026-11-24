package com.tradeledger.controller;

import com.tradeledger.dto.response.TradeResponse;
import com.tradeledger.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeController.class)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradeService tradeService;

    private final UUID portfolioId = UUID.randomUUID();

    @Test
    void getTrades_shouldReturnTradeList() throws Exception {
        TradeResponse t1 = new TradeResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "AAPL", new BigDecimal("150.00"), new BigDecimal("10"),
                LocalDateTime.now()
        );
        when(tradeService.getTradesForPortfolio(portfolioId)).thenReturn(List.of(t1));

        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol", is("AAPL")))
                .andExpect(jsonPath("$[0].price").value(150.00));
    }
}
