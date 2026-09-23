CREATE TABLE beehome.daily_executions (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES beehome.families(id),
    family_member_id UUID NOT NULL,
    execution_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'FINALIZED')),
    reopened BOOLEAN NOT NULL DEFAULT FALSE,
    note VARCHAR(2000),
    finalized_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (family_id, family_member_id) REFERENCES beehome.family_members(family_id, id),
    UNIQUE (family_id, family_member_id, execution_date),
    CHECK ((status = 'OPEN' AND finalized_at IS NULL) OR (status = 'FINALIZED' AND finalized_at IS NOT NULL AND NOT reopened))
);
CREATE INDEX daily_executions_history ON beehome.daily_executions(family_id, family_member_id, execution_date DESC, id DESC);
CREATE TABLE beehome.daily_execution_items (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES beehome.daily_executions(id),
    source_type VARCHAR(16) NOT NULL CHECK (source_type IN ('ROUTINE', 'DAILY_PLAN')),
    source_id UUID,
    title VARCHAR(120) NOT NULL CHECK (title ~ '[^[:space:]]'),
    description VARCHAR(2000),
    scheduled_time TIME CHECK (scheduled_time IS NULL OR EXTRACT(SECOND FROM scheduled_time) = 0),
    sort_order INTEGER NOT NULL CHECK (sort_order >= 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    completed_at TIMESTAMPTZ,
    completed_by_user_id UUID REFERENCES beehome.users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (execution_id, source_type, source_id),
    CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL) OR
           (status <> 'COMPLETED' AND completed_at IS NULL AND completed_by_user_id IS NULL))
);
CREATE INDEX daily_execution_items_order ON beehome.daily_execution_items(execution_id, sort_order, id);
