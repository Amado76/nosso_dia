-- No media FK: the queue entry must survive removal of its metadata row.
CREATE TABLE beehome.pending_media_deletions (
    media_id UUID PRIMARY KEY,
    storage_key VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX pending_media_deletions_due ON beehome.pending_media_deletions(next_attempt_at, media_id);
