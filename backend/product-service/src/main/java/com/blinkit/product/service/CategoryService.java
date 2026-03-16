package com.blinkit.product.service;

import com.blinkit.product.dto.request.CreateCategoryRequest;
import com.blinkit.product.dto.response.CategoryResponse;
import com.blinkit.product.entity.Category;
import com.blinkit.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryResponse createCategory(CreateCategoryRequest req, String adminUserId) {
        if (categoryRepository.existsByName(req.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category with this name already exists");
        }
        if (categoryRepository.existsBySlug(req.getSlug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category with this slug already exists");
        }

        String parentName = null;
        if (req.getParentCategoryId() != null) {
            Category parent = categoryRepository.findByCategoryId(req.getParentCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent category not found"));
            parentName = parent.getName();
        }

        Category category = Category.builder()
                .categoryId(UUID.randomUUID().toString())
                .name(req.getName())
                .slug(req.getSlug())
                .description(req.getDescription())
                .imageUrl(req.getImageUrl())
                .parentCategoryId(req.getParentCategoryId())
                .parentCategoryName(parentName)
                .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 0)
                .isActive(true)
                .createdBy(adminUserId)
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Created category: {} (id={})", saved.getName(), saved.getCategoryId());
        return CategoryResponse.from(saved);
    }

    public List<CategoryResponse> getCategoryTree() {
        List<Category> all = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        Map<String, CategoryResponse> responseMap = all.stream()
                .collect(Collectors.toMap(Category::getCategoryId, CategoryResponse::from));

        List<CategoryResponse> roots = new ArrayList<>();
        for (Category cat : all) {
            CategoryResponse response = responseMap.get(cat.getCategoryId());
            if (cat.getParentCategoryId() == null) {
                response.setChildren(new ArrayList<>());
                roots.add(response);
            } else {
                CategoryResponse parent = responseMap.get(cat.getParentCategoryId());
                if (parent != null) {
                    if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                    parent.getChildren().add(response);
                }
            }
        }
        return roots;
    }

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(CategoryResponse::from)
                .collect(Collectors.toList());
    }

    public CategoryResponse getCategoryById(String categoryId) {
        Category category = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        return CategoryResponse.from(category);
    }
}
