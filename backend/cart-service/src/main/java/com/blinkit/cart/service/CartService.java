package com.blinkit.cart.service;

import com.blinkit.cart.client.CouponServiceClient;
import com.blinkit.cart.client.ProductServiceClient;
import com.blinkit.cart.client.dto.ProductDto;
import com.blinkit.cart.client.dto.ValidateCouponRequest;
import com.blinkit.cart.client.dto.ValidateCouponResponse;
import com.blinkit.cart.dto.CartItemData;
import com.blinkit.cart.dto.request.AddItemRequest;
import com.blinkit.cart.dto.request.ApplyPromoRequest;
import com.blinkit.cart.dto.request.UpdateItemRequest;
import com.blinkit.cart.dto.response.CartCountResponse;
import com.blinkit.cart.dto.response.CartItemResponse;
import com.blinkit.cart.dto.response.CartResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductServiceClient productClient;
    private final CouponServiceClient couponClient;

    private static final Duration CART_TTL = Duration.ofDays(7);
    private static final double DELIVERY_FEE = 20.0;
    private static final double FREE_DELIVERY_THRESHOLD = 199.0;
    private static final int MAX_QUANTITY_PER_ITEM = 10;

    // ── Add item ──────────────────────────────────────────────────────

    public CartResponse addItem(String userId, AddItemRequest req) {
        // Fetch product from product-service (circuit breaker fallback marks unavailable)
        ProductDto product = fetchProduct(req.getProductId());

        if (!Boolean.TRUE.equals(product.getIsAvailable())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Product is not available: " + req.getProductId());
        }

        String cartKey = cartKey(userId);
        int currentQty = getCurrentQuantity(cartKey, req.getProductId());
        int newQty = currentQty + req.getQuantity();

        if (newQty > MAX_QUANTITY_PER_ITEM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum quantity per item is " + MAX_QUANTITY_PER_ITEM +
                    ". You already have " + currentQty + " in your cart.");
        }

        CartItemData item = CartItemData.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .imageUrl(product.getThumbnailUrl())
                .unit(product.getUnit())
                .mrp(product.getMrp())
                .unitPrice(product.getSellingPrice())
                .quantity(newQty)
                .build();

        writeItem(cartKey, req.getProductId(), item);
        refreshTtl(cartKey, userId);

        log.info("Added productId={} qty={} to cart for userId={}", req.getProductId(), newQty, userId);
        return buildCartResponse(userId);
    }

    // ── Update item quantity ───────────────────────────────────────────

    public CartResponse updateItem(String userId, String productId, UpdateItemRequest req) {
        String cartKey = cartKey(userId);

        if (req.getQuantity() == 0) {
            redisTemplate.opsForHash().delete(cartKey, productId);
            log.info("Removed productId={} from cart for userId={}", productId, userId);
        } else {
            String existing = (String) redisTemplate.opsForHash().get(cartKey, productId);
            if (existing == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found in cart: " + productId);
            }
            CartItemData item = deserializeItem(existing);
            item.setQuantity(req.getQuantity());
            writeItem(cartKey, productId, item);
        }

        refreshTtl(cartKey, userId);
        return buildCartResponse(userId);
    }

    // ── Remove item ───────────────────────────────────────────────────

    public CartResponse removeItem(String userId, String productId) {
        String cartKey = cartKey(userId);
        redisTemplate.opsForHash().delete(cartKey, productId);
        refreshTtl(cartKey, userId);
        log.info("Removed productId={} from cart for userId={}", productId, userId);
        return buildCartResponse(userId);
    }

    // ── Clear cart ────────────────────────────────────────────────────

    public void clearCart(String userId) {
        redisTemplate.delete(cartKey(userId));
        redisTemplate.delete(promoKey(userId));
        log.info("Cart cleared for userId={}", userId);
    }

    // ── Get cart ──────────────────────────────────────────────────────

    public CartResponse getCart(String userId) {
        return buildCartResponse(userId);
    }

    // ── Get cart count ────────────────────────────────────────────────

    public CartCountResponse getCount(String userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey(userId));
        int totalItems = entries.values().stream()
                .mapToInt(v -> {
                    CartItemData item = deserializeItem((String) v);
                    return item.getQuantity();
                })
                .sum();
        return new CartCountResponse(totalItems);
    }

    // ── Apply promo code ──────────────────────────────────────────────

    public CartResponse applyPromo(String userId, ApplyPromoRequest req) {
        String code = req.getCode().toUpperCase().trim();

        // Calculate current cart total to pass for validation
        CartResponse current = buildCartResponse(userId);
        if (current.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        ValidateCouponResponse validation = couponClient.validate(
                ValidateCouponRequest.builder()
                        .code(code)
                        .userId(userId)
                        .cartTotal(current.getItemsTotal())
                        .build()
        );

        if (!validation.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, validation.getMessage());
        }

        redisTemplate.opsForValue().set(promoKey(userId), code, CART_TTL);
        log.info("Promo code {} applied for userId={}", code, userId);
        return buildCartResponse(userId);
    }

    // ── Remove promo code ─────────────────────────────────────────────

    public CartResponse removePromo(String userId) {
        redisTemplate.delete(promoKey(userId));
        log.info("Promo code removed for userId={}", userId);
        return buildCartResponse(userId);
    }

    // ── Build full cart response ──────────────────────────────────────

    private CartResponse buildCartResponse(String userId) {
        String cartKey = cartKey(userId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey);

        List<CartItemResponse> items = new ArrayList<>();
        double itemsTotal = 0.0;
        int totalItems = 0;

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            CartItemData data = deserializeItem((String) entry.getValue());

            // Live availability check via product-service
            boolean isAvailable = true;
            try {
                ProductDto product = fetchProduct(data.getProductId());
                isAvailable = Boolean.TRUE.equals(product.getIsAvailable());
            } catch (Exception e) {
                isAvailable = false;
            }

            double totalPrice = data.getUnitPrice() * data.getQuantity();
            itemsTotal += totalPrice;
            totalItems += data.getQuantity();

            items.add(CartItemResponse.builder()
                    .productId(data.getProductId())
                    .name(data.getName())
                    .imageUrl(data.getImageUrl())
                    .unit(data.getUnit())
                    .mrp(data.getMrp())
                    .unitPrice(data.getUnitPrice())
                    .quantity(data.getQuantity())
                    .totalPrice(round(totalPrice))
                    .isAvailable(isAvailable)
                    .build());
        }

        items.sort(Comparator.comparing(CartItemResponse::getProductId));

        // Delivery fee
        double deliveryFee = itemsTotal >= FREE_DELIVERY_THRESHOLD ? 0.0 : DELIVERY_FEE;

        // Promo code
        String promoCode = redisTemplate.opsForValue().get(promoKey(userId));
        double couponDiscount = 0.0;
        boolean isFreeDelivery = false;

        if (promoCode != null && !items.isEmpty()) {
            try {
                ValidateCouponResponse couponResp = couponClient.validate(
                        ValidateCouponRequest.builder()
                                .code(promoCode)
                                .userId(userId)
                                .cartTotal(itemsTotal)
                                .build()
                );
                if (couponResp.isValid()) {
                    couponDiscount = couponResp.getDiscountAmount();
                    isFreeDelivery = couponResp.isFreeDelivery();
                    if (isFreeDelivery) deliveryFee = 0.0;
                } else {
                    // Coupon no longer valid — silently remove it
                    redisTemplate.delete(promoKey(userId));
                    promoCode = null;
                }
            } catch (Exception e) {
                log.warn("Could not validate promo {} for userId={}: {}", promoCode, userId, e.getMessage());
                promoCode = null;
            }
        }

        double totalAmount = Math.max(0.0, itemsTotal + deliveryFee - couponDiscount);

        return CartResponse.builder()
                .items(items)
                .itemsTotal(round(itemsTotal))
                .deliveryFee(round(deliveryFee))
                .couponCode(promoCode)
                .couponDiscount(round(couponDiscount))
                .isFreeDelivery(isFreeDelivery)
                .totalAmount(round(totalAmount))
                .totalItems(totalItems)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private ProductDto fetchProduct(String productId) {
        try {
            var response = productClient.getProduct(productId);
            if (response == null || response.getData() == null) {
                ProductDto unavailable = new ProductDto();
                unavailable.setProductId(productId);
                unavailable.setIsAvailable(false);
                return unavailable;
            }
            return response.getData();
        } catch (feign.FeignException.NotFound e) {
            // Product genuinely doesn't exist — propagate as 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Product not found: " + productId);
        } catch (Exception e) {
            // product-service is down or circuit breaker open — mark unavailable
            log.warn("product-service unavailable for productId={}: {}", productId, e.getMessage());
            ProductDto unavailable = new ProductDto();
            unavailable.setProductId(productId);
            unavailable.setIsAvailable(false);
            return unavailable;
        }
    }

    private int getCurrentQuantity(String cartKey, String productId) {
        String existing = (String) redisTemplate.opsForHash().get(cartKey, productId);
        if (existing == null) return 0;
        return deserializeItem(existing).getQuantity();
    }

    private void writeItem(String cartKey, String productId, CartItemData item) {
        try {
            redisTemplate.opsForHash().put(cartKey, productId, objectMapper.writeValueAsString(item));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update cart");
        }
    }

    private void refreshTtl(String cartKey, String userId) {
        redisTemplate.expire(cartKey, CART_TTL);
        String promoKey = promoKey(userId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(promoKey))) {
            redisTemplate.expire(promoKey, CART_TTL);
        }
    }

    private CartItemData deserializeItem(String json) {
        try {
            return objectMapper.readValue(json, CartItemData.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read cart data");
        }
    }

    private String cartKey(String userId) {
        return "cart:" + userId;
    }

    private String promoKey(String userId) {
        return "cart:" + userId + ":promo";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
