-- Migration: Create notification_logs table
-- Date: 2026-02-14
-- Description: Creates table for storing user notifications

-- ============================================
-- notification_logs table
-- ============================================
-- Purpose: Tracks notifications sent to users for announcements
-- Used by: NotificationService, NotificationController

CREATE TABLE IF NOT EXISTS notification_logs (
    id BIGSERIAL PRIMARY KEY,
    announcement_id BIGINT NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    recipient_email VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at TIMESTAMPTZ,
    type VARCHAR(50),  -- 'announcement', 'approval', 'rejection'
    title VARCHAR(500),
    message TEXT
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_notification_recipient_email 
    ON notification_logs(recipient_email);

CREATE INDEX IF NOT EXISTS idx_notification_announcement_id 
    ON notification_logs(announcement_id);

CREATE INDEX IF NOT EXISTS idx_notification_created_at 
    ON notification_logs(created_at);

CREATE INDEX IF NOT EXISTS idx_notification_read_at 
    ON notification_logs(read_at);

-- Comments
COMMENT ON TABLE notification_logs IS 'Stores notifications sent to users for announcements and approvals';
COMMENT ON COLUMN notification_logs.recipient_email IS 'Email address of the notification recipient';
COMMENT ON COLUMN notification_logs.announcement_id IS 'References the announcement that triggered the notification';
COMMENT ON COLUMN notification_logs.read_at IS 'When the notification was read by the user (null if unread)';
