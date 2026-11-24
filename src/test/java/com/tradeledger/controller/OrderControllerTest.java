package com.tradeledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeledger.domain.model.OrderSide;
import com.tradeledger.domain.model.OrderStatus;
import com.tradeledger.domain.model.OrderType;
import com.tradeledger.dto.request.PlaceOrderRequest;
import com.tradeledger.dto.response.OrderResponse;
import com.tradeledger.service.OrderService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private final UUID portfolioId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @Test
    void placeOrder_shouldReturn201() throws Exception {
        OrderResponse response = new OrderResponse(
                orderId, portfolioId, "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("10"), new BigDecimal("150.00"),
                BigDecimal.ZERO, new BigDecimal("10"),
                OrderStatus.NEW, "key-123", LocalDateTime.now()
        );
        when(orderService.placeOrder(eq(portfolioId), any(), eq("key-123"))).thenReturn(response);

        PlaceOrderRequest request = new PlaceOrderRequest(
                "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("10"), new BigDecimal("150.00")
        );

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/orders")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol", is("AAPL")))
                .andExpect(jsonPath("$.status", is("NEW")))
                .andExpect(jsonPath("$.idempotencyKey", is("key-123")));
    }

    @Test
    void getOrders_shouldReturnOrderList() throws Exception {
        OrderResponse o1 = new OrderResponse(
                orderId, portfolioId, "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("10"), new BigDecimal("150.00"),
                BigDecimal.ZERO, new BigDecimal("10"),
                OrderStatus.NEW, null, LocalDateTime.now()
        );
        when(orderService.getOrders(eq(portfolioId), any())).thenReturn(List.of(o1));

        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol", is("AAPL")));
    }

    @Test
    void cancelOrder_shouldReturn200() throws Exception {
        OrderResponse response = new OrderResponse(
                orderId, portfolioId, "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("10"), new BigDecimal("150.00"),
                BigDecimal.ZERO, new BigDecimal("10"),
                OrderStatus.CANCELLED, null, LocalDateTime.now()
        );
        when(orderService.cancelOrder(orderId)).thenReturn(response);

        mockMvc.perform(delete("/api/v1/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }
}

