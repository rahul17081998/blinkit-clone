package com.blinkit.inventory.repository;

import com.blinkit.inventory.entity.Stock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface StockRepository extends MongoRepository<Stock, String> {
    Optional<Stock> findByProductId(String productId);
    boolean existsByProductId(String productId);
}
