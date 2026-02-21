-- Phase 5 & 6 Database Migration Script

-- =============================================
-- Phase 5: Organization Settings
-- =============================================

CREATE TABLE IF NOT EXISTS organization_settings (
    id BIGSERIAL PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    logo_url TEXT,
    description TEXT,
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    website_url TEXT,
    facebook_enabled BOOLEAN DEFAULT FALSE,
    instagram_enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- =============================================
-- Phase 5: Audit Logs
-- =============================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    organization_id VARCHAR(255),
    action_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    old_value TEXT,
    new_value TEXT,
    description TEXT,
    ip_address VARCHAR(100),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_logs(timestamp);

-- =============================================
-- Phase 6: Notification Enhancements
-- =============================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'notification_logs') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'notifications') THEN
            ALTER TABLE notification_logs RENAME TO notifications;
        END IF;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'notifications' AND column_name = 'user_id') THEN
        ALTER TABLE notifications ADD COLUMN user_id BIGINT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'notifications' AND column_name = 'body') THEN
        ALTER TABLE notifications ADD COLUMN body TEXT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'notifications' AND column_name = 'action_url') THEN
        ALTER TABLE notifications ADD COLUMN action_url TEXT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'notifications' AND column_name = 'entity_type') THEN
        ALTER TABLE notifications ADD COLUMN entity_type VARCHAR(50);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'notifications' AND column_name = 'entity_id') THEN
        ALTER TABLE notifications ADD COLUMN entity_id BIGINT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_notifications_user'
    ) THEN
        ALTER TABLE notifications ADD CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_notification_user ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_recipient ON notifications(recipient_email);
CREATE INDEX IF NOT EXISTS idx_notification_read ON notifications(read_at);
CREATE INDEX IF NOT EXISTS idx_notification_created ON notifications(created_at);

-- =============================================
-- Phase 6: Notification Preferences
-- =============================================

CREATE TABLE IF NOT EXISTS notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    organization_id VARCHAR(255) NOT NULL,
    notify_by_email BOOLEAN NOT NULL DEFAULT TRUE,
    notify_in_app BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_approval BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_rejection BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_publish BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_comment BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_mention BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE(user_id, organization_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_notif_pref_user ON notification_preferences(user_id);
CREATE INDEX IF NOT EXISTS idx_notif_pref_org ON notification_preferences(organization_id);
