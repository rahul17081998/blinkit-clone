package com.blinkit.order.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.common.enums.ApiResponseCode;
import com.blinkit.order.dto.request.PlaceOrderRequest;
import com.blinkit.order.dto.response.OrderResponse;
import com.blinkit.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody PlaceOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                ApiResponseCode.ORDER_PLACED.getMessage(),
                orderService.placeOrder(userId, req)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getMyOrders(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponseCode.ORDERS_FETCHED.getMessage(),
                orderService.getMyOrders(userId, pageable)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponseCode.ORDER_FETCHED.getMessage(),
                orderService.getOrder(userId, orderId)));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponseCode.ORDER_CANCELLED.getMessage(),
                orderService.cancelOrder(userId, orderId)));
    }
}
