package com.soanar.service.impl;

import com.soanar.model.SocialMediaCredential;
import com.soanar.repository.SocialMediaCredentialRepository;
import com.soanar.service.CredentialService;
import com.soanar.service.EncryptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of credential management service
 */
@Service
@Transactional
public class CredentialServiceImpl implements CredentialService {

    private static final Logger logger = LoggerFactory.getLogger(CredentialServiceImpl.class);

    private final SocialMediaCredentialRepository credentialRepository;
    private final EncryptionService encryptionService;

    public CredentialServiceImpl(SocialMediaCredentialRepository credentialRepository,
                               EncryptionService encryptionService) {
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
    }

    @Override
    public SocialMediaCredential storeCredential(UUID organizationId,
                                               SocialMediaCredential.Platform platform,
                                               String pageId,
                                               String accessToken,
                                               long expirationSeconds) {
        logger.info("Storing credentials for {} on {}", organizationId, platform);

        // Check if credential already exists
        Optional<SocialMediaCredential> existing =
                credentialRepository.findByOrganizationIdAndPlatform(organizationId, platform);

        SocialMediaCredential credential;
        if (existing.isPresent()) {
            credential = existing.get();
            logger.info("Updating existing credential for {}", platform);
        } else {
            credential = new SocialMediaCredential(organizationId, platform, "", "");
            logger.info("Creating new credential for {}", platform);
        }

        // Encrypt sensitive fields
        credential.setPageId(encryptionService.encrypt(pageId.trim()));
        credential.setAccessToken(encryptionService.encrypt(accessToken.trim()));
        credential.setIsActive(true);

        // Set expiration time
        if (expirationSeconds > 0) {
            Instant expiresAt = Instant.now().plusSeconds(expirationSeconds);
            credential.setTokenExpiresAt(expiresAt);
        }

        credential.setUpdatedAt(Instant.now());
        credentialRepository.save(credential);

        logger.info("Credentials stored successfully for {}", platform);
        return credential;
    }

    @Override
    public Optional<SocialMediaCredential> getCredential(UUID organizationId,
                                                        SocialMediaCredential.Platform platform) {
        logger.debug("Fetching credential for {} on {}", organizationId, platform);
        return credentialRepository.findByOrganizationIdAndPlatformAndIsActiveTrue(organizationId, platform);
    }

    @Override
    public Optional<String> getDecryptedToken(UUID organizationId,
                                             SocialMediaCredential.Platform platform) {
        logger.debug("Fetching decrypted token for {} on {}", organizationId, platform);

        Optional<SocialMediaCredential> credential = getCredential(organizationId, platform);
        if (credential.isEmpty()) {
            logger.warn("No credential found for {}", platform);
            return Optional.empty();
        }

        SocialMediaCredential cred = credential.get();

        // Check if token is expired
        if (cred.getTokenExpiresAt() != null && cred.getTokenExpiresAt().isBefore(Instant.now())) {
            logger.warn("Token expired for {}", platform);
            return Optional.empty();
        }

        try {
            String decrypted = encryptionService.decrypt(cred.getAccessToken());
            return Optional.of(decrypted);
        } catch (Exception e) {
            logger.error("Failed to decrypt token for {}", platform, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getDecryptedPageId(UUID organizationId,
                                              SocialMediaCredential.Platform platform) {
        logger.debug("Fetching decrypted page ID for {} on {}", organizationId, platform);

        Optional<SocialMediaCredential> credential = getCredential(organizationId, platform);
        if (credential.isEmpty()) {
            logger.warn("No credential found for {}", platform);
            return Optional.empty();
        }

        SocialMediaCredential cred = credential.get();

        try {
            String decrypted = encryptionService.decrypt(cred.getPageId());
            return Optional.of(decrypted);
        } catch (Exception e) {
            logger.error("Failed to decrypt page ID for {}", platform, e);
            return Optional.empty();
        }
    }

    @Override
    public void refreshToken(UUID organizationId,
                            SocialMediaCredential.Platform platform,
                            String newToken,
                            long expirationSeconds) {
        logger.info("Refreshing token for {} on {}", organizationId, platform);

        Optional<SocialMediaCredential> existing =
                credentialRepository.findByOrganizationIdAndPlatform(organizationId, platform);

        if (existing.isEmpty()) {
            logger.warn("No credential found to refresh for {}", platform);
            return;
        }

        SocialMediaCredential credential = existing.get();
        credential.setAccessToken(encryptionService.encrypt(newToken.trim()));

        if (expirationSeconds > 0) {
            Instant expiresAt = Instant.now().plusSeconds(expirationSeconds);
            credential.setTokenExpiresAt(expiresAt);
        }

        credential.setUpdatedAt(Instant.now());
        credentialRepository.save(credential);

        logger.info("Token refreshed successfully for {}", platform);
    }

    @Override
    public void revokeCredential(UUID organizationId, SocialMediaCredential.Platform platform) {
        logger.info("Revoking credential for {} on {}", organizationId, platform);

        Optional<SocialMediaCredential> credential =
                credentialRepository.findByOrganizationIdAndPlatform(organizationId, platform);

        if (credential.isPresent()) {
            SocialMediaCredential cred = credential.get();
            cred.setIsActive(false);
            cred.setUpdatedAt(Instant.now());
            credentialRepository.save(cred);
            logger.info("Credential revoked for {}", platform);
        }
    }

    @Override
    public boolean isConnected(UUID organizationId, SocialMediaCredential.Platform platform) {
        boolean connected = credentialRepository.existsByOrganizationIdAndPlatformAndIsActiveTrue(organizationId, platform);
        logger.debug("Connection status for {}: {}", platform, connected);
        return connected;
    }

    @Override
    public boolean isTokenValid(UUID organizationId, SocialMediaCredential.Platform platform) {
        Optional<SocialMediaCredential> credential = getCredential(organizationId, platform);

        if (credential.isEmpty()) {
            logger.debug("Token invalid for {} - no credential found", platform);
            return false;
        }

        SocialMediaCredential cred = credential.get();

        if (cred.getTokenExpiresAt() != null && cred.getTokenExpiresAt().isBefore(Instant.now())) {
            logger.debug("Token invalid for {} - expired", platform);
            return false;
        }

        logger.debug("Token valid for {}", platform);
        return true;
    }
}
