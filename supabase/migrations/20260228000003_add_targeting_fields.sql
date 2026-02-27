-- Add targeting fields for thesis recipient targeting

ALTER TABLE users
ADD COLUMN IF NOT EXISTS year_level VARCHAR(50),
ADD COLUMN IF NOT EXISTS school VARCHAR(100);

ALTER TABLE announcements
ADD COLUMN IF NOT EXISTS target_year_levels JSONB NOT NULL DEFAULT '[]'::jsonb,
ADD COLUMN IF NOT EXISTS target_schools JSONB NOT NULL DEFAULT '[]'::jsonb,
ADD COLUMN IF NOT EXISTS target_manual_emails JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_users_year_level ON users(year_level);
CREATE INDEX IF NOT EXISTS idx_users_school ON users(school);
