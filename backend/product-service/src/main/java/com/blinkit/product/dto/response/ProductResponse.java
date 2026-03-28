package com.blinkit.product.dto.response;

import com.blinkit.product.entity.Product;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ProductResponse {
    private String productId;
    private String name;
    private String slug;
    private String description;
    private String shortDescription;
    private List<String> images;
    private String thumbnailUrl;
    private String categoryId;
    private String categoryName;
    private String categorySlug;
    private String brandId;
    private String brandName;
    private Double mrp;
    private Double sellingPrice;
    private Integer discountPercent;
    private String unit;
    private Double weightInGrams;
    private List<String> tags;
    private String countryOfOrigin;
    private String expiryInfo;
    private String nutritionInfo;
    private Boolean isFeatured;
    private Boolean isAvailable;
    private Boolean isOutOfStock;
    private Double avgRating;
    private Integer reviewCount;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProductResponse from(Product p) {
        return ProductResponse.builder()
                .productId(p.getProductId())
                .name(p.getName())
                .slug(p.getSlug())
                .description(p.getDescription())
                .shortDescription(p.getShortDescription())
                .images(p.getImages())
                .thumbnailUrl(p.getThumbnailUrl())
                .categoryId(p.getCategoryId())
                .categoryName(p.getCategoryName())
                .categorySlug(p.getCategorySlug())
                .brandId(p.getBrandId())
                .brandName(p.getBrandName())
                .mrp(p.getMrp())
                .sellingPrice(p.getSellingPrice())
                .discountPercent(p.getDiscountPercent())
                .unit(p.getUnit())
                .weightInGrams(p.getWeightInGrams())
                .tags(p.getTags())
                .countryOfOrigin(p.getCountryOfOrigin())
                .expiryInfo(p.getExpiryInfo())
                .nutritionInfo(p.getNutritionInfo())
                .isFeatured(p.getIsFeatured())
                .isAvailable(p.getIsAvailable())
                .isOutOfStock(p.getIsOutOfStock())
                .avgRating(p.getAvgRating())
                .reviewCount(p.getReviewCount())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
