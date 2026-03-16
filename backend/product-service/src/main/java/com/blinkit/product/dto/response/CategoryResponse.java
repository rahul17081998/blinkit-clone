package com.blinkit.product.dto.response;

import com.blinkit.product.entity.Category;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class CategoryResponse {
    private String categoryId;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private String parentCategoryId;
    private String parentCategoryName;
    private Integer displayOrder;
    private Boolean isActive;
    private Instant createdAt;
    private List<CategoryResponse> children;

    public static CategoryResponse from(Category c) {
        return CategoryResponse.builder()
                .categoryId(c.getCategoryId())
                .name(c.getName())
                .slug(c.getSlug())
                .description(c.getDescription())
                .imageUrl(c.getImageUrl())
                .parentCategoryId(c.getParentCategoryId())
                .parentCategoryName(c.getParentCategoryName())
                .displayOrder(c.getDisplayOrder())
                .isActive(c.getIsActive())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
