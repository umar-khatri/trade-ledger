package com.tradeledger.controller;

import com.tradeledger.dto.response.TradeResponse;
import com.tradeledger.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/trades")
@Tag(name = "Trades", description = "Trade execution history and settlement records")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @GetMapping
    @Operation(summary = "List trade executions for a portfolio",
            description = "Retrieves all trades executed for buyer or seller orders belonging to the specified portfolio, ordered by execution time descending.")
    public ResponseEntity<List<TradeResponse>> getTrades(@PathVariable UUID portfolioId) {
        return ResponseEntity.ok(tradeService.getTradesForPortfolio(portfolioId));
    }
}
