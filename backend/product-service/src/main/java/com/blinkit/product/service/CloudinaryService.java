package com.blinkit.product.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}")    String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {

        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret,
                "secure",     true
        ));
    }

    /**
     * Upload a multipart image to Cloudinary under the "blinkit/products" folder.
     * Returns the secure CDN URL.
     */
    public String uploadImage(MultipartFile file) {
        validateFile(file);
        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder",          "blinkit/products",
                            "resource_type",   "image",
                            "transformation",  "q_auto,f_auto"   // auto quality + format
                    )
            );
            String url = (String) result.get("secure_url");
            log.info("Image uploaded to Cloudinary: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Cloudinary upload failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image upload failed");
        }
    }

    /**
     * Delete an image from Cloudinary by its public_id.
     * The public_id is extracted from the URL path after the version segment.
     */
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        try {
            String publicId = extractPublicId(imageUrl);
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Deleted image from Cloudinary: {}", publicId);
        } catch (IOException e) {
            log.warn("Could not delete Cloudinary image {}: {}", imageUrl, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed");
        }
        long maxBytes = 5L * 1024 * 1024; // 5 MB
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be under 5 MB");
        }
    }

    /**
     * Extract Cloudinary public_id from a secure URL.
     * e.g. https://res.cloudinary.com/demo/image/upload/v123/blinkit/products/abc.jpg
     *   → blinkit/products/abc
     */
    private String extractPublicId(String url) {
        // Remove extension and everything before /upload/
        int uploadIdx = url.indexOf("/upload/");
        if (uploadIdx == -1) return url;
        String afterUpload = url.substring(uploadIdx + 8); // skip "/upload/"
        // Skip version segment if present (v1234567890/)
        if (afterUpload.matches("v\\d+/.*")) {
            afterUpload = afterUpload.substring(afterUpload.indexOf('/') + 1);
        }
        // Remove file extension
        int dotIdx = afterUpload.lastIndexOf('.');
        return dotIdx != -1 ? afterUpload.substring(0, dotIdx) : afterUpload;
    }
}
