-- Migration: Add social media crossposting tables
-- Date: 2026-01-31
-- Description: Creates tables for storing social media credentials and tracking crossposted announcements
--
-- EXECUTION INSTRUCTIONS:
-- 1. Open Supabase Dashboard: https://supabase.com/dashboard
-- 2. Select your project
-- 3. Go to SQL Editor (left sidebar)
-- 4. Click "New query"
-- 5. Copy this entire file
-- 6. Paste into SQL Editor
-- 7. Click "Run" (or Ctrl+Enter)
-- 8. Verify success: "Success. No rows returned"
--
-- This migration creates:
-- - social_media_credentials (encrypted OAuth tokens)
-- - social_media_posts (crosspost tracking with engagement metrics)
-- - Indexes, constraints, triggers

-- ============================================
-- 1. social_media_credentials table
-- ============================================
-- Purpose: Stores organization-scoped encrypted credentials for Facebook/Instagram
-- Used by: OAuthCallbackController, CredentialService, FacebookService, InstagramService
CREATE TABLE IF NOT EXISTS social_media_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    platform VARCHAR(50) NOT NULL CHECK (platform IN ('FACEBOOK', 'INSTAGRAM')),
    page_id VARCHAR(500) NOT NULL, -- Encrypted page ID
    access_token VARCHAR(2000) NOT NULL, -- Encrypted OAuth token
    token_expires_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Ensure one credential per org per platform
    UNIQUE (organization_id, platform)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_credentials_org_platform 
    ON social_media_credentials(organization_id, platform);

CREATE INDEX IF NOT EXISTS idx_credentials_active 
    ON social_media_credentials(organization_id, platform, is_active) 
    WHERE is_active = true;

-- Comments
COMMENT ON TABLE social_media_credentials IS 'Stores encrypted OAuth tokens for Facebook/Instagram crossposting';
COMMENT ON COLUMN social_media_credentials.organization_id IS 'Organization owning this credential (default: 00000000-0000-0000-0000-000000000001)';
COMMENT ON COLUMN social_media_credentials.page_id IS 'Encrypted Facebook Page ID or Instagram Business Account ID';
COMMENT ON COLUMN social_media_credentials.access_token IS 'Encrypted OAuth access token from Meta';


-- ============================================
-- 2. social_media_posts table
-- ============================================
-- Purpose: Tracks all crossposted announcements with status, engagement metrics, and errors
-- Used by: CrosspostService, FacebookService, InstagramService
CREATE TABLE IF NOT EXISTS social_media_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    announcement_id BIGINT NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL CHECK (platform IN ('FACEBOOK', 'INSTAGRAM')),
    post_id VARCHAR(500), -- Platform's post ID (e.g., "123456789_987654321")
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SCHEDULED', 'POSTED', 'FAILED', 'DELETED')),
    posted_at TIMESTAMPTZ,
    scheduled_for TIMESTAMPTZ, -- For future scheduled posting feature
    error_message TEXT, -- Error details if posting failed
    custom_caption TEXT, -- Platform-specific caption override
    engagement_data JSONB, -- {likes, comments, shares, reach, impressions, clicks}
    last_sync_at TIMESTAMPTZ, -- Last time engagement metrics were synced
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_posts_announcement 
    ON social_media_posts(announcement_id);

CREATE INDEX IF NOT EXISTS idx_posts_platform 
    ON social_media_posts(platform);

CREATE INDEX IF NOT EXISTS idx_posts_status 
    ON social_media_posts(status);

CREATE INDEX IF NOT EXISTS idx_posts_scheduled 
    ON social_media_posts(scheduled_for) 
    WHERE status = 'SCHEDULED';

CREATE INDEX IF NOT EXISTS idx_posts_sync 
    ON social_media_posts(last_sync_at) 
    WHERE status = 'POSTED';

-- GIN index for JSONB engagement data queries
CREATE INDEX IF NOT EXISTS idx_posts_engagement 
    ON social_media_posts USING gin(engagement_data);

-- Comments
COMMENT ON TABLE social_media_posts IS 'Tracks crossposted announcements with status and engagement metrics';
COMMENT ON COLUMN social_media_posts.announcement_id IS 'References the original announcement';
COMMENT ON COLUMN social_media_posts.post_id IS 'Platform-specific post ID returned after successful posting';
COMMENT ON COLUMN social_media_posts.status IS 'Current status: PENDING (queued), SCHEDULED (future), POSTED (live), FAILED (error), DELETED (removed)';
COMMENT ON COLUMN social_media_posts.engagement_data IS 'JSONB with likes, comments, shares, reach, impressions, clicks';
COMMENT ON COLUMN social_media_posts.last_sync_at IS 'Last time engagement metrics were fetched from platform API';


-- ============================================
-- 3. Add trigger for updated_at timestamps
-- ============================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_social_media_credentials_updated_at 
    BEFORE UPDATE ON social_media_credentials
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_social_media_posts_updated_at 
    BEFORE UPDATE ON social_media_posts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- ============================================
-- 4. Sample data (optional, for testing)
-- ============================================
-- Insert default organization credential placeholder (will be replaced by OAuth flow)
-- Uncomment if you want a placeholder row for testing
/*
INSERT INTO social_media_credentials (organization_id, platform, page_id, access_token, is_active)
VALUES 
    ('00000000-0000-0000-0000-000000000001', 'FACEBOOK', 'placeholder_encrypted_page_id', 'placeholder_encrypted_token', false),
    ('00000000-0000-0000-0000-000000000001', 'INSTAGRAM', 'placeholder_encrypted_page_id', 'placeholder_encrypted_token', false)
ON CONFLICT (organization_id, platform) DO NOTHING;
*/


-- ============================================
-- 5. Row Level Security (RLS) - Optional
-- ============================================
-- Enable RLS if using Supabase with authenticated users
-- ALTER TABLE social_media_credentials ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE social_media_posts ENABLE ROW LEVEL SECURITY;

-- Policy examples (customize based on your auth setup):
/*
CREATE POLICY "Allow backend service full access" 
    ON social_media_credentials FOR ALL 
    USING (true);

CREATE POLICY "Allow backend service full access" 
    ON social_media_posts FOR ALL 
    USING (true);
*/


-- ============================================
-- 6. Verification queries
-- ============================================
-- Run these to verify the migration:
/*
-- Check tables exist
SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE 'social_media%';

-- Check indexes
SELECT indexname, tablename FROM pg_indexes WHERE schemaname = 'public' AND tablename LIKE 'social_media%';

-- Check constraints
SELECT conname, contype FROM pg_constraint WHERE conrelid IN (
    SELECT oid FROM pg_class WHERE relname LIKE 'social_media%'
);
*/
