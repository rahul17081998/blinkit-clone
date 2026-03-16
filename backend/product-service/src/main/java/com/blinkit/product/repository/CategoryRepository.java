package com.blinkit.product.repository;

import com.blinkit.product.entity.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends MongoRepository<Category, String> {
    Optional<Category> findByCategoryId(String categoryId);
    Optional<Category> findBySlug(String slug);
    boolean existsByName(String name);
    boolean existsBySlug(String slug);
    List<Category> findByIsActiveTrueOrderByDisplayOrderAsc();
    List<Category> findByParentCategoryIdAndIsActiveTrue(String parentCategoryId);
}
