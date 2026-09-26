CREATE TABLE beehome.extracurricular_activities (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES beehome.families(id),
    name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
    normalized_name VARCHAR(120) NOT NULL CHECK (length(normalized_name) > 0),
    description VARCHAR(2000),
    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT extracurricular_activities_family_name_unique UNIQUE (family_id, normalized_name),
    CONSTRAINT extracurricular_activities_id_family_unique UNIQUE (id, family_id)
);
CREATE INDEX extracurricular_activities_list ON beehome.extracurricular_activities(family_id, sort_order, id);

CREATE TABLE beehome.extracurricular_records (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    child_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    record_date DATE NOT NULL,
    topic VARCHAR(120),
    duration_minutes INTEGER CHECK (duration_minutes > 0),
    description VARCHAR(10000),
    comments VARCHAR(10000),
    material VARCHAR(1000),
    start_page INTEGER CHECK (start_page > 0),
    end_page INTEGER CHECK (end_page > 0),
    created_by_user_id UUID NOT NULL REFERENCES beehome.users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT extracurricular_records_pages CHECK (start_page IS NULL OR end_page IS NULL OR end_page >= start_page),
    CONSTRAINT extracurricular_records_child_fk FOREIGN KEY (family_id, child_id) REFERENCES beehome.family_members(family_id, id),
    CONSTRAINT extracurricular_records_activity_fk FOREIGN KEY (activity_id, family_id) REFERENCES beehome.extracurricular_activities(id, family_id),
    CONSTRAINT extracurricular_records_id_family_unique UNIQUE (id, family_id)
);
CREATE INDEX extracurricular_records_history ON beehome.extracurricular_records(family_id, child_id, record_date DESC, id DESC);
CREATE INDEX extracurricular_records_summary ON beehome.extracurricular_records(family_id, child_id, activity_id, record_date);

CREATE TABLE beehome.extracurricular_record_tags (
    record_id UUID NOT NULL,
    family_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    PRIMARY KEY (record_id, tag_id),
    CONSTRAINT extracurricular_record_tags_record_fk FOREIGN KEY (record_id, family_id) REFERENCES beehome.extracurricular_records(id, family_id) ON DELETE CASCADE,
    CONSTRAINT extracurricular_record_tags_tag_fk FOREIGN KEY (family_id, tag_id) REFERENCES beehome.tags(family_id, id)
);
CREATE INDEX extracurricular_record_tags_tag_record ON beehome.extracurricular_record_tags(tag_id, record_id);
