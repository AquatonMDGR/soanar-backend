-- Migration: Add token_type column for System User support
-- Date: 2026-02-21
-- Purpose: Support both OAuth user tokens and Meta Business System User tokens

-- Add token_type column (defaults to USER_TOKEN for backward compatibility)
ALTER TABLE social_media_credentials 
ADD COLUMN IF NOT EXISTS token_type VARCHAR(50) NOT NULL DEFAULT 'USER_TOKEN';

-- Add index for faster queries
CREATE INDEX IF NOT EXISTS idx_social_media_cred_token_type 
ON social_media_credentials(organization_id, platform, token_type, is_active);

-- Add check constraint
ALTER TABLE social_media_credentials 
ADD CONSTRAINT chk_token_type 
CHECK (token_type IN ('USER_TOKEN', 'SYSTEM_USER_TOKEN'));

-- Comments
COMMENT ON COLUMN social_media_credentials.token_type IS 'USER_TOKEN for OAuth flow, SYSTEM_USER_TOKEN for Meta Business Portfolio';
