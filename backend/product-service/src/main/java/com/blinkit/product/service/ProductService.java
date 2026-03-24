package com.blinkit.product.service;

import com.blinkit.product.dto.request.CreateProductRequest;
import com.blinkit.product.dto.request.UpdateProductRequest;
import com.blinkit.product.dto.response.ProductResponse;
import com.blinkit.product.entity.Category;
import com.blinkit.product.entity.Product;
import com.blinkit.product.event.ProductCreatedEvent;
import com.blinkit.product.kafka.ProductEventPublisher;
import com.blinkit.product.repository.CategoryRepository;
import com.blinkit.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductEventPublisher eventPublisher;
    private final CloudinaryService cloudinaryService;

    public ProductResponse createProduct(CreateProductRequest req, String adminUserId) {
        if (productRepository.existsBySlug(req.getSlug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product with this slug already exists");
        }

        Category category = categoryRepository.findByCategoryId(req.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        String brandName = null;
        // Brand lookup would go here when brand-service exists; for now just use brandId as name if provided
        if (req.getBrandId() != null) {
            brandName = req.getBrandId(); // placeholder until brand entity is added
        }

        double mrp = req.getMrp();
        double sellingPrice = req.getSellingPrice();
        if (sellingPrice > mrp) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selling price cannot exceed MRP");
        }
        int discountPercent = (int) Math.floor(((mrp - sellingPrice) / mrp) * 100);

        String productId = UUID.randomUUID().toString();
        Product product = Product.builder()
                .productId(productId)
                .name(req.getName())
                .slug(req.getSlug())
                .description(req.getDescription())
                .shortDescription(req.getShortDescription())
                .images(req.getImages())
                .thumbnailUrl(req.getImages().get(0))
                .categoryId(category.getCategoryId())
                .categoryName(category.getName())
                .categorySlug(category.getSlug())
                .brandId(req.getBrandId())
                .brandName(brandName)
                .mrp(mrp)
                .sellingPrice(sellingPrice)
                .discountPercent(discountPercent)
                .unit(req.getUnit())
                .weightInGrams(req.getWeightInGrams())
                .tags(req.getTags() != null ? req.getTags() : new ArrayList<>())
                .countryOfOrigin(req.getCountryOfOrigin())
                .expiryInfo(req.getExpiryInfo())
                .nutritionInfo(req.getNutritionInfo())
                .isFeatured(req.getIsFeatured() != null ? req.getIsFeatured() : false)
                .isAvailable(true)
                .avgRating(0.0)
                .reviewCount(0)
                .createdBy(adminUserId)
                .build();

        Product saved = productRepository.save(product);
        log.info("Created product: {} (id={})", saved.getName(), saved.getProductId());

        eventPublisher.publishProductCreated(ProductCreatedEvent.builder()
                .productId(saved.getProductId())
                .productName(saved.getName())
                .unit(saved.getUnit())
                .build());

        return ProductResponse.from(saved);
    }

    public ProductResponse updateProduct(String productId, UpdateProductRequest req) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        if (req.getName() != null) product.setName(req.getName());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        if (req.getShortDescription() != null) product.setShortDescription(req.getShortDescription());
        if (req.getImages() != null && !req.getImages().isEmpty()) {
            // Delete old images from Cloudinary before replacing
            if (product.getImages() != null) {
                product.getImages().forEach(cloudinaryService::deleteImage);
            }
            product.setImages(req.getImages());
            product.setThumbnailUrl(req.getImages().get(0));
        }
        if (req.getCategoryId() != null) {
            Category category = categoryRepository.findByCategoryId(req.getCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
            product.setCategoryId(category.getCategoryId());
            product.setCategoryName(category.getName());
            product.setCategorySlug(category.getSlug());
        }
        if (req.getBrandId() != null) product.setBrandId(req.getBrandId());
        if (req.getMrp() != null) product.setMrp(req.getMrp());
        if (req.getSellingPrice() != null) {
            double mrp = product.getMrp();
            double sp = req.getSellingPrice();
            if (sp > mrp) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selling price cannot exceed MRP");
            product.setSellingPrice(sp);
            product.setDiscountPercent((int) Math.floor(((mrp - sp) / mrp) * 100));
        }
        if (req.getUnit() != null) product.setUnit(req.getUnit());
        if (req.getWeightInGrams() != null) product.setWeightInGrams(req.getWeightInGrams());
        if (req.getTags() != null) product.setTags(req.getTags());
        if (req.getCountryOfOrigin() != null) product.setCountryOfOrigin(req.getCountryOfOrigin());
        if (req.getExpiryInfo() != null) product.setExpiryInfo(req.getExpiryInfo());
        if (req.getNutritionInfo() != null) product.setNutritionInfo(req.getNutritionInfo());
        if (req.getIsFeatured() != null) product.setIsFeatured(req.getIsFeatured());

        Product saved = productRepository.save(product);
        log.info("Updated product: {}", productId);
        return ProductResponse.from(saved);
    }

    public void deleteProduct(String productId) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        // Delete all product images from Cloudinary CDN
        if (product.getImages() != null) {
            product.getImages().forEach(cloudinaryService::deleteImage);
        }

        productRepository.delete(product);
        log.info("Deleted product: {}", productId);
    }

    public ProductResponse toggleAvailability(String productId) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        product.setIsAvailable(!Boolean.TRUE.equals(product.getIsAvailable()));
        Product saved = productRepository.save(product);
        log.info("Toggled availability for product {} to {}", productId, saved.getIsAvailable());
        return ProductResponse.from(saved);
    }

    public Page<ProductResponse> listProducts(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        // Return all products — unavailable ones are shown faded on the customer UI
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }

    public Page<ProductResponse> listByCategory(String slug, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("sellingPrice").ascending());
        // Return all products in category — unavailable shown faded
        return productRepository.findByCategorySlug(slug, pageable).map(ProductResponse::from);
    }

    public Page<ProductResponse> searchProducts(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        // Regex search enables partial/prefix matching (e.g. "oni" matches "onion")
        return productRepository.searchByNameRegex(query, pageable).map(ProductResponse::from);
    }

    public ProductResponse getProduct(String productId) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        return ProductResponse.from(product);
    }

    public Page<ProductResponse> getFeaturedProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByIsFeaturedTrueAndIsAvailableTrue(pageable).map(ProductResponse::from);
    }
}
