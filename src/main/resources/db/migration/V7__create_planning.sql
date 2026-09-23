ALTER TABLE nosso_dia.families ADD COLUMN timezone VARCHAR(255);
UPDATE nosso_dia.families SET timezone = 'UTC';
ALTER TABLE nosso_dia.families ALTER COLUMN timezone SET NOT NULL;
ALTER TABLE nosso_dia.families ADD CONSTRAINT families_timezone_nonblank CHECK (timezone ~ '[^[:space:]]');

ALTER TABLE nosso_dia.family_members ADD CONSTRAINT family_members_family_id_unique UNIQUE (family_id, id);

CREATE TABLE nosso_dia.routines (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES nosso_dia.families(id),
    name VARCHAR(120) NOT NULL CHECK (name ~ '[^[:space:]]'),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    start_date DATE,
    end_date DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (family_id, id),
    CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);
CREATE INDEX routines_family_created ON nosso_dia.routines(family_id, created_at, id);
CREATE INDEX routines_active_created ON nosso_dia.routines(family_id, created_at, id) WHERE active = TRUE;

CREATE TABLE nosso_dia.routine_days (
    routine_id UUID NOT NULL REFERENCES nosso_dia.routines(id),
    day_of_week VARCHAR(9) NOT NULL CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    PRIMARY KEY (routine_id, day_of_week)
);
-- Deferred validation permits replacing the weekday set within one transaction.
CREATE FUNCTION nosso_dia.require_routine_days() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE target_id UUID;
BEGIN
    IF TG_TABLE_NAME = 'routines' THEN target_id := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN target_id := OLD.routine_id;
    ELSE target_id := OLD.routine_id;
    END IF;
    IF EXISTS (SELECT 1 FROM nosso_dia.routines WHERE id = target_id)
       AND NOT EXISTS (SELECT 1 FROM nosso_dia.routine_days WHERE routine_id = target_id) THEN
        RAISE EXCEPTION 'A routine requires weekdays' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER routines_require_days AFTER INSERT ON nosso_dia.routines
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nosso_dia.require_routine_days();
CREATE CONSTRAINT TRIGGER routine_days_nonempty AFTER DELETE OR UPDATE ON nosso_dia.routine_days
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nosso_dia.require_routine_days();

CREATE TABLE nosso_dia.routine_items (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    routine_id UUID NOT NULL,
    family_member_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL CHECK (title ~ '[^[:space:]]'),
    description VARCHAR(2000),
    scheduled_time TIME,
    sort_order INTEGER NOT NULL CHECK (sort_order >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (family_id, routine_id) REFERENCES nosso_dia.routines(family_id, id),
    FOREIGN KEY (family_id, family_member_id) REFERENCES nosso_dia.family_members(family_id, id),
    CHECK (scheduled_time IS NULL OR EXTRACT(SECOND FROM scheduled_time) = 0)
);
CREATE INDEX routine_items_routine_order ON nosso_dia.routine_items(routine_id, sort_order, id);
CREATE INDEX routine_items_family_member ON nosso_dia.routine_items(family_id, family_member_id, routine_id) WHERE active = TRUE;

CREATE TABLE nosso_dia.daily_plans (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    family_member_id UUID NOT NULL,
    plan_date DATE NOT NULL,
    note VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (family_id, family_member_id) REFERENCES nosso_dia.family_members(family_id, id),
    UNIQUE (family_id, family_member_id, plan_date)
);
CREATE TABLE nosso_dia.daily_plan_items (
    id UUID PRIMARY KEY,
    daily_plan_id UUID NOT NULL REFERENCES nosso_dia.daily_plans(id),
    title VARCHAR(120) NOT NULL CHECK (title ~ '[^[:space:]]'),
    description VARCHAR(2000),
    scheduled_time TIME,
    sort_order INTEGER NOT NULL CHECK (sort_order >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT daily_plan_items_order_unique UNIQUE (daily_plan_id, sort_order) DEFERRABLE INITIALLY DEFERRED,
    CHECK (scheduled_time IS NULL OR EXTRACT(SECOND FROM scheduled_time) = 0)
);
