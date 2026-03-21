package com.blinkit.order.repository;

import com.blinkit.common.enums.OrderStatus;
import com.blinkit.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {
    Optional<Order> findByOrderId(String orderId);
    Optional<Order> findByOrderIdAndUserId(String orderId, String userId);
    Page<Order> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Order> findByStatus(OrderStatus status);
}
