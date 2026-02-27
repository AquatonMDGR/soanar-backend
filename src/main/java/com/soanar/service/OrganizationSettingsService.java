package com.soanar.service;

import com.soanar.model.OrganizationSettings;
import com.soanar.repository.OrganizationSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class OrganizationSettingsService {

    private static final String DEFAULT_ORGANIZATION_ID = "default-org";

    @Autowired
    private OrganizationSettingsRepository settingsRepository;

    public Optional<OrganizationSettings> getSettingsByOrganizationId(String organizationId) {
        return settingsRepository.findByOrganizationId(organizationId);
    }

    public String resolveDefaultOrganizationId() {
        return settingsRepository.findTopByOrderByIdAsc()
            .map(OrganizationSettings::getOrganizationId)
            .orElse(DEFAULT_ORGANIZATION_ID);
    }

    @Transactional
    public OrganizationSettings createOrUpdateSettings(OrganizationSettings settings) {
        Optional<OrganizationSettings> existing = settingsRepository.findByOrganizationId(settings.getOrganizationId());
        
        if (existing.isPresent()) {
            OrganizationSettings existingSettings = existing.get();
            existingSettings.setName(settings.getName());
            existingSettings.setLogoUrl(settings.getLogoUrl());
            existingSettings.setDescription(settings.getDescription());
            existingSettings.setContactEmail(settings.getContactEmail());
            existingSettings.setContactPhone(settings.getContactPhone());
            existingSettings.setWebsiteUrl(settings.getWebsiteUrl());
            existingSettings.setFacebookEnabled(settings.getFacebookEnabled());
            existingSettings.setInstagramEnabled(settings.getInstagramEnabled());
            existingSettings.setUpdatedAt(Instant.now());
            return settingsRepository.save(existingSettings);
        } else {
            settings.setCreatedAt(Instant.now());
            return settingsRepository.save(settings);
        }
    }

    @Transactional
    public OrganizationSettings updateSettings(String organizationId, OrganizationSettings updates) {
        OrganizationSettings settings = settingsRepository.findByOrganizationId(organizationId)
            .orElseThrow(() -> new RuntimeException("Organization settings not found"));
        
        if (updates.getName() != null) settings.setName(updates.getName());
        if (updates.getLogoUrl() != null) settings.setLogoUrl(updates.getLogoUrl());
        if (updates.getDescription() != null) settings.setDescription(updates.getDescription());
        if (updates.getContactEmail() != null) settings.setContactEmail(updates.getContactEmail());
        if (updates.getContactPhone() != null) settings.setContactPhone(updates.getContactPhone());
        if (updates.getWebsiteUrl() != null) settings.setWebsiteUrl(updates.getWebsiteUrl());
        if (updates.getFacebookEnabled() != null) settings.setFacebookEnabled(updates.getFacebookEnabled());
        if (updates.getInstagramEnabled() != null) settings.setInstagramEnabled(updates.getInstagramEnabled());
        
        settings.setUpdatedAt(Instant.now());
        return settingsRepository.save(settings);
    }
}
