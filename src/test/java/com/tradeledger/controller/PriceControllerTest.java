package com.tradeledger.controller;

import com.tradeledger.dto.response.PriceResponse;
import com.tradeledger.service.PriceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PriceController.class)
class PriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PriceService priceService;

    @Test
    void getPrice_shouldReturnPriceResponse() throws Exception {
        PriceResponse response = new PriceResponse(
                "AAPL",
                new BigDecimal("185.5000"),
                Instant.parse("2026-11-24T12:00:00Z")
        );
        when(priceService.getPriceResponse("AAPL")).thenReturn(response);

        mockMvc.perform(get("/api/v1/prices/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol", is("AAPL")))
                .andExpect(jsonPath("$.price").value(185.5000));
    }
}

