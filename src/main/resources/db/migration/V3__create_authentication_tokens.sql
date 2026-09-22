CREATE TABLE nosso_dia.refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES nosso_dia.users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replaced_by UUID REFERENCES nosso_dia.refresh_tokens(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (expires_at > created_at),
    CHECK (replaced_by IS NULL OR revoked_at IS NOT NULL)
);
CREATE INDEX refresh_tokens_user_idx ON nosso_dia.refresh_tokens(user_id);

CREATE TABLE nosso_dia.password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES nosso_dia.users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (expires_at > created_at)
);
CREATE INDEX password_reset_tokens_user_created_idx ON nosso_dia.password_reset_tokens(user_id, created_at);
