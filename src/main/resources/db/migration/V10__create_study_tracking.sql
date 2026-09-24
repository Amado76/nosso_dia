CREATE TABLE beehome.study_subjects (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES beehome.families(id),
    name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
    normalized_name VARCHAR(120) NOT NULL,
    color VARCHAR(7) CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL CHECK (sort_order >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT study_subjects_family_name_unique UNIQUE (family_id, normalized_name),
    CONSTRAINT study_subjects_family_id_unique UNIQUE (family_id, id)
);
CREATE INDEX study_subjects_order ON beehome.study_subjects(family_id, active, sort_order, id);

CREATE TABLE beehome.study_sessions (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES beehome.families(id),
    family_member_id UUID NOT NULL,
    subject_id UUID,
    daily_execution_item_id UUID REFERENCES beehome.daily_execution_items(id),
    subject_name_snapshot VARCHAR(120),
    session_date DATE NOT NULL,
    title VARCHAR(120),
    notes TEXT CHECK (notes IS NULL OR length(notes) <= 10000),
    entry_mode VARCHAR(10) NOT NULL CHECK (entry_mode IN ('TIMER', 'MANUAL')),
    status VARCHAR(12) NOT NULL CHECK (status IN ('RUNNING', 'PAUSED', 'COMPLETED', 'VOIDED')),
    started_at TIMESTAMPTZ,
    current_run_started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    accumulated_duration_seconds INTEGER NOT NULL CHECK (accumulated_duration_seconds BETWEEN 0 AND 31536000),
    created_by_user_id UUID NOT NULL REFERENCES beehome.users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT study_sessions_family_member_fk FOREIGN KEY (family_id, family_member_id) REFERENCES beehome.family_members(family_id, id),
    CONSTRAINT study_sessions_family_subject_fk FOREIGN KEY (family_id, subject_id) REFERENCES beehome.study_subjects(family_id, id),
    CHECK ((entry_mode = 'MANUAL' AND status IN ('COMPLETED', 'VOIDED') AND started_at IS NULL AND current_run_started_at IS NULL AND ended_at IS NULL)
        OR (entry_mode = 'TIMER' AND started_at IS NOT NULL AND
            ((status = 'RUNNING' AND current_run_started_at IS NOT NULL AND ended_at IS NULL)
            OR (status = 'PAUSED' AND current_run_started_at IS NULL AND ended_at IS NULL)
            OR (status IN ('COMPLETED', 'VOIDED') AND current_run_started_at IS NULL AND ended_at IS NOT NULL))))
);
CREATE UNIQUE INDEX study_sessions_one_running ON beehome.study_sessions(family_member_id) WHERE status = 'RUNNING';
CREATE INDEX study_sessions_history ON beehome.study_sessions(family_id, family_member_id, session_date DESC, started_at DESC, id DESC);
CREATE INDEX study_sessions_subject_history ON beehome.study_sessions(family_id, family_member_id, subject_id, session_date DESC);
CREATE INDEX study_sessions_execution_item ON beehome.study_sessions(daily_execution_item_id);
