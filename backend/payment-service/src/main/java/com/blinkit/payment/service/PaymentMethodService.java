package com.blinkit.payment.service;

import com.blinkit.payment.dto.response.PaymentMethodResponse;
import com.blinkit.payment.dto.response.PaymentMethodsWithWalletResponse;
import com.blinkit.payment.entity.PaymentMethod;
import com.blinkit.payment.repository.PaymentMethodRepository;
import com.blinkit.payment.repository.WalletRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final WalletRepository walletRepository;

    // ── Seed default payment methods on startup ────────────────────────

    @PostConstruct
    public void seedDefaultMethods() {
        if (!paymentMethodRepository.existsByMethodId("WALLET")) {
            paymentMethodRepository.save(PaymentMethod.builder()
                    .methodId("WALLET")
                    .displayName("Wallet")
                    .description("Pay using your Blinkit wallet balance")
                    .iconType("WALLET")
                    .enabled(true)
                    .displayOrder(1)
                    .build());
            log.info("Seeded WALLET payment method");
        }
        if (!paymentMethodRepository.existsByMethodId("RAZORPAY")) {
            paymentMethodRepository.save(PaymentMethod.builder()
                    .methodId("RAZORPAY")
                    .displayName("Razorpay")
                    .description("Pay via UPI, Credit/Debit Card, or Net Banking")
                    .iconType("CARD")
                    .enabled(true)
                    .displayOrder(2)
                    .build());
            log.info("Seeded RAZORPAY payment method");
        }
    }

    // ── Customer: get enabled methods + wallet balance ────────────────

    public PaymentMethodsWithWalletResponse getEnabledMethodsWithWallet(String userId) {
        List<PaymentMethodResponse> methods = paymentMethodRepository
                .findAllByEnabledTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        Double walletBalance = null;
        if (userId != null && !userId.isBlank()) {
            walletBalance = walletRepository.findByUserId(userId)
                    .map(w -> w.getBalance())
                    .orElse(null);
        }

        return PaymentMethodsWithWalletResponse.builder()
                .methods(methods)
                .walletBalance(walletBalance)
                .build();
    }

    // ── Admin: get all methods ─────────────────────────────────────────

    public List<PaymentMethodResponse> getAllMethods() {
        return paymentMethodRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Admin: toggle method ───────────────────────────────────────────

    public PaymentMethodResponse toggleMethod(String methodId) {
        PaymentMethod method = paymentMethodRepository.findByMethodId(methodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Payment method not found: " + methodId));
        method.setEnabled(!method.isEnabled());
        paymentMethodRepository.save(method);
        log.info("Payment method {} toggled to enabled={}", methodId, method.isEnabled());
        return toResponse(method);
    }

    // ── Mapper ────────────────────────────────────────────────────────

    private PaymentMethodResponse toResponse(PaymentMethod m) {
        return PaymentMethodResponse.builder()
                .methodId(m.getMethodId())
                .displayName(m.getDisplayName())
                .description(m.getDescription())
                .iconType(m.getIconType())
                .enabled(m.isEnabled())
                .displayOrder(m.getDisplayOrder())
                .build();
    }
}
