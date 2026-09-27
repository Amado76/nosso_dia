ALTER TABLE beehome.refresh_tokens ADD COLUMN session_id UUID;

WITH RECURSIVE session_chain AS (
    SELECT id, replaced_by, id AS session_id
    FROM beehome.refresh_tokens
    WHERE id NOT IN (
        SELECT replaced_by FROM beehome.refresh_tokens WHERE replaced_by IS NOT NULL
    )
    UNION ALL
    SELECT next.id, next.replaced_by, chain.session_id
    FROM beehome.refresh_tokens next
    JOIN session_chain chain ON next.id = chain.replaced_by
)
UPDATE beehome.refresh_tokens token
SET session_id = chain.session_id
FROM session_chain chain
WHERE token.id = chain.id;

ALTER TABLE beehome.refresh_tokens ALTER COLUMN session_id SET NOT NULL;
CREATE INDEX refresh_tokens_active_session_idx
    ON beehome.refresh_tokens(user_id, session_id, expires_at)
    WHERE revoked_at IS NULL;
