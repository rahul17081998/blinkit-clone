package com.blinkit.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "categories")
public class Category {

    @Id
    private String id;

    @Indexed(unique = true)
    private String categoryId;

    @Indexed(unique = true)
    private String name;

    @Indexed(unique = true)
    private String slug;

    private String description;
    private String imageUrl;
    private String parentCategoryId;
    private String parentCategoryName;
    private Integer displayOrder;
    private Boolean isActive;
    private String createdBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
