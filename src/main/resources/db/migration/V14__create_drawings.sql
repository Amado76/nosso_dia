CREATE TABLE beehome.drawings (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    member_id UUID NOT NULL,
    surface VARCHAR(40) NOT NULL CHECK (surface = 'PROFILE_SCRATCHPAD'),
    format_version INTEGER NOT NULL CHECK (format_version = 1),
    document JSONB NOT NULL CHECK (jsonb_typeof(document) = 'object'),
    revision BIGINT NOT NULL CHECK (revision >= 1),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT drawings_family_fk FOREIGN KEY (family_id) REFERENCES beehome.families(id),
    CONSTRAINT drawings_member_family_fk FOREIGN KEY (family_id, member_id)
        REFERENCES beehome.family_members(family_id, id),
    CONSTRAINT drawings_member_surface_unique UNIQUE (family_id, member_id, surface)
);

CREATE INDEX drawings_member_surface_lookup ON beehome.drawings(member_id, surface);
