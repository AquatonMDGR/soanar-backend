-- Migration: Create social_media_credentials table
-- Version: V001
-- Created: 2026-01-31

-- Create social_media_credentials table for storing encrypted platform credentials
CREATE TABLE IF NOT EXISTS social_media_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    platform VARCHAR(50) NOT NULL,
    page_id VARCHAR(500) NOT NULL,
    access_token VARCHAR(2000) NOT NULL,
    token_expires_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, platform)
);

-- Create index for fast lookups
CREATE INDEX idx_social_media_cred_org_platform ON social_media_credentials(organization_id, platform);
CREATE INDEX idx_social_media_cred_platform_active ON social_media_credentials(platform, is_active);

-- Create social_media_posts table for tracking announcement posts
CREATE TABLE IF NOT EXISTS social_media_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    announcement_id BIGINT NOT NULL,
    platform VARCHAR(50) NOT NULL,
    post_id VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    posted_at TIMESTAMP,
    scheduled_for TIMESTAMP,
    error_message TEXT,
    custom_caption TEXT,
    engagement_data JSONB,
    last_sync_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE
);

-- Create indexes for social_media_posts
CREATE INDEX idx_social_media_posts_announcement ON social_media_posts(announcement_id);
CREATE INDEX idx_social_media_posts_platform_status ON social_media_posts(platform, status);
CREATE INDEX idx_social_media_posts_status_scheduled ON social_media_posts(status, scheduled_for);
CREATE INDEX idx_social_media_posts_created_at ON social_media_posts(created_at);

-- Verify tables were created
SELECT 'social_media_credentials table created' as status;
SELECT 'social_media_posts table created' as status;
