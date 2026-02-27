package com.soanar.dto;

import java.util.ArrayList;
import java.util.List;

public class TargetingRequest {
    private List<String> yearLevels = new ArrayList<>();
    private List<String> schools = new ArrayList<>();
    private List<Long> distributionGroupIds = new ArrayList<>();
    private List<String> manualEmails = new ArrayList<>();

    public List<String> getYearLevels() {
        return yearLevels;
    }

    public void setYearLevels(List<String> yearLevels) {
        this.yearLevels = yearLevels != null ? yearLevels : new ArrayList<>();
    }

    public List<String> getSchools() {
        return schools;
    }

    public void setSchools(List<String> schools) {
        this.schools = schools != null ? schools : new ArrayList<>();
    }

    public List<Long> getDistributionGroupIds() {
        return distributionGroupIds;
    }

    public void setDistributionGroupIds(List<Long> distributionGroupIds) {
        this.distributionGroupIds = distributionGroupIds != null ? distributionGroupIds : new ArrayList<>();
    }

    public List<String> getManualEmails() {
        return manualEmails;
    }

    public void setManualEmails(List<String> manualEmails) {
        this.manualEmails = manualEmails != null ? manualEmails : new ArrayList<>();
    }

    public boolean hasAnyFilter() {
        return (yearLevels != null && !yearLevels.isEmpty())
                || (schools != null && !schools.isEmpty())
                || (distributionGroupIds != null && !distributionGroupIds.isEmpty())
                || (manualEmails != null && !manualEmails.isEmpty());
    }
}
