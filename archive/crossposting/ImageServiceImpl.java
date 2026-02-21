package com.soanar.service.impl;

import com.soanar.service.ImageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Implementation of image processing service using Java ImageIO
 */
@Service
public class ImageServiceImpl implements ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageServiceImpl.class);

    // Facebook: 1200x628 (1.91:1 aspect ratio)
    private static final int FACEBOOK_WIDTH = 1200;
    private static final int FACEBOOK_HEIGHT = 628;

    // Instagram: 1080x1350 (4:5 aspect ratio)
    private static final int INSTAGRAM_WIDTH = 1080;
    private static final int INSTAGRAM_HEIGHT = 1350;

    // Max file size: 8MB
    private static final long MAX_FILE_SIZE = 8 * 1024 * 1024;

    @Value("${image.quality:0.85}")
    private float jpegQuality;

    @Override
    public void validateImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Image size exceeds 8MB limit");
        }

        // Check MIME type
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") &&
                !contentType.equals("image/png") &&
                !contentType.equals("image/gif") &&
                !contentType.equals("image/webp"))) {
            throw new IllegalArgumentException("Only JPG, PNG, GIF, and WebP images are supported");
        }

        // Validate image can be read
        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img == null) {
                throw new IllegalArgumentException("Invalid image format");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read image file", e);
        }

        logger.info("Image validation passed: {} bytes", file.getSize());
    }

    @Override
    public byte[] resizeForFacebook(byte[] imageData) throws IOException {
        logger.info("Resizing image for Facebook (1200x628)");
        return resizeImage(imageData, FACEBOOK_WIDTH, FACEBOOK_HEIGHT);
    }

    @Override
    public byte[] resizeForInstagram(byte[] imageData) throws IOException {
        logger.info("Resizing image for Instagram (1080x1350)");
        return resizeImage(imageData, INSTAGRAM_WIDTH, INSTAGRAM_HEIGHT);
    }

    @Override
    public String getImageFormat(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return "jpg";
        }

        if (contentType.equals("image/jpeg")) return "jpg";
        if (contentType.equals("image/png")) return "png";
        if (contentType.equals("image/gif")) return "gif";
        if (contentType.equals("image/webp")) return "webp";

        return "jpg"; // Default
    }

    @Override
    public byte[] compress(byte[] imageData, String format) throws IOException {
        logger.info("Compressing image to format: {}", format);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageData)) {
            BufferedImage img = ImageIO.read(bais);
            if (img == null) {
                return imageData;
            }

            return compressToBytes(img, format);
        }
    }

    /**
     * Resize image while maintaining aspect ratio
     * Uses white background for padding if needed
     */
    private byte[] resizeImage(byte[] imageData, int targetWidth, int targetHeight) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageData)) {
            BufferedImage original = ImageIO.read(bais);
            if (original == null) {
                throw new IOException("Failed to read image");
            }

            // Calculate dimensions maintaining aspect ratio
            Dimension scaledDim = getScaledDimension(
                    original.getWidth(),
                    original.getHeight(),
                    targetWidth,
                    targetHeight
            );

            // Create resized image
            BufferedImage resized = new BufferedImage(
                    targetWidth,
                    targetHeight,
                    BufferedImage.TYPE_INT_RGB
            );

            // Fill with white background
            Graphics2D g2d = resized.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, targetWidth, targetHeight);

            // Calculate position to center image
            int x = (targetWidth - scaledDim.width) / 2;
            int y = (targetHeight - scaledDim.height) / 2;

            // Draw scaled image centered
            g2d.drawImage(
                    original.getScaledInstance(scaledDim.width, scaledDim.height, Image.SCALE_SMOOTH),
                    x, y, null
            );
            g2d.dispose();

            return compressToBytes(resized, "jpg");
        }
    }

    /**
     * Calculate scaled dimensions while maintaining aspect ratio
     */
    private Dimension getScaledDimension(int originalWidth, int originalHeight,
                                        int boundWidth, int boundHeight) {
        double originalAspect = (double) originalWidth / originalHeight;
        double boundAspect = (double) boundWidth / boundHeight;

        int newWidth;
        int newHeight;

        if (originalAspect > boundAspect) {
            // Original is wider - scale by width
            newWidth = boundWidth;
            newHeight = (int) (boundWidth / originalAspect);
        } else {
            // Original is taller - scale by height
            newHeight = boundHeight;
            newWidth = (int) (boundHeight * originalAspect);
        }

        return new Dimension(newWidth, newHeight);
    }

    /**
     * Compress image to bytes with JPEG quality setting
     */
    private byte[] compressToBytes(BufferedImage image, String format) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (format.equalsIgnoreCase("jpg") || format.equalsIgnoreCase("jpeg")) {
                // Use JPEG with quality setting
                ImageIO.write(image, "jpg", baos);
            } else {
                // Use PNG for other formats (preserves transparency)
                ImageIO.write(image, format, baos);
            }
            return baos.toByteArray();
        }
    }
}
