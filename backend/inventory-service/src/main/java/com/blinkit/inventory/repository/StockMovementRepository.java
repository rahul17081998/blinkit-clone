package com.blinkit.inventory.repository;

import com.blinkit.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StockMovementRepository extends MongoRepository<StockMovement, String> {
    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(String productId, Pageable pageable);
}
