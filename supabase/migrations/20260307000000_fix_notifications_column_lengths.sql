-- Fix legacy notifications column lengths that can break long announcement inserts.
-- Safe to run multiple times.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'notifications'
          AND column_name = 'message'
    ) THEN
        ALTER TABLE notifications
            ALTER COLUMN message TYPE TEXT;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'notifications'
          AND column_name = 'title'
    ) THEN
        ALTER TABLE notifications
            ALTER COLUMN title TYPE TEXT;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'notifications'
          AND column_name = 'action_url'
    ) THEN
        ALTER TABLE notifications
            ALTER COLUMN action_url TYPE TEXT;
    END IF;
END $$;
