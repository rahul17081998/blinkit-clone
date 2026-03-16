package com.blinkit.inventory.controller;

import com.blinkit.inventory.dto.request.ConfirmStockRequest;
import com.blinkit.inventory.dto.request.ReleaseStockRequest;
import com.blinkit.inventory.dto.request.ReserveStockRequest;
import com.blinkit.common.dto.ApiResponse;
import com.blinkit.common.enums.ApiResponseCode;
import com.blinkit.inventory.dto.response.StockResponse;
import com.blinkit.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<StockResponse>> getStock(@PathVariable String productId) {
        return ResponseEntity.status(ApiResponseCode.STOCK_FETCHED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.STOCK_FETCHED.getMessage(), inventoryService.getStock(productId)));
    }

    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<StockResponse>> reserve(@Valid @RequestBody ReserveStockRequest req) {
        return ResponseEntity.status(ApiResponseCode.STOCK_RESERVED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.STOCK_RESERVED.getMessage(), inventoryService.reserveStock(req)));
    }

    @PostMapping("/release")
    public ResponseEntity<ApiResponse<StockResponse>> release(@Valid @RequestBody ReleaseStockRequest req) {
        return ResponseEntity.status(ApiResponseCode.STOCK_RELEASED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.STOCK_RELEASED.getMessage(), inventoryService.releaseStock(req)));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<StockResponse>> confirm(@Valid @RequestBody ConfirmStockRequest req) {
        return ResponseEntity.status(ApiResponseCode.STOCK_CONFIRMED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.STOCK_CONFIRMED.getMessage(), inventoryService.confirmStock(req)));
    }
}
