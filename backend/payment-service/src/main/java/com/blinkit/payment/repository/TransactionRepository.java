package com.blinkit.payment.repository;

import com.blinkit.payment.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends MongoRepository<Transaction, String> {
    Page<Transaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Optional<Transaction> findByTransactionId(String transactionId);
    Optional<Transaction> findByOrderIdAndType(String orderId, String type);
    List<Transaction> findByOrderId(String orderId);
    Page<Transaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
