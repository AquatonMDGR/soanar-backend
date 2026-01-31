package com.soanar.service.impl;

import com.soanar.model.Announcement;
import com.soanar.model.SocialMediaCredential;
import com.soanar.service.CredentialService;
import com.soanar.service.ImageService;
import com.soanar.service.InstagramService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.core.ParameterizedTypeReference;
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
 * Service for posting announcements to Instagram
 */
@Service
public class InstagramServiceImpl implements InstagramService {

    private static final Logger logger = LoggerFactory.getLogger(InstagramServiceImpl.class);
    private static final String INSTAGRAM_API_URL = "https://graph.instagram.com/v18.0";

    @Value("${instagram.api-key:}")
    private String instagramApiKey;

    @Value("${instagram.context-user:system}")
    private String defaultOrgId;

    private final CredentialService credentialService;
    private final ImageService imageService;
    private final RestTemplate restTemplate;

    public InstagramServiceImpl(CredentialService credentialService,
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
        logger.info("Posting announcement {} to Instagram", announcement.getId());

        // Instagram requires an image
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Instagram requires an image to post");
        }

        UUID orgId = getOrgContext();
        Optional<String> token = credentialService.getDecryptedToken(orgId, SocialMediaCredential.Platform.INSTAGRAM);
        
        if (token.isEmpty()) {
            throw new IllegalStateException("Instagram credentials not configured");
        }

        Optional<SocialMediaCredential> cred = credentialService.getCredential(orgId, SocialMediaCredential.Platform.INSTAGRAM);
        String accountId = cred.map(SocialMediaCredential::getPageId).orElse("");
        
        if (accountId.isEmpty()) {
            throw new IllegalStateException("Instagram account ID not configured");
        }

        // Prepare caption
        String postCaption = caption != null && !caption.isEmpty() ? caption : buildDefaultCaption(announcement);

        try {
            imageService.validateImage(image);
            byte[] imageData = image.getBytes();
            byte[] resizedImage = imageService.resizeForInstagram(imageData);

            // Two-step process for Instagram:
            // Step 1: Create media container with image URL
            // Step 2: Publish the media
            String mediaId = createAndPublishMediaContainer(accountId, token.get(), postCaption, resizedImage);

            logger.info("Successfully posted to Instagram: {}", mediaId);
            return mediaId;

        } catch (Exception e) {
            logger.error("Failed to post to Instagram", e);
            throw e;
        }
    }

    private String createAndPublishMediaContainer(String accountId, String token, String caption, byte[] imageData) throws Exception {
        try {
            // Step 1: Create media container (draft)
            String mediaUrl = String.format("%s/%s/media", INSTAGRAM_API_URL, accountId);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image_url", uploadImageAndGetUrl(imageData));
            body.add("caption", caption);
            body.add("access_token", token);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            
            logger.info("Creating media container on Instagram");
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    mediaUrl,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
                );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Instagram API error creating media: " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            String mediaId = responseBody != null ? (String) responseBody.get("id") : null;

            if (mediaId == null) {
                throw new RuntimeException("No media ID returned from Instagram");
            }

            // Step 2: Publish the media
            String publishUrl = String.format("%s/%s/media_publish", INSTAGRAM_API_URL, accountId);
            Map<String, String> publishParams = new LinkedHashMap<>();
            publishParams.put("creation_id", mediaId);
            publishParams.put("access_token", token);

            String queryString = buildQueryString(publishParams);
            String publishUri = publishUrl + "?" + queryString;

            logger.info("Publishing media container on Instagram");
                ResponseEntity<Map<String, Object>> publishResponse = restTemplate.exchange(
                    publishUri,
                    HttpMethod.POST,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
                );

            if (!publishResponse.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Instagram API error publishing media: " + publishResponse.getStatusCode());
            }

            return mediaId;

        } catch (Exception e) {
            logger.error("Failed to create/publish Instagram media", e);
            throw e;
        }
    }

    private String uploadImageAndGetUrl(byte[] imageData) throws Exception {
        // In production, upload to a file server or S3 and return the URL
        // For now, return a base64 data URI (Instagram may not accept this)
        // This is a limitation that should be addressed in production
        String base64Image = Base64.getEncoder().encodeToString(imageData);
        return "data:image/jpeg;base64," + base64Image;
    }

    @Override
    public void deletePost(String postId) throws Exception {
        logger.info("Deleting Instagram post: {}", postId);

        UUID orgId = getOrgContext();
        Optional<String> token = credentialService.getDecryptedToken(orgId, SocialMediaCredential.Platform.INSTAGRAM);

        if (token.isEmpty()) {
            throw new IllegalStateException("Instagram credentials not configured");
        }

        try {
            String url = String.format("%s/%s?access_token=%s", INSTAGRAM_API_URL, postId, token.get());
            restTemplate.delete(url);
            logger.info("Successfully deleted Instagram post: {}", postId);
        } catch (Exception e) {
            logger.error("Failed to delete Instagram post: {}", postId, e);
            throw e;
        }
    }

    @Override
    public Map<String, Object> getEngagement(String postId) throws Exception {
        logger.info("Getting engagement metrics for post: {}", postId);

        UUID orgId = getOrgContext();
        Optional<String> token = credentialService.getDecryptedToken(orgId, SocialMediaCredential.Platform.INSTAGRAM);

        if (token.isEmpty()) {
            throw new IllegalStateException("Instagram credentials not configured");
        }

        try {
            String url = String.format(
                    "%s/%s?fields=like_count,comments_count,caption&access_token=%s",
                    INSTAGRAM_API_URL, postId, token.get()
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
                engagement.put("likes", responseBody.getOrDefault("like_count", 0));
                engagement.put("comments", responseBody.getOrDefault("comments_count", 0));
                engagement.put("caption", responseBody.getOrDefault("caption", ""));
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
        logger.info("Validating Instagram token");

        UUID orgId = getOrgContext();
        Optional<String> token = credentialService.getDecryptedToken(orgId, SocialMediaCredential.Platform.INSTAGRAM);

        if (token.isEmpty()) {
            logger.warn("No Instagram token configured");
            return false;
        }

        try {
            String url = String.format("%s/me?access_token=%s", INSTAGRAM_API_URL, token.get());
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
                );
            boolean valid = response.getStatusCode().is2xxSuccessful();

            logger.info("Instagram token validation: {}", valid);
            return valid;
        } catch (Exception e) {
            logger.warn("Instagram token validation failed", e);
            return false;
        }
    }

    @Override
    public String getAccountId() throws Exception {
        UUID orgId = getOrgContext();
        Optional<SocialMediaCredential> cred = credentialService.getCredential(orgId, SocialMediaCredential.Platform.INSTAGRAM);

        if (cred.isEmpty()) {
            throw new IllegalStateException("Instagram account ID not configured");
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
            // Truncate to 2200 chars for Instagram
            if (desc.length() > 2200) {
                desc = desc.substring(0, 2200) + "...";
            }
            caption.append(desc);
        }

        // Add some default hashtags
        caption.append("\n\n#UniLife #StudentsFirst");

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
