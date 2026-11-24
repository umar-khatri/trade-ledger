package com.tradeledger.controller;

import com.tradeledger.dto.request.SetTargetAllocationRequest;
import com.tradeledger.dto.response.OrderResponse;
import com.tradeledger.dto.response.RebalancePreviewResponse;
import com.tradeledger.dto.response.TargetAllocationResponse;
import com.tradeledger.service.RebalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}")
@Tag(name = "Rebalancing", description = "Target allocation configuration and automated portfolio rebalancing engine")
public class RebalanceController {

    private final RebalanceService rebalanceService;

    public RebalanceController(RebalanceService rebalanceService) {
        this.rebalanceService = rebalanceService;
    }

    @PutMapping("/target-allocation")
    @Operation(summary = "Set target allocation weights",
            description = "Configures target weights per symbol for the portfolio. Sum of weights must equal 1.0.")
    public ResponseEntity<List<TargetAllocationResponse>> setTargetAllocations(
            @PathVariable UUID portfolioId,
            @Valid @RequestBody SetTargetAllocationRequest request) {
        return ResponseEntity.ok(rebalanceService.setTargetAllocations(portfolioId, request));
    }

    @GetMapping("/target-allocation")
    @Operation(summary = "Get target allocation weights for portfolio")
    public ResponseEntity<List<TargetAllocationResponse>> getTargetAllocations(
            @PathVariable UUID portfolioId) {
        return ResponseEntity.ok(rebalanceService.getTargetAllocations(portfolioId));
    }

    @PostMapping("/rebalance/preview")
    @Operation(summary = "Preview rebalancing proposed orders (no side effects)",
            description = "Calculates asset drift against target allocations. Returns proposed BUY and SELL orders with whole-share rounding and minimum cash buffer preservation.")
    public ResponseEntity<RebalancePreviewResponse> previewRebalance(
            @PathVariable UUID portfolioId) {
        return ResponseEntity.ok(rebalanceService.previewRebalance(portfolioId));
    }

    @PostMapping("/rebalance/execute")
    @Operation(summary = "Execute portfolio rebalance",
            description = "Submits the generated rebalancing orders through the order matching engine, executing sells first to unlock liquidity before buys.")
    public ResponseEntity<List<OrderResponse>> executeRebalance(
            @PathVariable UUID portfolioId) {
        return ResponseEntity.ok(rebalanceService.executeRebalance(portfolioId));
    }
}
