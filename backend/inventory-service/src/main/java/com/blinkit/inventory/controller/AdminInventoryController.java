package com.blinkit.inventory.controller;

import com.blinkit.inventory.dto.request.UpdateStockRequest;
import com.blinkit.common.dto.ApiResponse;
import com.blinkit.common.enums.ApiResponseCode;
import com.blinkit.inventory.dto.response.StockResponse;
import com.blinkit.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/inventory/admin")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventoryService;

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(
                    ApiResponseCode.ACCESS_DENIED.getHttpStatus(),
                    ApiResponseCode.ACCESS_DENIED.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockResponse>>> getAllStock(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PageableDefault(size = 20, sort = "productId") Pageable pageable) {
        requireAdmin(role);
        return ResponseEntity.status(ApiResponseCode.ALL_STOCK_FETCHED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.ALL_STOCK_FETCHED.getMessage(), inventoryService.getAllStock(pageable)));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<StockResponse>> updateStock(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String productId,
            @Valid @RequestBody UpdateStockRequest req) {
        requireAdmin(role);
        return ResponseEntity.status(ApiResponseCode.STOCK_UPDATED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.STOCK_UPDATED.getMessage(), inventoryService.updateStock(productId, req, userId)));
    }
}
