-- Migration: Deprecate Crossposting Tables
-- Date: 2026-02-21
-- Purpose: Add deprecation comments to social media tables after removing automated crossposting
--
-- Background:
-- Automated crossposting to Facebook/Instagram has been removed in favor of manual sharing via AddToAny.
-- These tables are kept for historical reference only and should not be used going forward.

-- Add deprecation comment to social_media_credentials table
COMMENT ON TABLE social_media_credentials IS 
  'DEPRECATED (Feb 2026): Automated crossposting removed. Table kept for historical data only. Do not use for new features.';

-- Add deprecation comment to social_media_posts table
COMMENT ON TABLE social_media_posts IS 
  'DEPRECATED (Feb 2026): Automated crossposting removed. Table kept for historical data only. Do not use for new features.';

-- Add comments to key columns explaining the deprecation
COMMENT ON COLUMN social_media_credentials.platform IS 
  'DEPRECATED: Previously used for Facebook/Instagram OAuth credentials';

COMMENT ON COLUMN social_media_credentials.access_token IS 
  'DEPRECATED: Access tokens are no longer used after crossposting removal';

COMMENT ON COLUMN social_media_posts.platform IS 
  'DEPRECATED: Historical record of cross-posted platforms (facebook/instagram)';

COMMENT ON COLUMN social_media_posts.status IS 
  'DEPRECATED: Historical posting status (pending/success/failed)';

-- Log the deprecation in audit logs (if audit_logs table exists)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'audit_logs') THEN
    INSERT INTO audit_logs (
      action_type,
      entity_type,
      entity_id,
      description,
      timestamp
    ) VALUES (
      'SYSTEM_MIGRATION',
      'DATABASE_SCHEMA',
      NULL,
      'Migration 20260221000002: Deprecated social_media_credentials and social_media_posts tables. Automated crossposting feature removed - switched to AddToAny manual sharing.',
      NOW()
    );
  END IF;
END $$;
