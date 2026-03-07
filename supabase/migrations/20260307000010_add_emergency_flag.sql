-- Add emergency announcement flag for OSAS/Academic urgent broadcasts
ALTER TABLE announcements
ADD COLUMN IF NOT EXISTS is_emergency BOOLEAN NOT NULL DEFAULT FALSE;
