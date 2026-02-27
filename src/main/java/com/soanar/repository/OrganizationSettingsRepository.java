package com.soanar.repository;

import com.soanar.model.OrganizationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, Long> {
    Optional<OrganizationSettings> findByOrganizationId(String organizationId);
    Optional<OrganizationSettings> findTopByOrderByIdAsc();
}
