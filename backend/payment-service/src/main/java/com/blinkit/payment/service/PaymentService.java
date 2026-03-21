package com.blinkit.payment.service;

import com.blinkit.payment.dto.request.PayRequest;
import com.blinkit.payment.dto.request.TopUpRequest;
import com.blinkit.payment.dto.response.PaymentResponse;
import com.blinkit.payment.dto.response.TransactionResponse;
import com.blinkit.payment.dto.response.WalletResponse;
import com.blinkit.payment.entity.Transaction;
import com.blinkit.payment.entity.Wallet;
import com.blinkit.payment.event.PaymentFailedEvent;
import com.blinkit.payment.event.PaymentSuccessEvent;
import com.blinkit.payment.kafka.PaymentEventPublisher;
import com.blinkit.payment.repository.TransactionRepository;
import com.blinkit.payment.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final double SIGNUP_BONUS = 10000.0;

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final MongoTemplate mongoTemplate;
    private final PaymentEventPublisher eventPublisher;

    // ── Wallet creation on user signup ────────────────────────────────

    public void createWallet(String userId) {
        if (walletRepository.existsByUserId(userId)) {
            log.info("Wallet already exists for userId={} — skipping", userId);
            return;
        }

        String walletId = UUID.randomUUID().toString();
        Wallet wallet = Wallet.builder()
                .walletId(walletId)
                .userId(userId)
                .balance(SIGNUP_BONUS)
                .build();
        walletRepository.save(wallet);

        Transaction tx = Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .walletId(walletId)
                .userId(userId)
                .type("CREDIT")
                .reason("SIGNUP_BONUS")
                .amount(SIGNUP_BONUS)
                .balanceBefore(0.0)
                .balanceAfter(SIGNUP_BONUS)
                .status("SUCCESS")
                .description("Welcome! ₹10,000 added to your Blinkit wallet.")
                .build();
        transactionRepository.save(tx);

        log.info("Wallet created for userId={} with ₹{}", userId, SIGNUP_BONUS);
    }

    // ── Pay (debit wallet) — called by order-service via Feign ───────

    public PaymentResponse pay(PayRequest req) {
        // Atomic debit: find wallet where balance >= amount and decrement
        Query query = new Query(Criteria.where("userId").is(req.getUserId())
                .and("balance").gte(req.getAmount())
                .and("isActive").is(true));
        Update update = new Update().inc("balance", -req.getAmount());
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);

        Wallet walletBefore = walletRepository.findByUserId(req.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found for userId: " + req.getUserId()));

        Wallet updatedWallet = mongoTemplate.findAndModify(query, update, options, Wallet.class);

        if (updatedWallet == null) {
            // Insufficient balance
            double available = walletBefore.getBalance();
            String reason = String.format("Insufficient wallet balance. Available: ₹%.2f, Required: ₹%.2f",
                    available, req.getAmount());

            eventPublisher.publishPaymentFailed(PaymentFailedEvent.builder()
                    .orderId(req.getOrderId())
                    .userId(req.getUserId())
                    .amount(req.getAmount())
                    .reason(reason)
                    .failedAt(Instant.now())
                    .build());

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }

        double balanceAfter = updatedWallet.getBalance();
        double balanceBefore = balanceAfter + req.getAmount();

        String txId = UUID.randomUUID().toString();
        Transaction tx = Transaction.builder()
                .transactionId(txId)
                .walletId(updatedWallet.getWalletId())
                .userId(req.getUserId())
                .orderId(req.getOrderId())
                .type("DEBIT")
                .reason("ORDER_PAYMENT")
                .amount(req.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .status("SUCCESS")
                .description(req.getDescription() != null ? req.getDescription() : "Payment for order")
                .build();
        transactionRepository.save(tx);

        eventPublisher.publishPaymentSuccess(PaymentSuccessEvent.builder()
                .paymentId(txId)
                .orderId(req.getOrderId())
                .userId(req.getUserId())
                .amount(req.getAmount())
                .paidAt(Instant.now())
                .build());

        log.info("Payment SUCCESS: userId={} orderId={} amount={} balanceAfter={}",
                req.getUserId(), req.getOrderId(), req.getAmount(), balanceAfter);

        return PaymentResponse.builder()
                .transactionId(txId)
                .walletBalance(balanceAfter)
                .amount(req.getAmount())
                .status("SUCCESS")
                .build();
    }

    // ── Refund (credit wallet) — called on order cancellation ────────

    public PaymentResponse refund(String orderId) {
        Transaction debitTx = transactionRepository.findByOrderIdAndType(orderId, "DEBIT")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No payment found for orderId: " + orderId));

        String userId = debitTx.getUserId();
        double refundAmount = debitTx.getAmount();

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));

        // Credit wallet
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update().inc("balance", refundAmount);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        Wallet updatedWallet = mongoTemplate.findAndModify(query, update, options, Wallet.class);

        if (updatedWallet == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Refund failed");
        }

        double balanceAfter = updatedWallet.getBalance();

        String txId = UUID.randomUUID().toString();
        Transaction refundTx = Transaction.builder()
                .transactionId(txId)
                .walletId(wallet.getWalletId())
                .userId(userId)
                .orderId(orderId)
                .type("CREDIT")
                .reason("ORDER_REFUND")
                .amount(refundAmount)
                .balanceBefore(balanceAfter - refundAmount)
                .balanceAfter(balanceAfter)
                .status("SUCCESS")
                .description(String.format("Refund of ₹%.2f for order %s", refundAmount, orderId))
                .build();
        transactionRepository.save(refundTx);

        log.info("Refund SUCCESS: userId={} orderId={} amount={} balanceAfter={}",
                userId, orderId, refundAmount, balanceAfter);

        return PaymentResponse.builder()
                .transactionId(txId)
                .walletBalance(balanceAfter)
                .amount(refundAmount)
                .status("SUCCESS")
                .build();
    }

    // ── Get wallet ────────────────────────────────────────────────────

    public WalletResponse getWallet(String userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
        return toWalletResponse(wallet);
    }

    // ── Transaction history ───────────────────────────────────────────

    public Page<TransactionResponse> getHistory(String userId, Pageable pageable) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toTransactionResponse);
    }

    public TransactionResponse getTransaction(String userId, String transactionId) {
        Transaction tx = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (!tx.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return toTransactionResponse(tx);
    }

    // ── Admin: top-up wallet ──────────────────────────────────────────

    public WalletResponse topUp(String userId, TopUpRequest req) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found for userId: " + userId));

        double balanceBefore = wallet.getBalance();

        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update().inc("balance", req.getAmount());
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        Wallet updatedWallet = mongoTemplate.findAndModify(query, update, options, Wallet.class);

        double balanceAfter = updatedWallet.getBalance();

        Transaction tx = Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .walletId(wallet.getWalletId())
                .userId(userId)
                .type("CREDIT")
                .reason("ADMIN_TOPUP")
                .amount(req.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .status("SUCCESS")
                .description(req.getDescription() != null ? req.getDescription() : "Admin top-up")
                .build();
        transactionRepository.save(tx);

        log.info("Top-up: userId={} amount={} newBalance={}", userId, req.getAmount(), balanceAfter);
        return toWalletResponse(updatedWallet);
    }

    // ── Admin: all wallets ────────────────────────────────────────────

    public Page<WalletResponse> getAllWallets(Pageable pageable) {
        return walletRepository.findAll(pageable).map(this::toWalletResponse);
    }

    public WalletResponse getWalletByUserId(String userId) {
        return getWallet(userId);
    }

    // ── Admin: all transactions ───────────────────────────────────────

    public Page<TransactionResponse> getAllTransactions(Pageable pageable) {
        return transactionRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toTransactionResponse);
    }

    // ── Mappers ───────────────────────────────────────────────────────

    private WalletResponse toWalletResponse(Wallet w) {
        return WalletResponse.builder()
                .walletId(w.getWalletId())
                .userId(w.getUserId())
                .balance(w.getBalance())
                .currency(w.getCurrency())
                .isActive(w.isActive())
                .build();
    }

    private TransactionResponse toTransactionResponse(Transaction t) {
        return TransactionResponse.builder()
                .transactionId(t.getTransactionId())
                .walletId(t.getWalletId())
                .userId(t.getUserId())
                .orderId(t.getOrderId())
                .type(t.getType())
                .reason(t.getReason())
                .amount(t.getAmount())
                .balanceBefore(t.getBalanceBefore())
                .balanceAfter(t.getBalanceAfter())
                .status(t.getStatus())
                .description(t.getDescription())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
