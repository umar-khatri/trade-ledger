package com.tradeledger.controller;

import com.tradeledger.domain.model.OrderStatus;
import com.tradeledger.dto.request.PlaceOrderRequest;
import com.tradeledger.dto.response.OrderResponse;
import com.tradeledger.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Orders", description = "Order placement, matching, cancellation, and retrieval")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/v1/portfolios/{portfolioId}/orders")
    @Operation(summary = "Place a new order",
            description = "Submits a LIMIT or MARKET order. Matches against the in-memory order book with price-time priority. Supports duplicate prevention via Idempotency-Key header.")
    public ResponseEntity<OrderResponse> placeOrder(
            @PathVariable UUID portfolioId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Parameter(description = "Optional unique idempotency token to prevent double-booking on retries")
            String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse response = orderService.placeOrder(portfolioId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/orders")
    @Operation(summary = "List orders for a portfolio",
            description = "Retrieves orders belonging to the specified portfolio, with optional status filter.")
    public ResponseEntity<List<OrderResponse>> getOrders(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) OrderStatus status) {
        List<OrderResponse> orders = orderService.getOrders(portfolioId, status);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/api/v1/orders/{orderId}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @DeleteMapping("/api/v1/orders/{orderId}")
    @Operation(summary = "Cancel an active resting order",
            description = "Removes the order from the in-memory order book and transitions its status to CANCELLED.")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId));
    }
}

