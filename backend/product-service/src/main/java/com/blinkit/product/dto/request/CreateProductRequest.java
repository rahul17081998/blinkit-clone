package com.blinkit.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 200, message = "Name must be at most 200 characters")
    private String name;

    @NotBlank(message = "Slug is required")
    private String slug;

    private String description;

    @Size(max = 100, message = "Short description must be at most 100 characters")
    private String shortDescription;

    @NotEmpty(message = "At least one image URL is required")
    private List<String> images;

    @NotBlank(message = "Category ID is required")
    private String categoryId;

    private String brandId;

    @NotNull(message = "MRP is required")
    @DecimalMin(value = "0.01", message = "MRP must be greater than 0")
    private Double mrp;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.01", message = "Selling price must be greater than 0")
    private Double sellingPrice;

    @NotBlank(message = "Unit is required")
    private String unit;

    private Double weightInGrams;
    private List<String> tags;
    private String countryOfOrigin;
    private String expiryInfo;
    private String nutritionInfo;
    private Boolean isFeatured;
}
