package com.blinkit.product.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.product.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageUploadController {

    private final CloudinaryService cloudinaryService;

    /**
     * POST /api/images/upload
     * Requires ADMIN role (enforced via X-User-Role header injected by the gateway).
     * Accepts a multipart file and returns the Cloudinary CDN URL.
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(
            @RequestHeader("X-User-Role") String role,
            @RequestPart("file") MultipartFile file) {

        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.fail("Only admins can upload images"));
        }

        String url = cloudinaryService.uploadImage(file);
        return ResponseEntity.ok(ApiResponse.ok("Image uploaded successfully",
                Map.of("url", url)));
    }
}
