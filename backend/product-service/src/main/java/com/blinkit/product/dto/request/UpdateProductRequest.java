package com.blinkit.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateProductRequest {

    @Size(max = 200, message = "Name must be at most 200 characters")
    private String name;

    private String description;

    @Size(max = 100, message = "Short description must be at most 100 characters")
    private String shortDescription;

    private List<String> images;
    private String categoryId;
    private String brandId;

    @DecimalMin(value = "0.01", message = "MRP must be greater than 0")
    private Double mrp;

    @DecimalMin(value = "0.01", message = "Selling price must be greater than 0")
    private Double sellingPrice;

    private String unit;
    private Double weightInGrams;
    private List<String> tags;
    private String countryOfOrigin;
    private String expiryInfo;
    private String nutritionInfo;
    private Boolean isFeatured;
}
