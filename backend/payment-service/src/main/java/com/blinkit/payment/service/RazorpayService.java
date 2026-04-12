package com.blinkit.payment.service;

import com.blinkit.payment.dto.request.RazorpayVerifyRequest;
import com.blinkit.payment.dto.response.RazorpayOrderResponse;
import com.blinkit.payment.entity.Transaction;
import com.blinkit.payment.event.PaymentFailedEvent;
import com.blinkit.payment.event.PaymentSuccessEvent;
import com.blinkit.payment.kafka.PaymentEventPublisher;
import com.blinkit.payment.repository.TransactionRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class RazorpayService {

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    private final TransactionRepository transactionRepository;
    private final PaymentEventPublisher eventPublisher;

    public RazorpayService(TransactionRepository transactionRepository,
                           PaymentEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
    }

    // ── Create Razorpay Order ─────────────────────────────────────────

    public RazorpayOrderResponse createOrder(String internalOrderId, Double amountInRupees) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            long amountInPaise = Math.round(amountInRupees * 100);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", internalOrderId);
            orderRequest.put("payment_capture", 1);

            Order order = client.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");

            log.info("Razorpay order created: razorpayOrderId={} internalOrderId={} amount={}",
                    razorpayOrderId, internalOrderId, amountInRupees);

            return RazorpayOrderResponse.builder()
                    .razorpayOrderId(razorpayOrderId)
                    .keyId(keyId)
                    .amountInPaise(amountInPaise)
                    .currency("INR")
                    .orderId(internalOrderId)
                    .build();

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order for orderId={}: {}", internalOrderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create payment order: " + e.getMessage());
        }
    }

    // ── Verify Razorpay Signature + publish payment.success ──────────

    public void verifyAndPublish(RazorpayVerifyRequest req) {
        // HMAC-SHA256 verification
        String payload = req.getRazorpayOrderId() + "|" + req.getRazorpayPaymentId();
        boolean valid = verifySignature(payload, req.getRazorpaySignature(), keySecret);

        if (!valid) {
            log.warn("Razorpay signature verification FAILED for orderId={}", req.getOrderId());
            eventPublisher.publishPaymentFailed(PaymentFailedEvent.builder()
                    .orderId(req.getOrderId())
                    .userId(req.getUserId())
                    .amount(req.getAmount() != null ? req.getAmount() : 0.0)
                    .reason("Razorpay payment signature verification failed")
                    .failedAt(Instant.now())
                    .build());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment verification failed. Please contact support.");
        }

        // Record transaction
        String txId = UUID.randomUUID().toString();
        Transaction tx = Transaction.builder()
                .transactionId(txId)
                .userId(req.getUserId())
                .orderId(req.getOrderId())
                .type("DEBIT")
                .reason("RAZORPAY_PAYMENT")
                .amount(req.getAmount() != null ? req.getAmount() : 0.0)
                .balanceBefore(0.0)
                .balanceAfter(0.0)
                .status("SUCCESS")
                .description("Razorpay payment. PaymentId: " + req.getRazorpayPaymentId())
                .build();
        transactionRepository.save(tx);

        // Publish payment.success — order-service will confirm the order
        eventPublisher.publishPaymentSuccess(PaymentSuccessEvent.builder()
                .paymentId(txId)
                .orderId(req.getOrderId())
                .userId(req.getUserId())
                .addressId(req.getAddressId())
                .amount(req.getAmount() != null ? req.getAmount() : 0.0)
                .paidAt(Instant.now())
                .build());

        log.info("Razorpay payment verified and payment.success published for orderId={}", req.getOrderId());
    }

    // ── HMAC-SHA256 signature verification ────────────────────────────

    private boolean verifySignature(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            return computed.equals(signature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
