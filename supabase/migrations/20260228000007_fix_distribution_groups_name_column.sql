-- Fix distribution_groups name column mismatch
-- Root issue: JPA expects column `name`, but some DBs still have legacy `group_name`.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'distribution_groups'
    ) THEN
        -- If `name` is missing but legacy `group_name` exists, rename it.
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'distribution_groups'
              AND column_name = 'name'
        )
        AND EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'distribution_groups'
              AND column_name = 'group_name'
        ) THEN
            ALTER TABLE distribution_groups RENAME COLUMN group_name TO name;
        END IF;

        -- If still missing, add it.
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'distribution_groups'
              AND column_name = 'name'
        ) THEN
            ALTER TABLE distribution_groups ADD COLUMN name VARCHAR(255);
        END IF;

        -- Ensure no null names before NOT NULL.
        UPDATE distribution_groups
        SET name = COALESCE(NULLIF(TRIM(name), ''), 'Unnamed Group')
        WHERE name IS NULL OR TRIM(name) = '';

        -- Enforce NOT NULL for model compatibility.
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'distribution_groups'
              AND column_name = 'name'
              AND is_nullable = 'YES'
        ) THEN
            ALTER TABLE distribution_groups ALTER COLUMN name SET NOT NULL;
        END IF;
    END IF;
END $$;
