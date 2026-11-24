package com.tradeledger.controller;

import com.tradeledger.dto.request.CreatePortfolioRequest;
import com.tradeledger.dto.request.DepositRequest;
import com.tradeledger.dto.response.PortfolioResponse;
import com.tradeledger.dto.response.PositionResponse;
import com.tradeledger.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios")
@Tag(name = "Portfolios", description = "Portfolio and position management")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @PostMapping
    @Operation(summary = "Create a new portfolio")
    public ResponseEntity<PortfolioResponse> createPortfolio(
            @Valid @RequestBody CreatePortfolioRequest request) {
        PortfolioResponse response = portfolioService.createPortfolio(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all portfolios")
    public ResponseEntity<List<PortfolioResponse>> listPortfolios() {
        return ResponseEntity.ok(portfolioService.listPortfolios());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get portfolio by ID")
    public ResponseEntity<PortfolioResponse> getPortfolio(@PathVariable UUID id) {
        return ResponseEntity.ok(portfolioService.getPortfolio(id));
    }

    @PostMapping("/{id}/deposit")
    @Operation(summary = "Deposit cash into portfolio")
    public ResponseEntity<PortfolioResponse> deposit(
            @PathVariable UUID id,
            @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(portfolioService.deposit(id, request));
    }

    @GetMapping("/{id}/positions")
    @Operation(summary = "List positions with market value, unrealized P&L and weight")
    public ResponseEntity<List<PositionResponse>> getPositions(@PathVariable UUID id) {
        return ResponseEntity.ok(portfolioService.getPositions(id));
    }
}

