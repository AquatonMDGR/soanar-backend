package com.soanar.service.impl;

import com.soanar.model.Announcement;
import com.soanar.model.SocialMediaCredential;
import com.soanar.service.CredentialService;
import com.soanar.service.FacebookService;
import com.soanar.service.ImageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;


/**
 * Service for posting announcements to Facebook
 */
@Service
public class FacebookServiceImpl implements FacebookService {

    private static final Logger logger = LoggerFactory.getLogger(FacebookServiceImpl.class);
    private static final String FACEBOOK_API_URL = "https://graph.facebook.com/v19.0";

    @Value("${facebook.api-key:}")
    private String facebookApiKey;

    private final CredentialService credentialService;
    private final ImageService imageService;
    private final RestTemplate restTemplate;

    public FacebookServiceImpl(CredentialService credentialService,
                             ImageService imageService,
                             RestTemplate restTemplate) {
        this.credentialService = credentialService;
        this.imageService = imageService;
        this.restTemplate = restTemplate;
    }

    @Override
    public String postAnnouncement(Announcement announcement, String caption, MultipartFile image, UUID organizationId) throws Exception {
        logger.info("Posting announcement {} to Facebook", announcement.getId());

        UUID orgId = organizationId;

        // Get credentials
        Optional<String> token = credentialService.getDecryptedToken(orgId, SocialMediaCredential.Platform.FACEBOOK);
        if (token.isEmpty()) {
            throw new IllegalStateException("Facebook credentials not configured");
        }

        // Get decrypted page ID
        Optional<String> pageIdOpt = credentialService.getDecryptedPageId(orgId, SocialMediaCredential.Platform.FACEBOOK);
        if (pageIdOpt.isEmpty()) {
            throw new IllegalStateException("Facebook page ID not configured");
        }
        
        String pageId = pageIdOpt.get();

        // Prepare caption
        String postCaption = caption != null && !caption.isEmpty() ? caption : buildDefaultCaption(announcement);

        try {
            String postId;

            // ALWAYS check imageUrls first (like NotificationService email pattern)
            List<String> storedImageUrls = announcement.getImageUrls();

            if (storedImageUrls != null && storedImageUrls.size() > 1) {
                // Multiple images from database storage - create album post
                logger.info("Posting Facebook announcement with {} stored images (album)", storedImageUrls.size());
                postId = postWithMultipleImages(pageId, token.get(), postCaption, storedImageUrls);
            }
            else if (storedImageUrls != null && storedImageUrls.size() == 1) {
                // Single image from database storage
                logger.info("Posting Facebook announcement with 1 stored image");
                postId = postWithImageUrl(pageId, token.get(), postCaption, storedImageUrls.get(0));
            }
            else if (announcement.getImageUrl() != null && !announcement.getImageUrl().isBlank()) {
                // Legacy single image field - backward compatibility
                logger.info("Posting Facebook announcement with legacy imageUrl");
                postId = postWithImageUrl(pageId, token.get(), postCaption, announcement.getImageUrl());
            }
            else if (image != null && !image.isEmpty()) {
                // Single image from uploaded file (crosspost endpoint)
                logger.info("Posting Facebook announcement with uploaded image file");
                imageService.validateImage(image);
                byte[] imageData = image.getBytes();
                byte[] resizedImage = imageService.resizeForFacebook(imageData);
                postId = uploadAndPostImage(pageId, token.get(), postCaption, resizedImage);
            }
            else {
                // Text-only post
                String feedUrl = String.format("%s/%s/feed", FACEBOOK_API_URL, pageId);
                logger.info("Posting Facebook announcement without image (text-only)");
                postId = postTextOnly(feedUrl, token.get(), postCaption);
            }

            logger.info("Successfully posted to Facebook: {}", postId);
            return postId;

        } catch (Exception e) {
            logger.error("Failed to post to Facebook", e);
            throw e;
        }
    }

