package com.soanar.controller;

import com.soanar.model.SocialMediaCredential;
import com.soanar.repository.UserRepository;
import com.soanar.service.CredentialService;
import com.soanar.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.UUID;

/**
 * Handles OAuth2 callbacks from Facebook and Instagram
 * Exchanges authorization codes for access tokens and stores them
 */
@RestController
@RequestMapping("/api/auth/oauth")
@CrossOrigin(origins = "*")
public class OAuthCallbackController {

    private static final Logger logger = LoggerFactory.getLogger(OAuthCallbackController.class);
    
    @Value("${facebook.client-id:}")
    private String facebookClientId;

    @Value("${facebook.client-secret:}")
    private String facebookClientSecret;

    @Value("${instagram.client-id:}")
    private String instagramClientId;

    @Value("${instagram.client-secret:}")
    private String instagramClientSecret;

    @Value("${app.oauth.redirect-uri:http://localhost:8080/api/auth/oauth/callback}")
    private String redirectUri;

    private final CredentialService credentialService;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;

    public OAuthCallbackController(CredentialService credentialService, 
                                   JwtUtil jwtUtil,
                                   RestTemplate restTemplate,
                                   UserRepository userRepository) {
        this.credentialService = credentialService;
        this.jwtUtil = jwtUtil;
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
    }

