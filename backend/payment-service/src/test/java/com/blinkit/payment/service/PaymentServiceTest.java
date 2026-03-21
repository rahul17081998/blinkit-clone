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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock WalletRepository walletRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock MongoTemplate mongoTemplate;
    @Mock PaymentEventPublisher eventPublisher;

    @InjectMocks PaymentService paymentService;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = Wallet.builder()
                .id("mongo-id")
                .walletId("wallet-1")
                .userId("user-1")
                .balance(10000.0)
                .currency("INR")
                .isActive(true)
                .build();
    }

    // ── createWallet ───────────────────────────────────────────────────

    @Test
    @DisplayName("createWallet: creates wallet and signup-bonus transaction for new user")
    void createWallet_newUser_createsWalletAndTransaction() {
        when(walletRepository.existsByUserId("user-1")).thenReturn(false);

        paymentService.createWallet("user-1");

        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().getBalance()).isEqualTo(10_000.0);
        assertThat(walletCaptor.getValue().getUserId()).isEqualTo("user-1");

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getReason()).isEqualTo("SIGNUP_BONUS");
        assertThat(txCaptor.getValue().getType()).isEqualTo("CREDIT");
    }

    @Test
    @DisplayName("createWallet: skips wallet creation if wallet already exists")
    void createWallet_existingUser_skipsCreation() {
        when(walletRepository.existsByUserId("user-1")).thenReturn(true);

        paymentService.createWallet("user-1");

        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    // ── pay ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pay: success — debits wallet and publishes payment.success")
    void pay_sufficientBalance_returnsPaymentResponse() {
        PayRequest req = new PayRequest();
        req.setOrderId("order-1");
        req.setUserId("user-1");
        req.setAmount(500.0);

        Wallet updatedWallet = Wallet.builder()
                .walletId("wallet-1").userId("user-1").balance(9500.0).build();

        when(walletRepository.findByUserId("user-1")).thenReturn(Optional.of(wallet));
        doReturn(updatedWallet).when(mongoTemplate)
                .findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Wallet.class));

        PaymentResponse response = paymentService.pay(req);

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getAmount()).isEqualTo(500.0);
        assertThat(response.getWalletBalance()).isEqualTo(9500.0);

        verify(transactionRepository).save(any(Transaction.class));
        verify(eventPublisher).publishPaymentSuccess(any(PaymentSuccessEvent.class));
        verify(eventPublisher, never()).publishPaymentFailed(any());
    }

    @Test
    @DisplayName("pay: wallet not found — throws 404")
    void pay_walletNotFound_throws404() {
        PayRequest req = new PayRequest();
        req.setOrderId("order-1");
        req.setUserId("user-x");
        req.setAmount(100.0);

        when(walletRepository.findByUserId("user-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.pay(req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(mongoTemplate, never()).findAndModify(any(), any(), any(), any(Class.class));
    }

    @Test
    @DisplayName("pay: insufficient balance — publishes payment.failed and throws 400")
    void pay_insufficientBalance_publishesFailedAndThrows400() {
        PayRequest req = new PayRequest();
        req.setOrderId("order-1");
        req.setUserId("user-1");
        req.setAmount(20000.0); // more than wallet balance

        when(walletRepository.findByUserId("user-1")).thenReturn(Optional.of(wallet));
        doReturn(null).when(mongoTemplate)
                .findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Wallet.class));

        assertThatThrownBy(() -> paymentService.pay(req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(eventPublisher).publishPaymentFailed(any(PaymentFailedEvent.class));
        verify(transactionRepository, never()).save(any());
    }

    // ── refund ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("refund: success — credits wallet and saves refund transaction")
    void refund_success_creditsWalletAndSavesTransaction() {
        Transaction debitTx = Transaction.builder()
                .transactionId("tx-1").walletId("wallet-1")
                .userId("user-1").orderId("order-1")
                .type("DEBIT").amount(500.0)
                .balanceBefore(10000.0).balanceAfter(9500.0)
                .build();

        Wallet updatedWallet = Wallet.builder()
                .walletId("wallet-1").userId("user-1").balance(10000.0).build();

        when(transactionRepository.findByOrderIdAndType("order-1", "DEBIT"))
                .thenReturn(Optional.of(debitTx));
        when(walletRepository.findByUserId("user-1")).thenReturn(Optional.of(wallet));
        doReturn(updatedWallet).when(mongoTemplate)
                .findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Wallet.class));

        PaymentResponse response = paymentService.refund("order-1");

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getAmount()).isEqualTo(500.0);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getReason()).isEqualTo("ORDER_REFUND");
        assertThat(txCaptor.getValue().getType()).isEqualTo("CREDIT");
    }

    @Test
    @DisplayName("refund: no DEBIT transaction found — throws 404")
    void refund_noDebitTransaction_throws404() {
        when(transactionRepository.findByOrderIdAndType("order-x", "DEBIT"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refund("order-x"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── getWallet ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getWallet: returns wallet response for existing user")
    void getWallet_found_returnsResponse() {
        when(walletRepository.findByUserId("user-1")).thenReturn(Optional.of(wallet));

        WalletResponse response = paymentService.getWallet("user-1");

        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getBalance()).isEqualTo(10000.0);
        assertThat(response.getCurrency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("getWallet: throws 404 for unknown user")
    void getWallet_notFound_throws404() {
        when(walletRepository.findByUserId("user-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getWallet("user-x"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── topUp ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("topUp: credits wallet and records ADMIN_TOPUP transaction")
    void topUp_success_creditsAndSavesTransaction() {
        TopUpRequest req = new TopUpRequest();
        req.setAmount(500.0);
        req.setDescription("Admin top-up");

        Wallet updatedWallet = Wallet.builder()
                .walletId("wallet-1").userId("user-1").balance(10500.0).currency("INR").build();

        when(walletRepository.findByUserId("user-1")).thenReturn(Optional.of(wallet));
        doReturn(updatedWallet).when(mongoTemplate)
                .findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Wallet.class));

        WalletResponse response = paymentService.topUp("user-1", req);

        assertThat(response.getBalance()).isEqualTo(10500.0);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getReason()).isEqualTo("ADMIN_TOPUP");
        assertThat(txCaptor.getValue().getType()).isEqualTo("CREDIT");
        assertThat(txCaptor.getValue().getAmount()).isEqualTo(500.0);
    }

    // ── getHistory ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getHistory: returns paginated transaction history for user")
    void getHistory_returnsPaginatedTransactions() {
        Transaction tx = Transaction.builder()
                .transactionId("tx-1").userId("user-1")
                .type("CREDIT").reason("SIGNUP_BONUS").amount(10000.0)
                .balanceBefore(0.0).balanceAfter(10000.0).status("SUCCESS")
                .build();
        PageRequest pageable = PageRequest.of(0, 10);
        when(transactionRepository.findByUserIdOrderByCreatedAtDesc("user-1", pageable))
                .thenReturn(new PageImpl<>(List.of(tx)));

        Page<TransactionResponse> result = paymentService.getHistory("user-1", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTransactionId()).isEqualTo("tx-1");
        assertThat(result.getContent().get(0).getType()).isEqualTo("CREDIT");
    }

    @Test
    @DisplayName("getTransaction: throws 403 when userId doesn't own the transaction")
    void getTransaction_wrongUser_throws403() {
        Transaction tx = Transaction.builder()
                .transactionId("tx-1").userId("user-2").build();

        when(transactionRepository.findByTransactionId("tx-1")).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> paymentService.getTransaction("user-1", "tx-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
