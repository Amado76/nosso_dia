CREATE TABLE nosso_dia.family_members (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES nosso_dia.families(id),
    name VARCHAR(120) NOT NULL CHECK (name ~ '[^[:space:]]'),
    member_type VARCHAR(10) NOT NULL CHECK (member_type IN ('ADULT', 'CHILD')),
    birth_date DATE,
    color VARCHAR(7) CHECK (color ~ '^#[0-9A-F]{6}$'),
    avatar_reference VARCHAR(255),
    linked_user_id UUID,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT family_members_family_user_unique UNIQUE (family_id, linked_user_id),
    CONSTRAINT family_members_membership_fk FOREIGN KEY (family_id, linked_user_id)
        REFERENCES nosso_dia.family_memberships(family_id, user_id)
);

-- Preserve creation order with and without the active-only filter; type is a residual
-- filter because families contain a small number of people.
CREATE INDEX family_members_family_created ON nosso_dia.family_members(family_id, created_at, id);
CREATE INDEX family_members_active_created ON nosso_dia.family_members(family_id, created_at, id)
    WHERE active = TRUE;