    /**
     * Handle OAuth2 callback from Facebook or Instagram
     * GET/POST /api/auth/oauth/callback?provider=facebook&code=...&state=...
     */
    @RequestMapping(value = "/callback", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> handleOAuthCallback(
            @RequestParam(required = false) String provider,
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        try {
            ResolvedState resolved = resolveState(provider, state);
            String resolvedProvider = resolved.provider;
            if (resolvedProvider == null || resolvedProvider.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing provider"));
            }

            // Get user email from JWT (Authorization header preferred; fallback to state token)
            String token = authHeader != null ? authHeader.replace("Bearer ", "") : null;
            if ((token == null || token.isBlank()) && resolved.token != null) {
                token = resolved.token;
            }
            if (token == null || token.isBlank()) {
                return ResponseEntity.status(401).body(Map.of("error", "Missing user token"));
            }

            String emailFromToken = jwtUtil.extractEmail(token);
            String tokenRole = jwtUtil.extractRole(token);
            var user = userRepository.findBySchoolEmail(emailFromToken)
                    .orElseThrow(() -> new RuntimeException("User not found: " + emailFromToken));
            String effectiveRole = isAuthorizedRole(tokenRole) ? tokenRole : user.getRole();
            if (!isAuthorizedRole(effectiveRole)) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized to connect social media"));
            }

            // User-scoped organization ID (derived from email to avoid hardcoding)
            UUID organizationId = resolveOrganizationId(emailFromToken);
            logger.info("Processing OAuth callback for user {} and provider: {}", emailFromToken, resolvedProvider);

            // Exchange code for access token
            Map<String, Object> tokenResponse = exchangeCodeForToken(resolvedProvider, code);

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                logger.error("Failed to obtain access token from {}", provider);
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to exchange code for token"));
            }

            String accessToken = (String) tokenResponse.get("access_token");
            String pageId = (String) tokenResponse.getOrDefault("page_id", "");
            Long expiresIn = parseExpiresIn(tokenResponse.get("expires_in"), 5184000L); // 60 days default

            if ("facebook".equalsIgnoreCase(resolvedProvider) && (pageId == null || pageId.isBlank())) {
                logger.warn("Facebook connect succeeded but no page ID found. Check Pages permissions and Page admin role.");
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Facebook Page not found. Ensure pages_show_list is granted and you manage at least one Page."
                ));
            }

            // Store credential
            SocialMediaCredential.Platform platformEnum = 
                    SocialMediaCredential.Platform.valueOf(resolvedProvider.toUpperCase());

            credentialService.storeCredential(
                    organizationId,
                    platformEnum,
                    pageId,
                    accessToken,
                    expiresIn
            );

                logger.info("Successfully stored credential for organization {} on {}", organizationId, resolvedProvider);
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully connected to " + resolvedProvider,
                    "platform", resolvedProvider,
                    "pageId", pageId
            ));

        } catch (IllegalArgumentException e) {
            logger.error("Invalid platform: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid platform"));
        } catch (Exception e) {
            logger.error("Error processing OAuth callback", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process callback: " + e.getMessage()));
        }
    }

    /**
     * Exchange authorization code for access token
     */
    private Map<String, Object> exchangeCodeForToken(String provider, String code) {
        try {
            String tokenUrl;
            String clientId;
            String clientSecret;

            // Facebook and Instagram use the SAME App ID (unified Meta OAuth)
            tokenUrl = "https://graph.facebook.com/v19.0/oauth/access_token";
            clientId = facebookClientId;
            clientSecret = facebookClientSecret;

            if (!("facebook".equalsIgnoreCase(provider) || "instagram".equalsIgnoreCase(provider))) {
                throw new IllegalArgumentException("Unsupported provider: " + provider);
            }

            // Build request body
            Map<String, String> body = new LinkedHashMap<>();
            body.put("client_id", clientId);
            body.put("client_secret", clientSecret);
            body.put("grant_type", "authorization_code");
            body.put("redirect_uri", redirectUri);
            body.put("code", code);

            logger.info("Exchanging code for token from {}: {}", provider, tokenUrl);

            // Exchange code
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(tokenUrl, body, Map.class);

            if (response == null) {
                logger.error("Null response from {} token endpoint", provider);
                return null;
            }

            if (response.containsKey("error")) {
                logger.error("Error from {}: {}", provider, response.get("error_description"));
                return null;
            }

            // For Facebook, also fetch page ID and page access token
            if ("facebook".equalsIgnoreCase(provider)) {
                String userAccessToken = (String) response.get("access_token");
                Map<String, String> pageData = fetchFacebookPrimaryPage(userAccessToken);
                String pageId = pageData.getOrDefault("page_id", "");
                if (pageId == null || pageId.isBlank()) {
                    logger.warn("Facebook page ID fetch returned empty. This means /me/accounts returned no pages. Check if you have pages_show_list scope approved and are an admin of at least one page.");
                }
                response.put("page_id", pageId);
                String pageAccessToken = pageData.getOrDefault("page_access_token", "");
                if (!pageAccessToken.isBlank()) {
                    response.put("access_token", pageAccessToken);
                }
            }

            // For Instagram, fetch Instagram Business Account ID
            if ("instagram".equalsIgnoreCase(provider)) {
                String userAccessToken = (String) response.get("access_token");
                String igAccountId = fetchInstagramBusinessAccount(userAccessToken);
                
                if (igAccountId == null || igAccountId.isBlank()) {
                    logger.error("Failed to retrieve Instagram Business Account ID. Cannot complete Instagram OAuth.");
                    return null; // This will trigger error response in main handler
                }
                
                response.put("page_id", igAccountId);
                logger.info("Instagram Business Account ID retrieved: {}", igAccountId);
            }

            return response;

        } catch (Exception e) {
            logger.error("Failed to exchange code for token", e);
            return null;
        }
    }

    /**
     * Fetch Facebook page ID from access token
     */
    private Map<String, String> fetchFacebookPrimaryPage(String userAccessToken) {
        Map<String, String> result = new HashMap<>();
        try {
                String url = String.format(
                    "https://graph.facebook.com/v19.0/me/accounts?fields=id,access_token,name&access_token=%s",
                    userAccessToken
                );
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            logger.info("Facebook /me/accounts response: {}", response);

            if (response != null && response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                logger.info("Facebook pages list size: {}", data.size());
                if (!data.isEmpty()) {
                    Map<String, Object> page = data.get(0);
                    result.put("page_id", String.valueOf(page.getOrDefault("id", "")));
                    result.put("page_access_token", String.valueOf(page.getOrDefault("access_token", "")));
                    logger.info("Captured Facebook page_id: {}", result.get("page_id"));
                }
            } else {
                logger.warn("No 'data' key in response or response is null");
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch Facebook page data", e);
        }
        return result;
    }

    /**
     * Fetch Instagram Business Account ID from user access token
     * Tries two approaches:
     * 1. Get from /me?fields=instagram_business_account (direct link)
     * 2. Get from user's primary Facebook Page (page link)
     */
    private String fetchInstagramBusinessAccount(String userAccessToken) {
        // Approach 1: Try to get from user directly
        try {
            String url = String.format(
                    "https://graph.facebook.com/v19.0/me?fields=instagram_business_account&access_token=%s",
                    userAccessToken
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            logger.info("Response from /me?fields=instagram_business_account: {}", response);

            if (response != null && response.containsKey("instagram_business_account")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> igAccount = (Map<String, Object>) response.get("instagram_business_account");
                String accountId = (String) igAccount.get("id");
                logger.info("Instagram Business Account from /me: {}", igAccount);
                if (accountId != null && !accountId.isBlank()) {
                    logger.info("Successfully fetched Instagram Business Account from /me: {}", accountId);
                    return accountId;
                }
            }
            logger.warn("No Instagram Business Account found on user profile. Response: {}", response);
        } catch (Exception e) {
            logger.warn("Failed to fetch Instagram Business Account from /me", e);
        }

        // Approach 2: Get from primary Facebook Page
        try {
            logger.info("Attempting to fetch Instagram Business Account from user's primary Facebook Page");
            String pageUrl = String.format(
                    "https://graph.facebook.com/v19.0/me/accounts?fields=id,access_token,name,instagram_business_account&access_token=%s",
                    userAccessToken
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> pageResponse = restTemplate.getForObject(pageUrl, Map.class);
            logger.info("Response from /me/accounts: {}", pageResponse);

            if (pageResponse != null && pageResponse.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) pageResponse.get("data");
                if (!data.isEmpty()) {
                    Map<String, Object> primaryPage = data.get(0);
                    logger.info("Primary page data: {}", primaryPage);
                    
                    // Check if page has instagram_business_account field
                    if (primaryPage.containsKey("instagram_business_account")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> igAccount = (Map<String, Object>) primaryPage.get("instagram_business_account");
                        logger.info("Instagram Business Account from Page (embedded): {}", igAccount);
                        String accountId = (String) igAccount.get("id");
                        if (accountId != null && !accountId.isBlank()) {
                            logger.info("Successfully fetched Instagram Business Account from Page: {}", accountId);
                            return accountId;
                        }
                    }
                    
                    // If page has the account but not embedded, fetch it separately
                    String pageId = (String) primaryPage.get("id");
                    String pageAccessToken = (String) primaryPage.get("access_token");
                    if (pageId != null && pageAccessToken != null) {
                        logger.info("Fetching Instagram Business Account details from page {}", pageId);
                        String pageDetailsUrl = String.format(
                                "https://graph.facebook.com/v19.0/%s?fields=instagram_business_account&access_token=%s",
                                pageId, pageAccessToken
                        );
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pageDetailsResponse = restTemplate.getForObject(pageDetailsUrl, Map.class);
                        logger.info("Page details response: {}", pageDetailsResponse);
                        
                        if (pageDetailsResponse != null && pageDetailsResponse.containsKey("instagram_business_account")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> igAccount = (Map<String, Object>) pageDetailsResponse.get("instagram_business_account");
                            logger.info("Instagram Business Account from Page details: {}", igAccount);
                            String accountId = (String) igAccount.get("id");
                            if (accountId != null && !accountId.isBlank()) {
                                logger.info("Successfully fetched Instagram Business Account from Page details: {}", accountId);
                                return accountId;
                            }
                        }
                    }
                }
            }
            logger.warn("No Instagram Business Account found on user's Facebook Pages. Response: {}", pageResponse);
        } catch (Exception e) {
            logger.warn("Failed to fetch Instagram Business Account from Pages", e);
        }

        logger.error("CRITICAL: Could not fetch Instagram Business Account ID. Possible causes:\n" +
                "1. User is not an admin of any Facebook pages\n" +
                "2. pages_show_list or pages_manage_posts scopes not approved in Meta App\n" +
                "3. No Facebook pages exist for this user\n" +
                "4. Instagram Business Account not linked to any Facebook page\n" +
                "5. User needs to re-authorize with auth_type=rerequest");
        return null;
    }

    /**
     * Get connection status for a platform
     * GET /api/auth/oauth/status/{platform}
     */
    @GetMapping("/status/{platform}")
    public ResponseEntity<?> getConnectionStatus(
            @PathVariable String platform,
            @RequestHeader("Authorization") String authHeader) {

        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(token);
            String tokenRole = jwtUtil.extractRole(token);
            var user = userRepository.findBySchoolEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));
            String effectiveRole = isAuthorizedRole(tokenRole) ? tokenRole : user.getRole();
            if (!isAuthorizedRole(effectiveRole)) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized to view social connections"));
            }

            UUID organizationId = resolveOrganizationId(email);

            SocialMediaCredential.Platform platformEnum = 
                    SocialMediaCredential.Platform.valueOf(platform.toUpperCase());

            Optional<SocialMediaCredential> credential = credentialService.getCredential(organizationId, platformEnum);

            return ResponseEntity.ok(Map.of(
                    "platform", platform,
                    "connected", credential.isPresent(),
                    "pageId", credential.map(SocialMediaCredential::getPageId).orElse("")
            ));

        } catch (Exception e) {
            logger.error("Error getting connection status", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Disconnect social media account
     * DELETE /api/auth/oauth/disconnect/{platform}
     */
    @DeleteMapping("/disconnect/{platform}")
    public ResponseEntity<?> disconnect(
            @PathVariable String platform,
            @RequestHeader("Authorization") String authHeader) {

        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(token);
            String tokenRole = jwtUtil.extractRole(token);
            var user = userRepository.findBySchoolEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));
            String effectiveRole = isAuthorizedRole(tokenRole) ? tokenRole : user.getRole();
            if (!isAuthorizedRole(effectiveRole)) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized to disconnect social media"));
            }

            UUID organizationId = resolveOrganizationId(email);

            SocialMediaCredential.Platform platformEnum = 
                    SocialMediaCredential.Platform.valueOf(platform.toUpperCase());

            credentialService.revokeCredential(organizationId, platformEnum);

            logger.info("Disconnected {} for organization {}", platform, organizationId);
            return ResponseEntity.ok(Map.of("message", "Disconnected from " + platform));

        } catch (Exception e) {
            logger.error("Error disconnecting social account", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private UUID resolveOrganizationId(String email) {
        return UUID.nameUUIDFromBytes(email.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private Long parseExpiresIn(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean isAuthorizedRole(String role) {
        if (role == null) {
            return false;
        }
        String normalized = role.trim();
        return "Student Organization".equalsIgnoreCase(normalized)
                || "OSAS".equalsIgnoreCase(normalized)
                || "Academic".equalsIgnoreCase(normalized);
    }

    private ResolvedState resolveState(String provider, String state) {
        ResolvedState resolved = new ResolvedState();
        if (provider != null && !provider.isBlank()) {
            resolved.provider = provider;
        }
        if (state != null && !state.isBlank()) {
            if (state.contains("|")) {
                String[] parts = state.split("\\|", 2);
                if (resolved.provider == null || resolved.provider.isBlank()) {
                    resolved.provider = parts[0];
                }
                if (parts.length > 1 && !parts[1].isBlank()) {
                    resolved.token = parts[1];
                }
            } else if (resolved.provider == null || resolved.provider.isBlank()) {
                resolved.provider = state;
            }
        }
        return resolved;
    }

    private static class ResolvedState {
        private String provider;
        private String token;
    }
}
