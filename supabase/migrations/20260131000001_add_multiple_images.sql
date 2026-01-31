-- Add support for multiple images per announcement
-- This migration adds a JSONB column to store an array of image URLs

-- Add new column for storing multiple image URLs
ALTER TABLE announcements 
ADD COLUMN image_urls JSONB DEFAULT '[]'::jsonb;

-- Migrate existing data: copy imageUrl to imageUrls array if imageUrl exists
UPDATE announcements 
SET image_urls = CASE 
    WHEN image_url IS NOT NULL AND image_url != '' 
    THEN jsonb_build_array(image_url)
    ELSE '[]'::jsonb
END;

-- Create index on image_urls for efficient querying
CREATE INDEX idx_announcements_image_urls 
ON announcements USING gin(image_urls);

-- Add constraint to ensure imageUrl remains single for backward compatibility
-- (This column will continue to store the first image for queries expecting a single URL)

COMMENT ON COLUMN announcements.image_urls IS 'JSONB array of image URLs for announcements supporting multiple images';
