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
    private static final String INSTAGRAM_API_URL = "https://graph.facebook.com/v19.0";

    @Value("${instagram.api-key:}")
    private String instagramApiKey;

    @Value("${instagram.system-user.enabled:false}")
    private boolean systemUserEnabled;

    @Value("${instagram.system-user.token:}")
    private String systemUserToken;

    @Value("${instagram.system-user.account-id:}")
    private String systemUserAccountId;

    @Value("${media.public-base-url:}")
    private String mediaPublicBaseUrl;

    @Value("${media.origin-base-url:}")
    private String mediaOriginBaseUrl;

    @Value("${media.use-signed-urls:false}")
    private boolean mediaUseSignedUrls;

    @Value("${media.signed-url-ttl-seconds:600}")
    private long mediaSignedUrlTtlSeconds;

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key:}")
    private String supabaseKey;

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

    @Override
    public String postAnnouncement(Announcement announcement, String caption, List<MultipartFile> images, UUID organizationId) throws Exception {
        logger.info("Posting announcement {} to Instagram", announcement.getId());

        List<String> imageUrls = new ArrayList<>();
        if (announcement.getImageUrls() != null) {
            imageUrls.addAll(announcement.getImageUrls());
        }
        if (imageUrls.isEmpty() && announcement.getImageUrl() != null && !announcement.getImageUrl().isBlank()) {
            imageUrls.add(announcement.getImageUrl());
        }
        if (imageUrls.size() > 10) {
            logger.warn("Announcement has {} images; only the first 10 will be posted to Instagram", imageUrls.size());
            imageUrls = imageUrls.subList(0, 10);
        }

        if (imageUrls.isEmpty()) {
            if (images != null && images.stream().anyMatch(img -> img != null && !img.isEmpty())) {
                logger.warn("Instagram posting requested with uploaded files, but no public image URLs are stored");
            }
            throw new IllegalArgumentException("Instagram requires public image URLs. Upload the announcement images first.");
        }

        imageUrls = normalizeImageUrls(imageUrls);

        // Get credentials (System User or OAuth)
        TokenInfo tokenInfo = getTokenAndAccountId(organizationId);
        String token = tokenInfo.token;
        String accountId = tokenInfo.accountId;
        
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Instagram credentials not configured");
        }
        if (accountId == null || accountId.isBlank()) {
            logger.error("Instagram account ID is empty or null. Instagram Business Account was not properly retrieved during OAuth.");
            throw new IllegalStateException("Instagram account ID is empty. Ensure your Instagram Business Account is connected to your Facebook account and try reconnecting.");
        }

        logger.info("Using {} token for Instagram posting", tokenInfo.tokenType);

        // Prepare caption
        String postCaption = caption != null && !caption.isEmpty() ? caption : buildDefaultCaption(announcement);

        try {
            String mediaId;
            if (imageUrls.size() > 1) {
                mediaId = createAndPublishCarouselContainer(accountId, token, postCaption, imageUrls);
            } else {
                String imageUrl = imageUrls.get(0);
                mediaId = createAndPublishMediaContainer(accountId, token, postCaption, imageUrl);
            }

            logger.info("Successfully posted to Instagram: {}", mediaId);
            return mediaId;

        } catch (Exception e) {
            logger.error("Failed to post to Instagram", e);
            throw e;
        }
    }

    private String createAndPublishMediaContainer(String accountId, String token, String caption, String imageUrl) throws Exception {
        try {
            // Step 1: Create media container (draft)
            String mediaUrl = String.format("%s/%s/media", INSTAGRAM_API_URL, accountId);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image_url", imageUrl);
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

    private String createAndPublishCarouselContainer(String accountId, String token, String caption, List<String> imageUrls) throws Exception {
        try {
            String mediaUrl = String.format("%s/%s/media", INSTAGRAM_API_URL, accountId);
            List<String> creationIds = new ArrayList<>();

            for (int i = 0; i < imageUrls.size(); i++) {
                String imageUrl = imageUrls.get(i);
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("image_url", imageUrl);
                body.add("is_carousel_item", "true");
                body.add("access_token", token);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
                logger.info("Creating carousel item {}/{}", i + 1, imageUrls.size());
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    mediaUrl,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
                );

                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new RuntimeException("Instagram API error creating carousel item: " + response.getStatusCode());
                }

                Map<String, Object> responseBody = response.getBody();
                String creationId = responseBody != null ? (String) responseBody.get("id") : null;
                if (creationId == null) {
                    throw new RuntimeException("No creation ID returned for carousel item");
                }
                creationIds.add(creationId);
            }

            String children = String.join(",", creationIds);
            MultiValueMap<String, Object> carouselBody = new LinkedMultiValueMap<>();
            carouselBody.add("media_type", "CAROUSEL");
            carouselBody.add("children", children);
            carouselBody.add("caption", caption);
            carouselBody.add("access_token", token);

            HttpHeaders carouselHeaders = new HttpHeaders();
            carouselHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, Object>> carouselRequest = new HttpEntity<>(carouselBody, carouselHeaders);
            logger.info("Creating carousel container with {} items", creationIds.size());
            ResponseEntity<Map<String, Object>> carouselResponse = restTemplate.exchange(
                mediaUrl,
                HttpMethod.POST,
                carouselRequest,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (!carouselResponse.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Instagram API error creating carousel container: " + carouselResponse.getStatusCode());
            }

            Map<String, Object> carouselBodyResponse = carouselResponse.getBody();
            String creationId = carouselBodyResponse != null ? (String) carouselBodyResponse.get("id") : null;
            if (creationId == null) {
                throw new RuntimeException("No creation ID returned for carousel container");
            }

            String publishUrl = String.format("%s/%s/media_publish", INSTAGRAM_API_URL, accountId);
            Map<String, String> publishParams = new LinkedHashMap<>();
            publishParams.put("creation_id", creationId);
            publishParams.put("access_token", token);

            String queryString = buildQueryString(publishParams);
            String publishUri = publishUrl + "?" + queryString;

            logger.info("Publishing carousel container");
            ResponseEntity<Map<String, Object>> publishResponse = restTemplate.exchange(
                publishUri,
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (!publishResponse.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Instagram API error publishing carousel: " + publishResponse.getStatusCode());
            }

            return creationId;
        } catch (Exception e) {
            logger.error("Failed to create/publish Instagram carousel", e);
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
    public void deletePost(String postId, UUID organizationId) throws Exception {
        logger.info("Deleting Instagram post: {}", postId);

        TokenInfo tokenInfo = getTokenAndAccountId(organizationId);
        String token = tokenInfo.token;

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Instagram credentials not configured");
        }

        try {
            String url = String.format("%s/%s?access_token=%s", INSTAGRAM_API_URL, postId, token);
            restTemplate.delete(url);
            logger.info("Successfully deleted Instagram post: {}", postId);
        } catch (Exception e) {
            logger.error("Failed to delete Instagram post: {}", postId, e);
            throw e;
        }
    }

    @Override
    public Map<String, Object> getEngagement(String postId, UUID organizationId) throws Exception {
        logger.info("Getting engagement metrics for post: {}", postId);

        TokenInfo tokenInfo = getTokenAndAccountId(organizationId);
        String token = tokenInfo.token;

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Instagram credentials not configured");
        }

        try {
            String url = String.format(
                    "%s/%s?fields=like_count,comments_count,caption&access_token=%s",
                    INSTAGRAM_API_URL, postId, token
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
    public boolean validateToken(UUID organizationId) throws Exception {
        logger.info("Validating Instagram token");

        TokenInfo tokenInfo = getTokenAndAccountId(organizationId);
        String token = tokenInfo.token;

        if (token == null || token.isBlank()) {
            logger.warn("No Instagram token configured");
            return false;
        }

        try {
            String url = String.format("%s/me?access_token=%s", INSTAGRAM_API_URL, token);
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
    public String getAccountId(UUID organizationId) throws Exception {
        UUID orgId = organizationId;
        Optional<SocialMediaCredential> cred = credentialService.getCredential(orgId, SocialMediaCredential.Platform.INSTAGRAM);

        if (cred.isEmpty()) {
            throw new IllegalStateException("Instagram account ID not configured");
        }

        return cred.get().getPageId();
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

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        List<String> normalized = new ArrayList<>();
        for (String url : imageUrls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            normalized.add(normalizeImageUrl(url));
        }
        return normalized;
    }

    private String normalizeImageUrl(String url) {
        if (mediaUseSignedUrls) {
            return signSupabaseUrl(url);
        }

        String publicBase = normalizeBase(mediaPublicBaseUrl);
        if (publicBase.isEmpty()) {
            return url;
        }

        String originBase = normalizeBase(mediaOriginBaseUrl);
        if (!originBase.isEmpty() && url.startsWith(originBase)) {
            return publicBase + url.substring(originBase.length());
        }

        return url;
    }

    private String normalizeBase(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String signSupabaseUrl(String url) {
        String resolvedSupabaseUrl = supabaseUrl != null ? supabaseUrl.trim() : "";
        String resolvedKey = supabaseKey != null ? supabaseKey.trim() : "";
        if (resolvedSupabaseUrl.isEmpty() || resolvedKey.isEmpty()) {
            throw new IllegalStateException("Supabase credentials are required to generate signed URLs for Instagram");
        }

        String originBase = normalizeBase(mediaOriginBaseUrl);
        if (originBase.isEmpty()) {
            originBase = normalizeBase(resolvedSupabaseUrl + "/storage/v1/object/public");
        }

        if (!url.startsWith(originBase)) {
            return url;
        }

        String objectPath = url.substring(originBase.length());
        if (objectPath.startsWith("/")) {
            objectPath = objectPath.substring(1);
        }

        int slashIndex = objectPath.indexOf('/');
        if (slashIndex <= 0 || slashIndex == objectPath.length() - 1) {
            throw new IllegalStateException("Unable to parse Supabase bucket/path from URL");
        }

        String bucket = objectPath.substring(0, slashIndex);
        String path = objectPath.substring(slashIndex + 1);

        String signUrl = resolvedSupabaseUrl + "/storage/v1/object/sign/" + bucket + "/" + path;
        Map<String, Object> payload = new HashMap<>();
        payload.put("expiresIn", Math.max(60, mediaSignedUrlTtlSeconds));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(resolvedKey);
        headers.set("apikey", resolvedKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                signUrl,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Failed to generate signed URL for Instagram");
        }

        Object signedUrlValue = response.getBody().get("signedURL");
        if (signedUrlValue == null) {
            signedUrlValue = response.getBody().get("signedUrl");
        }
        if (signedUrlValue == null) {
            throw new IllegalStateException("Signed URL response missing signedURL field");
        }

        String signedUrl = String.valueOf(signedUrlValue);
        if (signedUrl.startsWith("http")) {
            return signedUrl;
        }

        return resolvedSupabaseUrl + signedUrl;
    }

    /**
     * Get token and account ID, preferring System User configuration over OAuth tokens.
     * This supports both personal OAuth flow and Meta Business Portfolio System Users.
     */
    private TokenInfo getTokenAndAccountId(UUID organizationId) {
        TokenInfo tokenInfo = new TokenInfo();

        // Check if System User is enabled and configured
        if (systemUserEnabled && systemUserToken != null && !systemUserToken.isBlank() 
                && systemUserAccountId != null && !systemUserAccountId.isBlank()) {
            logger.info("Using Instagram System User token (Business Portfolio)");
            tokenInfo.token = systemUserToken.trim();
            tokenInfo.accountId = systemUserAccountId.trim();
            tokenInfo.tokenType = "SYSTEM_USER";
            return tokenInfo;
        }

        // Fallback to OAuth token from database
        logger.info("Using Instagram OAuth token from database");
        Optional<String> token = credentialService.getDecryptedToken(organizationId, SocialMediaCredential.Platform.INSTAGRAM);
        Optional<String> accountId = credentialService.getDecryptedPageId(organizationId, SocialMediaCredential.Platform.INSTAGRAM);

        tokenInfo.token = token.orElse(null);
        tokenInfo.accountId = accountId.orElse(null);
        tokenInfo.tokenType = "USER_TOKEN";

        return tokenInfo;
    }

    /**
     * Helper class to hold token and account ID together
     */
    private static class TokenInfo {
        String token;
        String accountId;
        String tokenType;
    }
}
