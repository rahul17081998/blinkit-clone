package com.blinkit.order.repository;

import com.blinkit.common.enums.OrderStatus;
import com.blinkit.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {
    Optional<Order> findByOrderId(String orderId);
    Optional<Order> findByOrderIdAndUserId(String orderId, String userId);
    Page<Order> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Order> findByStatus(OrderStatus status);

    boolean existsByUserIdAndStatusAndItemsProductId(String userId, OrderStatus status, String productId);

    /** Count orders whose orderNumber starts with the given date prefix, e.g. "BLK-20260323-" */
    @Query(value = "{ 'orderNumber': { $regex: ?0 } }", count = true)
    long countByOrderNumberStartingWith(String prefix);
}
