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

            if ("facebook".equalsIgnoreCase(provider)) {
                tokenUrl = "https://graph.facebook.com/v18.0/oauth/access_token";
                clientId = facebookClientId;
                clientSecret = facebookClientSecret;
            } else if ("instagram".equalsIgnoreCase(provider)) {
                tokenUrl = "https://graph.instagram.com/v18.0/oauth/access_token";
                clientId = instagramClientId;
                clientSecret = instagramClientSecret;
            } else {
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
                response.put("page_id", pageData.getOrDefault("page_id", ""));
                String pageAccessToken = pageData.getOrDefault("page_access_token", "");
                if (!pageAccessToken.isBlank()) {
                    response.put("access_token", pageAccessToken);
                }
            }

            // For Instagram, use returned user_id as page_id when present
            if ("instagram".equalsIgnoreCase(provider) && !response.containsKey("page_id")) {
                Object userId = response.get("user_id");
                if (userId != null) {
                    response.put("page_id", String.valueOf(userId));
                }
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
            String url = String.format("https://graph.facebook.com/v18.0/me/accounts?access_token=%s", userAccessToken);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                if (!data.isEmpty()) {
                    Map<String, Object> page = data.get(0);
                    result.put("page_id", String.valueOf(page.getOrDefault("id", "")));
                    result.put("page_access_token", String.valueOf(page.getOrDefault("access_token", "")));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch Facebook page data", e);
        }
        return result;
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
