package com.blinkit.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @NotBlank(message = "Slug is required")
    private String slug;

    private String description;
    private String imageUrl;
    private String parentCategoryId;
    private Integer displayOrder;
}
