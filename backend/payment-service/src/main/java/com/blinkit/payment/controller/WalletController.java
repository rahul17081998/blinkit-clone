package com.blinkit.payment.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.common.enums.ApiResponseCode;
import com.blinkit.payment.dto.response.TransactionResponse;
import com.blinkit.payment.dto.response.WalletResponse;
import com.blinkit.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class WalletController {

    private final PaymentService paymentService;

    @GetMapping("/wallet")
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponseCode.WALLET_FETCHED.getMessage(),
                paymentService.getWallet(userId)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getHistory(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponseCode.TRANSACTION_HISTORY_FETCHED.getMessage(),
                paymentService.getHistory(userId, pageable)));
    }

    @GetMapping("/history/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String transactionId) {
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponseCode.TRANSACTION_FETCHED.getMessage(),
                paymentService.getTransaction(userId, transactionId)));
    }
}
