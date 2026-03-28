package com.blinkit.product.repository;

import com.blinkit.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {
    Optional<Product> findByProductId(String productId);
    Optional<Product> findBySlug(String slug);
    boolean existsBySlug(String slug);
    // Customer listings — include all products (available + unavailable shown faded)
    Page<Product> findByCategorySlug(String categorySlug, Pageable pageable);
    Page<Product> findAll(Pageable pageable);
    Page<Product> findByIsAvailableTrue(Pageable pageable);
    Page<Product> findByCategorySlugAndIsAvailableTrue(String categorySlug, Pageable pageable);
    // Full list variants for shuffle-then-paginate
    List<Product> findAllByOrderByCreatedAtDesc();
    List<Product> findByCategorySlug(String categorySlug);
    List<Product> findByIsFeaturedTrueAndIsAvailableTrue();

    @Query("{ '$text': { '$search': ?0 }, 'isAvailable': true }")
    Page<Product> searchByText(String query, Pageable pageable);

    // Search includes all products — unavailable shown faded to customer
    @Query("{ 'name': { '$regex': ?0, '$options': 'i' } }")
    Page<Product> searchByNameRegex(String pattern, Pageable pageable);
}
