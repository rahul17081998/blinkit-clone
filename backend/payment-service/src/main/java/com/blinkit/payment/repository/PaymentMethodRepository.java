package com.blinkit.payment.repository;

import com.blinkit.payment.entity.PaymentMethod;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends MongoRepository<PaymentMethod, String> {
    List<PaymentMethod> findAllByEnabledTrueOrderByDisplayOrderAsc();
    List<PaymentMethod> findAllByOrderByDisplayOrderAsc();
    Optional<PaymentMethod> findByMethodId(String methodId);
    boolean existsByMethodId(String methodId);
}