    private String uploadAndPostImage(String pageId, String token, String message, byte[] imageData) throws Exception {
        try {
            // Use /photos endpoint for binary image upload
            String photosUrl = String.format("%s/%s/photos", FACEBOOK_API_URL, pageId);
            
            // Create multipart request for image upload
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("source", new ByteArrayResource(imageData) {
                @Override
                public String getFilename() {
                    return "announcement.jpg";
                }
            });
            body.add("message", message);  // Use 'message' not 'caption' for photos endpoint
            body.add("access_token", token);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            
            logger.info("Uploading image to Facebook photos: {}", photosUrl);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    photosUrl,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
                );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Facebook API error: " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            String postId = responseBody != null ? (String) responseBody.get("id") : null;

            if (postId == null) {
                throw new RuntimeException("No post ID returned from Facebook");
            }

            logger.info("Successfully uploaded photo to Facebook: {}", postId);
            return postId;

        } catch (Exception e) {
            logger.error("Failed to upload image to Facebook", e);
            throw e;
        }
    }

    private String postWithImageUrl(String pageId, String token, String message, String imageUrl) throws Exception {
        try {
            // Download image from URL
            logger.info("Downloading image from URL: {}", imageUrl);
            byte[] imageData = restTemplate.getForObject(imageUrl, byte[].class);
            if (imageData == null || imageData.length == 0) {
                throw new RuntimeException("Failed to download image from URL: " + imageUrl);
            }

            logger.info("Downloaded {} bytes, resizing for Facebook", imageData.length);
            byte[] resizedImage = imageService.resizeForFacebook(imageData);

            // Upload image and post
            return uploadAndPostImage(pageId, token, message, resizedImage);

        } catch (Exception e) {
            logger.error("Failed to post with image URL: {}", imageUrl, e);
            throw e;
        }
    }

    private String postTextOnly(String feedUrl, String token, String message) throws Exception {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("message", message);
            params.put("access_token", token);

            String queryString = buildQueryString(params);
            String url = feedUrl + "?" + queryString;

            logger.info("Posting text-only to Facebook: {}", feedUrl);
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
                );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Facebook API error: " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            String postId = responseBody != null ? (String) responseBody.get("id") : null;

            if (postId == null) {
                throw new RuntimeException("No post ID returned from Facebook");
            }

            return postId;

        } catch (Exception e) {
            logger.error("Failed to post text to Facebook", e);
            throw e;
        }
    }

    @Override
    public void deletePost(String postId, UUID organizationId) throws Exception {
        logger.info("Deleting Facebook post: {}", postId);

        UUID orgId = organizationId;
        Optional<String> token = credentialService.getDecryptedToken(orgId, SocialMediaCredential.Platform.FACEBOOK);

        if (token.isEmpty()) {
            throw new IllegalStateException("Facebook credentials not configured");
        }

        try {
            String url = String.format("%s/%s?access_token=%s", FACEBOOK_API_URL, postId, token.get());
            restTemplate.delete(url);
            logger.info("Successfully deleted Facebook post: {}", postId);
        } catch (Exception e) {
            logger.error("Failed to delete Facebook post: {}", postId, e);
            throw e;
        }
    }

    @Override
    public Map<String, Object> getEngagement(String postId, UUID organizationId) throws Exception {
        logger.info("Getting engagement metrics for post: {}", postId);

        UUID orgId = organizationId;
        Optional<String> token = credentialService.getDecryptedToken(orgId, SocialMediaCredential.Platform.FACEBOOK);

        if (token.isEmpty()) {
            throw new IllegalStateException("Facebook credentials not configured");
        }

        try {
            String url = String.format(
                    "%s/%s?fields=likes.summary(true).limit(0),comments.summary(true).limit(0),shares&access_token=%s",
                    FACEBOOK_API_URL, postId, token.get()
            );

                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
                );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to get engagement metrics");
            }

            Map<String, Object> responseBody = response.getBody();
            Map<String, Object> engagement = new HashMap<>();

            if (responseBody != null) {
                // Extract engagement data
                if (responseBody.containsKey("likes")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> likes = (Map<String, Object>) responseBody.get("likes");
                    if (likes.containsKey("summary")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> summary = (Map<String, Object>) likes.get("summary");
                        engagement.put("likes", summary.get("total_count"));
                    }
                }
                if (responseBody.containsKey("comments")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> comments = (Map<String, Object>) responseBody.get("comments");
                    if (comments.containsKey("summary")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> summary = (Map<String, Object>) comments.get("summary");
                        engagement.put("comments", summary.get("total_count"));
                    }
                }
                if (responseBody.containsKey("shares")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> shares = (Map<String, Object>) responseBody.get("shares");
                    engagement.put("shares", shares.get("count"));
                }
            }

            logger.info("Retrieved engagement metrics for post: {}", postId);
            return engagement;
        } catch (Exception e) {
            logger.error("Failed to get engagement metrics", e);
            throw e;
        }
    }

    @Override
    public boolean validateToken(UUID organizationId) throws Exception {
        logger.info("Validating Facebook token");

        UUID orgId = organizationId;
        Optional<String> token = credentialService.getDecryptedToken(orgId, SocialMediaCredential.Platform.FACEBOOK);

        if (token.isEmpty()) {
            logger.warn("No Facebook token configured");
            return false;
        }

        try {
            String url = String.format("%s/me?access_token=%s", FACEBOOK_API_URL, token.get());
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
                );
            boolean valid = response.getStatusCode().is2xxSuccessful();

            logger.info("Facebook token validation: {}", valid);
            return valid;
        } catch (Exception e) {
            logger.warn("Facebook token validation failed", e);
            return false;
        }
    }

    @Override
    public String getPageId(UUID organizationId) throws Exception {
        UUID orgId = organizationId;
        Optional<SocialMediaCredential> cred = credentialService.getCredential(orgId, SocialMediaCredential.Platform.FACEBOOK);

        if (cred.isEmpty()) {
            throw new IllegalStateException("Facebook page ID not configured");
        }

        return cred.get().getPageId();
    }

    private String buildDefaultCaption(Announcement announcement) {
        StringBuilder caption = new StringBuilder();
        caption.append(announcement.getTitle()).append("\n\n");

        if (announcement.getDescription() != null && !announcement.getDescription().isEmpty()) {
            String desc = announcement.getDescription();
            // Truncate to 500 chars for Facebook
            if (desc.length() > 500) {
                desc = desc.substring(0, 500) + "...";
            }
            caption.append(desc);
        }

        return caption.toString();
    }

    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /**
     * Posts an announcement with multiple images as a Facebook album/carousel.
     * Follows the email notification pattern: download → resize → upload unpublished → collect IDs → create feed post.
     */
    private String postWithMultipleImages(String pageId, String token, String message, List<String> imageUrls) throws Exception {
        logger.info("===== MULTI-IMAGE POST FLOW: {} images =====", imageUrls.size());
        
        List<String> photoIds = new ArrayList<>();
        
        try {
            // Step 1: Upload each image as unpublished photo, collect photo IDs
            for (int i = 0; i < imageUrls.size(); i++) {
                String imageUrl = imageUrls.get(i);
                logger.info("Processing image {}/{}: {}", i + 1, imageUrls.size(), imageUrl);
                
                try {
                    // Download image from Supabase storage URL
                    logger.info("Downloading image from: {}", imageUrl);
                    URL url = new URL(imageUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    
                    byte[] imageData = null;
                    try (InputStream is = connection.getInputStream()) {
                        imageData = is.readAllBytes();
                    }
                    
                    logger.info("Downloaded: {} bytes, resizing for Facebook", imageData.length);
                    
                    // Resize for Facebook (1200x628)
                    byte[] resizedData = imageService.resizeForFacebook(imageData);
                    
                    // Upload as unpublished photo
                    String photoId = uploadUnpublishedPhoto(pageId, token, resizedData);
                    photoIds.add(photoId);
                    logger.info("Uploaded photo {}/{}, photo_id: {}", i + 1, imageUrls.size(), photoId);
                    
                } catch (Exception e) {
                    logger.error("Error processing image {}: {}", imageUrl, e.getMessage());
                    throw new Exception("Failed to process image " + (i + 1) + ": " + e.getMessage(), e);
                }
            }
            
            if (photoIds.isEmpty()) {
                throw new Exception("No photos were successfully uploaded");
            }
            
            logger.info("All {} photos uploaded, creating feed post with attached_media", photoIds.size());
            
            // Step 2: Create feed post with all photo IDs as attached_media
            String postId = createFeedPostWithPhotos(pageId, token, message, photoIds);
            
            logger.info("===== MULTI-IMAGE POST COMPLETE: post_id={} with {} photos =====", postId, photoIds.size());
            return postId;
            
        } catch (Exception e) {
            logger.error("Multi-image post failed: {}", e.getMessage(), e);
            throw new Exception("Multi-image post failed: " + e.getMessage(), e);
        }
    }

    /**
     * Uploads a single photo to a Facebook page as unpublished.
     * Returns the photo ID from Facebook's response.
     * Used by postWithMultipleImages to collect photo IDs before creating feed post.
     */
    private String uploadUnpublishedPhoto(String pageId, String token, byte[] imageData) throws Exception {
        String photoUrl = String.format("%s/%s/photos", FACEBOOK_API_URL, pageId);
        
        logger.info("Uploading unpublished photo to: {}", photoUrl);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            // Build multipart request
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("access_token", token);
            body.add("published", "false");  // KEY: unpublished so we can use in attached_media
            
            // Add image as byte array
            ByteArrayResource imageResource = new ByteArrayResource(imageData) {
                @Override
                public String getFilename() {
                    return "photo.jpg";
                }
            };
            body.add("source", imageResource);
            
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(photoUrl, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object photoId = response.getBody().get("id");
                if (photoId != null) {
                    logger.info("Photo uploaded successfully, id: {}", photoId);
                    return photoId.toString();
                }
            }
            
            throw new Exception("No photo ID in response: " + response.getBody());
            
        } catch (Exception e) {
            logger.error("Photo upload failed: {}", e.getMessage(), e);
            throw new Exception("Failed to upload unpublished photo: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a Facebook feed post with multiple attached media (photos).
     * This combines the collected photo IDs into a single album/carousel post.
     * Used by postWithMultipleImages to finalize the multi-image post.
     */
    private String createFeedPostWithPhotos(String pageId, String token, String message, List<String> photoIds) throws Exception {
        String feedUrl = String.format("%s/%s/feed", FACEBOOK_API_URL, pageId);
        
        logger.info("Creating feed post with {} attached photos at: {}", photoIds.size(), feedUrl);
        
        try {
            // Build attached_media array: [{"media_fbid":"123"},{"media_fbid":"456"}]
            List<Map<String, String>> attachedMedia = new ArrayList<>();
            for (String photoId : photoIds) {
                Map<String, String> mediaItem = new HashMap<>();
                mediaItem.put("media_fbid", photoId);
                attachedMedia.add(mediaItem);
            }
            
            // Serialize to JSON
            ObjectMapper mapper = new ObjectMapper();
            String attachedMediaJson = mapper.writeValueAsString(attachedMedia);
            logger.info("Attached media JSON: {}", attachedMediaJson);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            // Build form data
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("access_token", token);
            body.add("message", message);
            body.add("attached_media", attachedMediaJson);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(feedUrl, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object postId = response.getBody().get("id");
                if (postId != null) {
                    logger.info("Feed post created successfully with {} photos, id: {}", photoIds.size(), postId);
                    return postId.toString();
                }
            }
            
            throw new Exception("No post ID in response: " + response.getBody());
            
        } catch (Exception e) {
            logger.error("Feed post creation failed: {}", e.getMessage(), e);
            throw new Exception("Failed to create feed post with photos: " + e.getMessage(), e);
        }
    }
}
