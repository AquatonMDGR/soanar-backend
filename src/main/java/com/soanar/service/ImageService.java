package com.soanar.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Service for image processing and validation
 */
public interface ImageService {

    /**
     * Validate image file (size, format)
     * @throws IllegalArgumentException if validation fails
     */
    void validateImage(MultipartFile file) throws IOException;

    /**
     * Resize image for Facebook (1200x628)
     */
    byte[] resizeForFacebook(byte[] imageData) throws IOException;

    /**
     * Resize image for Instagram (1080x1350)
     */
    byte[] resizeForInstagram(byte[] imageData) throws IOException;

    /**
     * Get image format/MIME type
     */
    String getImageFormat(MultipartFile file);

    /**
     * Compress image for optimal file size
     */
    byte[] compress(byte[] imageData, String format) throws IOException;
}
