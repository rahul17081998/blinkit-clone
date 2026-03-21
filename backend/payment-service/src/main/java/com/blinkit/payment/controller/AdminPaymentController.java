package com.blinkit.payment.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.common.enums.ApiResponseCode;
import com.blinkit.payment.dto.request.TopUpRequest;
import com.blinkit.payment.dto.response.TransactionResponse;
import com.blinkit.payment.dto.response.WalletResponse;
import com.blinkit.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/payments/admin")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping("/wallets")
    public ResponseEntity<ApiResponse<Page<WalletResponse>>> getAllWallets(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        PageRequest pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponseCode.WALLETS_FETCHED.getMessage(),
                paymentService.getAllWallets(pageable)));
    }

    @GetMapping("/wallets/{userId}")
    public ResponseEntity<ApiResponse<WalletResponse>> getWalletByUser(
            @RequestHeader("X-User-Role") String role,
            @PathVariable String userId) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponseCode.WALLET_FETCHED.getMessage(),
                paymentService.getWalletByUserId(userId)));
    }

    @PostMapping("/wallets/{userId}/topup")
    public ResponseEntity<ApiResponse<WalletResponse>> topUp(
            @RequestHeader("X-User-Role") String role,
            @PathVariable String userId,
            @Valid @RequestBody TopUpRequest req) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponseCode.WALLET_TOPUP_SUCCESS.getMessage(),
                paymentService.topUp(userId, req)));
    }

    @PostMapping("/wallets/{userId}/seed")
    public ResponseEntity<ApiResponse<Void>> seedWallet(
            @RequestHeader("X-User-Role") String role,
            @PathVariable String userId) {
        requireAdmin(role);
        paymentService.createWallet(userId);
        return ResponseEntity.ok(ApiResponse.ok("Wallet seeded successfully", null));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getAllTransactions(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponseCode.TRANSACTIONS_FETCHED.getMessage(),
                paymentService.getAllTransactions(pageable)));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }
}
