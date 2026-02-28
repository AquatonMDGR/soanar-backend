-- Ensure distribution group tables match current application model
-- Safe to run multiple times.

CREATE TABLE IF NOT EXISTS distribution_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    CONSTRAINT fk_distribution_groups_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS distribution_group_members (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    user_id BIGINT,
    student_email VARCHAR(255) NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_distribution_group_members_group
        FOREIGN KEY (group_id) REFERENCES distribution_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_distribution_group_members_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'distribution_groups') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_groups' AND column_name = 'id') THEN
            IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_groups' AND column_name = 'group_id') THEN
                ALTER TABLE distribution_groups RENAME COLUMN group_id TO id;
            ELSIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_groups' AND column_name = 'distribution_group_id') THEN
                ALTER TABLE distribution_groups RENAME COLUMN distribution_group_id TO id;
            ELSE
                ALTER TABLE distribution_groups ADD COLUMN id BIGINT;
            END IF;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_groups' AND column_name = 'id')
           AND NOT EXISTS (
               SELECT 1 FROM information_schema.columns
               WHERE table_name = 'distribution_groups'
                 AND column_name = 'id'
                 AND is_nullable = 'NO'
           )
           AND NOT EXISTS (
               SELECT 1 FROM distribution_groups WHERE id IS NULL
           ) THEN
            ALTER TABLE distribution_groups ALTER COLUMN id SET NOT NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name = kcu.constraint_name
             AND tc.table_schema = kcu.table_schema
            WHERE tc.table_name = 'distribution_groups'
              AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
              AND kcu.column_name = 'id'
        ) THEN
            ALTER TABLE distribution_groups
                ADD CONSTRAINT uq_distribution_groups_id UNIQUE (id);
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_groups' AND column_name = 'organization_id') THEN
            ALTER TABLE distribution_groups ADD COLUMN organization_id VARCHAR(255);
            UPDATE distribution_groups SET organization_id = 'default-org' WHERE organization_id IS NULL;
            ALTER TABLE distribution_groups ALTER COLUMN organization_id SET NOT NULL;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_groups' AND column_name = 'description') THEN
            ALTER TABLE distribution_groups ADD COLUMN description TEXT;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_groups' AND column_name = 'created_at') THEN
            ALTER TABLE distribution_groups ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_groups' AND column_name = 'created_by') THEN
            ALTER TABLE distribution_groups ADD COLUMN created_by BIGINT;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE table_name = 'distribution_groups'
              AND constraint_name = 'fk_distribution_groups_created_by'
        ) THEN
            ALTER TABLE distribution_groups
                ADD CONSTRAINT fk_distribution_groups_created_by
                FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;
        END IF;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'distribution_group_members') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'group_id') THEN
            IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'distribution_group_id') THEN
                ALTER TABLE distribution_group_members RENAME COLUMN distribution_group_id TO group_id;
            ELSIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'groupid') THEN
                ALTER TABLE distribution_group_members RENAME COLUMN groupid TO group_id;
            ELSE
                ALTER TABLE distribution_group_members ADD COLUMN group_id BIGINT;
            END IF;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'student_email') THEN
            IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'email') THEN
                ALTER TABLE distribution_group_members RENAME COLUMN email TO student_email;
            ELSIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'member_email') THEN
                ALTER TABLE distribution_group_members RENAME COLUMN member_email TO student_email;
            ELSIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'school_email') THEN
                ALTER TABLE distribution_group_members RENAME COLUMN school_email TO student_email;
            ELSIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'studentEmail') THEN
                ALTER TABLE distribution_group_members RENAME COLUMN "studentEmail" TO student_email;
            ELSE
                ALTER TABLE distribution_group_members ADD COLUMN student_email VARCHAR(255);
            END IF;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'user_id') THEN
            ALTER TABLE distribution_group_members ADD COLUMN user_id BIGINT;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'added_at') THEN
            ALTER TABLE distribution_group_members ADD COLUMN added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE table_name = 'distribution_group_members'
              AND constraint_name = 'fk_distribution_group_members_group'
        ) THEN
            ALTER TABLE distribution_group_members
                ADD CONSTRAINT fk_distribution_group_members_group
                FOREIGN KEY (group_id) REFERENCES distribution_groups(id) ON DELETE CASCADE;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'group_id')
           AND NOT EXISTS (
               SELECT 1 FROM information_schema.columns
               WHERE table_name = 'distribution_group_members'
                 AND column_name = 'group_id'
                 AND is_nullable = 'NO'
           )
           AND NOT EXISTS (
               SELECT 1 FROM distribution_group_members WHERE group_id IS NULL
           ) THEN
            ALTER TABLE distribution_group_members ALTER COLUMN group_id SET NOT NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE table_name = 'distribution_group_members'
              AND constraint_name = 'fk_distribution_group_members_user'
        ) THEN
            ALTER TABLE distribution_group_members
                ADD CONSTRAINT fk_distribution_group_members_user
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'student_email')
           AND NOT EXISTS (
               SELECT 1 FROM information_schema.columns
               WHERE table_name = 'distribution_group_members'
                 AND column_name = 'student_email'
                 AND is_nullable = 'NO'
           )
           AND NOT EXISTS (
               SELECT 1 FROM distribution_group_members WHERE student_email IS NULL
           ) THEN
            ALTER TABLE distribution_group_members ALTER COLUMN student_email SET NOT NULL;
        END IF;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_distribution_groups_org ON distribution_groups(organization_id);
CREATE INDEX IF NOT EXISTS idx_distribution_group_members_group ON distribution_group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_distribution_group_members_email ON distribution_group_members(student_email);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'group_id')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'student_email') THEN
        DELETE FROM distribution_group_members dgm
        USING distribution_group_members dup
        WHERE dgm.id > dup.id
          AND dgm.group_id = dup.group_id
          AND dgm.student_email = dup.student_email;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_distribution_group_member_email'
    )
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'group_id')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'distribution_group_members' AND column_name = 'student_email') THEN
        ALTER TABLE distribution_group_members
            ADD CONSTRAINT uq_distribution_group_member_email UNIQUE (group_id, student_email);
    END IF;
END $$;
