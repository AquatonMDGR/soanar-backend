-- Add soft delete support for announcements
ALTER TABLE announcements
  ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- Backfill null safety for legacy rows
UPDATE announcements
SET is_deleted = FALSE
WHERE is_deleted IS NULL;

CREATE INDEX IF NOT EXISTS idx_announcements_is_deleted
  ON announcements (is_deleted);
