package com.soanar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for crossposting announcements
 */
public class CrosspostRequest {

    @JsonProperty("facebook")
    private PlatformConfig facebook;

    @JsonProperty("instagram")
    private PlatformConfig instagram;

    public CrosspostRequest() {}

    public CrosspostRequest(PlatformConfig facebook, PlatformConfig instagram) {
        this.facebook = facebook;
        this.instagram = instagram;
    }

    public PlatformConfig getFacebook() { return facebook; }
    public void setFacebook(PlatformConfig facebook) { this.facebook = facebook; }

    public PlatformConfig getInstagram() { return instagram; }
    public void setInstagram(PlatformConfig instagram) { this.instagram = instagram; }

    /**
     * Configuration for a specific platform
     */
    public static class PlatformConfig {

        @JsonProperty("enabled")
        private Boolean enabled = false;

        @JsonProperty("caption")
        private String caption;

        @JsonProperty("scheduledFor")
        private String scheduledFor; // ISO 8601 datetime string

        public PlatformConfig() {}

        public PlatformConfig(Boolean enabled, String caption) {
            this.enabled = enabled;
            this.caption = caption;
        }

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public String getCaption() { return caption; }
        public void setCaption(String caption) { this.caption = caption; }

        public String getScheduledFor() { return scheduledFor; }
        public void setScheduledFor(String scheduledFor) { this.scheduledFor = scheduledFor; }
    }
}
