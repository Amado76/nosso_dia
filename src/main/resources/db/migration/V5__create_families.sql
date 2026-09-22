CREATE TABLE nosso_dia.families (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE nosso_dia.family_memberships (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES nosso_dia.families(id),
    user_id UUID NOT NULL REFERENCES nosso_dia.users(id),
    role VARCHAR(10) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT family_memberships_family_user_unique UNIQUE (family_id, user_id)
);

CREATE UNIQUE INDEX family_memberships_one_owner ON nosso_dia.family_memberships(family_id)
    WHERE role = 'OWNER';
CREATE INDEX family_memberships_user_family ON nosso_dia.family_memberships(user_id, family_id);
