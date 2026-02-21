package com.soanar.service;

import com.soanar.model.SocialMediaCredential;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing encrypted social media credentials
 */
public interface CredentialService {

    /**
     * Store credentials for a platform (OAuth user token)
     */
    SocialMediaCredential storeCredential(UUID organizationId, SocialMediaCredential.Platform platform,
                                         String pageId, String accessToken, long expirationSeconds);

    /**
     * Store System User credentials for a platform (Meta Business Portfolio)
     */
    SocialMediaCredential storeSystemUserCredential(UUID organizationId, SocialMediaCredential.Platform platform,
                                                   String pageId, String accessToken);

    /**
     * Get active credentials for a platform
     */
    Optional<SocialMediaCredential> getCredential(UUID organizationId, SocialMediaCredential.Platform platform);

    /**
     * Get decrypted access token for API calls
     */
    Optional<String> getDecryptedToken(UUID organizationId, SocialMediaCredential.Platform platform);

    /**
     * Get decrypted page ID for API calls
     */
    Optional<String> getDecryptedPageId(UUID organizationId, SocialMediaCredential.Platform platform);

    /**
     * Refresh expired token
     */
    void refreshToken(UUID organizationId, SocialMediaCredential.Platform platform, String newToken, long expirationSeconds);

    /**
     * Revoke access for a platform
     */
    void revokeCredential(UUID organizationId, SocialMediaCredential.Platform platform);

    /**
     * Check if platform is connected and active
     */
    boolean isConnected(UUID organizationId, SocialMediaCredential.Platform platform);

    /**
     * Validate if token is still valid (not expired)
     */
    boolean isTokenValid(UUID organizationId, SocialMediaCredential.Platform platform);
}
