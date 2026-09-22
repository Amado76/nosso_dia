CREATE TABLE nosso_dia.external_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES nosso_dia.users(id),
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('GOOGLE', 'APPLE')),
    provider_subject VARCHAR(255) NOT NULL CHECK (length(provider_subject) > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (provider, provider_subject)
);
CREATE INDEX external_identities_user_idx ON nosso_dia.external_identities(user_id);
