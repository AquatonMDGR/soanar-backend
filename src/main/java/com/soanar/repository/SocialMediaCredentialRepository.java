package com.soanar.repository;

import com.soanar.model.SocialMediaCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialMediaCredentialRepository extends JpaRepository<SocialMediaCredential, UUID> {

    /**
     * Find credentials by organization and platform
     */
    Optional<SocialMediaCredential> findByOrganizationIdAndPlatform(UUID organizationId, SocialMediaCredential.Platform platform);

    /**
     * Check if platform is connected for organization
     */
    boolean existsByOrganizationIdAndPlatformAndIsActiveTrue(UUID organizationId, SocialMediaCredential.Platform platform);

    /**
     * Find active credentials by organization
     */
    Optional<SocialMediaCredential> findByOrganizationIdAndPlatformAndIsActiveTrue(UUID organizationId, SocialMediaCredential.Platform platform);
}
