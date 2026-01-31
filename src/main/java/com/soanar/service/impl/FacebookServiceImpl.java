package com.soanar.service.impl;

import com.soanar.model.Announcement;
import com.soanar.model.SocialMediaCredential;
import com.soanar.service.CredentialService;
import com.soanar.service.FacebookService;
import com.soanar.service.ImageService;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service for posting announcements to Facebook
 */
@Service
public class FacebookServiceImpl implements FacebookService {

    private static final Logger logger = LoggerFactory.getLogger(FacebookServiceImpl.class);
    private static final String FACEBOOK_API_URL = "https://graph.facebook.com/v18.0";

    @Value("${facebook.api-key:}")
    private String facebookApiKey;

    @Value("${facebook.context-user:system}") // Default org context
    private String defaultOrgId;

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

    /**
     * Set organization context (extracted from JWT in calling service)
     */
    public void setOrgContext(UUID orgId) {
        // This will be called from CrosspostService with user's organization ID
    }

    @Override
    public String postAnnouncement(Announcement announcement, String caption, MultipartFile image) throws Exception {
        logger.info("Posting announcement {} to Facebook", announcement.getId());

        // Get default org ID if not set (this should be passed from CrosspostService)
        UUID orgId = getOrgContext();

        // Get credentials
        Optional<String> token = credentialService.getDecryptedToken(orgId, SocialMediaCredential.Platform.FACEBOOK);
        if (token.isEmpty()) {
            throw new IllegalStateException("Facebook credentials not configured");
        }

        // Get page ID
        Optional<SocialMediaCredential> cred = credentialService.getCredential(orgId, SocialMediaCredential.Platform.FACEBOOK);
        String pageId = cred.map(SocialMediaCredential::getPageId).orElse("");
        
        if (pageId.isEmpty()) {
            throw new IllegalStateException("Facebook page ID not configured");
        }

        // Prepare caption
        String postCaption = caption != null && !caption.isEmpty() ? caption : buildDefaultCaption(announcement);

        try {
            String feedUrl = String.format("%s/%s/feed", FACEBOOK_API_URL, pageId);
            String postId;

            // Post with image if provided
            if (image != null && !image.isEmpty()) {
                imageService.validateImage(image);
                byte[] imageData = image.getBytes();
                byte[] resizedImage = imageService.resizeForFacebook(imageData);

                // Upload image and post
                postId = uploadAndPostImage(feedUrl, token.get(), postCaption, resizedImage);
            } else {
                // Post without image
                postId = postTextOnly(feedUrl, token.get(), postCaption);
            }

            logger.info("Successfully posted to Facebook: {}", postId);
            return postId;

        } catch (Exception e) {
            logger.error("Failed to post to Facebook", e);
            throw e;
        }
    }

    private String uploadAndPostImage(String feedUrl, String token, String message, byte[] imageData) throws Exception {
        try {
            // Create multipart request for image upload
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("source", new ByteArrayResource(imageData) {
                @Override
                public String getFilename() {
                    return "announcement.jpg";
                }
            });
            body.add("caption", message);
            body.add("access_token", token);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            
            logger.info("Uploading image to Facebook: {}", feedUrl);
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    feedUrl,
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

            return postId;

        } catch (Exception e) {
            logger.error("Failed to upload image to Facebook", e);
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
    public void deletePost(String postId) throws Exception {
        logger.info("Deleting Facebook post: {}", postId);

        UUID orgId = getOrgContext();
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
    public Map<String, Object> getEngagement(String postId) throws Exception {
        logger.info("Getting engagement metrics for post: {}", postId);

        UUID orgId = getOrgContext();
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
    public boolean validateToken() throws Exception {
        logger.info("Validating Facebook token");

        UUID orgId = getOrgContext();
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
    public String getPageId() throws Exception {
        UUID orgId = getOrgContext();
        Optional<SocialMediaCredential> cred = credentialService.getCredential(orgId, SocialMediaCredential.Platform.FACEBOOK);

        if (cred.isEmpty()) {
            throw new IllegalStateException("Facebook page ID not configured");
        }

        return cred.get().getPageId();
    }

    private UUID getOrgContext() {
        // In a real implementation, extract from SecurityContext or ThreadLocal
        // For now, use a default
        try {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        } catch (Exception e) {
            return null;
        }
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
}
