package com.blinkit.inventory.controller;

import com.blinkit.inventory.dto.request.UpdateStockRequest;
import com.blinkit.inventory.dto.response.ApiResponse;
import com.blinkit.inventory.dto.response.StockResponse;
import com.blinkit.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/inventory/admin")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventoryService;

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockResponse>>> getAllStock(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok("Stock report", inventoryService.getAllStock()));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<StockResponse>> updateStock(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String productId,
            @Valid @RequestBody UpdateStockRequest req) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok("Stock updated", inventoryService.updateStock(productId, req, userId)));
    }
}
