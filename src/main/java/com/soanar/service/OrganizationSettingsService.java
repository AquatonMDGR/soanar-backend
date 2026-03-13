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
    public OrganizationSettings getOrCreateDefaultSettings() {
        String organizationId = resolveDefaultOrganizationId();
        return settingsRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    OrganizationSettings settings = new OrganizationSettings();
                    settings.setOrganizationId(organizationId);
                    settings.setName("SOANAR");
                    settings.setCreatedAt(Instant.now());
                    return settingsRepository.save(settings);
                });
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
            existingSettings.setTerm1StartMonthDay(settings.getTerm1StartMonthDay());
            existingSettings.setTerm1EndMonthDay(settings.getTerm1EndMonthDay());
            existingSettings.setTerm2StartMonthDay(settings.getTerm2StartMonthDay());
            existingSettings.setTerm2EndMonthDay(settings.getTerm2EndMonthDay());
            existingSettings.setTerm3StartMonthDay(settings.getTerm3StartMonthDay());
            existingSettings.setTerm3EndMonthDay(settings.getTerm3EndMonthDay());
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
        if (updates.getTerm1StartMonthDay() != null) settings.setTerm1StartMonthDay(updates.getTerm1StartMonthDay());
        if (updates.getTerm1EndMonthDay() != null) settings.setTerm1EndMonthDay(updates.getTerm1EndMonthDay());
        if (updates.getTerm2StartMonthDay() != null) settings.setTerm2StartMonthDay(updates.getTerm2StartMonthDay());
        if (updates.getTerm2EndMonthDay() != null) settings.setTerm2EndMonthDay(updates.getTerm2EndMonthDay());
        if (updates.getTerm3StartMonthDay() != null) settings.setTerm3StartMonthDay(updates.getTerm3StartMonthDay());
        if (updates.getTerm3EndMonthDay() != null) settings.setTerm3EndMonthDay(updates.getTerm3EndMonthDay());
        
        settings.setUpdatedAt(Instant.now());
        return settingsRepository.save(settings);
    }
}
