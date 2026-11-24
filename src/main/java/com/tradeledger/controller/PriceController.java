package com.tradeledger.controller;

import com.tradeledger.dto.response.PriceResponse;
import com.tradeledger.service.PriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prices")
@Tag(name = "Prices", description = "Market data and real-time price queries")
public class PriceController {

    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "Get latest market price for a symbol",
            description = "Reads through the Redis cache. On cache-miss, loads from the PriceProvider and caches with TTL.")
    public ResponseEntity<PriceResponse> getPrice(@PathVariable String symbol) {
        PriceResponse response = priceService.getPriceResponse(symbol);
        return ResponseEntity.ok(response);
    }
}
