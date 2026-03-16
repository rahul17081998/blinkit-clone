package com.blinkit.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
@CompoundIndex(name = "category_price_idx", def = "{'categorySlug': 1, 'sellingPrice': 1}")
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String productId;

    @Indexed(unique = true)
    private String slug;

    @TextIndexed
    private String name;

    private String description;
    private String shortDescription;
    private List<String> images;
    private String thumbnailUrl;

    private String categoryId;
    private String categoryName;

    @Indexed
    private String categorySlug;

    private String brandId;
    private String brandName;

    private Double mrp;
    private Double sellingPrice;
    private Integer discountPercent;
    private String unit;
    private Double weightInGrams;

    @TextIndexed
    private List<String> tags;

    private String countryOfOrigin;
    private String expiryInfo;
    private String nutritionInfo;

    private Boolean isFeatured;
    private Boolean isAvailable;
    private Double avgRating;
    private Integer reviewCount;
    private String createdBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
