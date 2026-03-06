-- Persist Google profile photos for user avatars across sessions/devices
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS photo_url TEXT;

ALTER TABLE announcements
  ADD COLUMN IF NOT EXISTS poster_photo_snapshot TEXT;

UPDATE announcements a
SET poster_photo_snapshot = u.photo_url
FROM users u
WHERE a.posted_by = u.id
  AND a.poster_photo_snapshot IS NULL
  AND u.photo_url IS NOT NULL
  AND btrim(u.photo_url) <> '';
