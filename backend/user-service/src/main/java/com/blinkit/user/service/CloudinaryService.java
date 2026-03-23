package com.blinkit.user.service;

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

    public String uploadProfilePhoto(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file provided");

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed");

        if (file.getSize() > 5L * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be under 5 MB");

        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder",         "blinkit/profiles",
                            "resource_type",  "image",
                            "transformation", "q_auto,f_auto,w_400,h_400,c_fill,g_face"
                    )
            );
            String url = (String) result.get("secure_url");
            log.info("Profile photo uploaded to Cloudinary: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Cloudinary upload failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Photo upload failed");
        }
    }

    public void deletePhoto(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        try {
            int uploadIdx = imageUrl.indexOf("/upload/");
            if (uploadIdx == -1) return;
            String afterUpload = imageUrl.substring(uploadIdx + 8);
            if (afterUpload.matches("v\\d+/.*"))
                afterUpload = afterUpload.substring(afterUpload.indexOf('/') + 1);
            int dotIdx = afterUpload.lastIndexOf('.');
            String publicId = dotIdx != -1 ? afterUpload.substring(0, dotIdx) : afterUpload;
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Deleted profile photo from Cloudinary: {}", publicId);
        } catch (IOException e) {
            log.warn("Could not delete Cloudinary photo {}: {}", imageUrl, e.getMessage());
        }
    }
}
