CREATE TABLE beehome.tags (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES beehome.families(id),
    name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
    normalized_name VARCHAR(120) NOT NULL,
    color VARCHAR(7) CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT tags_family_name_unique UNIQUE (family_id, normalized_name),
    CONSTRAINT tags_family_id_unique UNIQUE (family_id, id)
);
CREATE INDEX tags_family_order ON beehome.tags(family_id, normalized_name, id);

ALTER TABLE beehome.study_sessions ADD CONSTRAINT study_sessions_family_id_unique UNIQUE (family_id, id);
CREATE TABLE beehome.book_tags (
    family_id UUID NOT NULL,
    book_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    PRIMARY KEY (book_id, tag_id),
    FOREIGN KEY (book_id, family_id) REFERENCES beehome.books(id, family_id) ON DELETE CASCADE,
    FOREIGN KEY (family_id, tag_id) REFERENCES beehome.tags(family_id, id) ON DELETE CASCADE
);
CREATE INDEX book_tags_tag_book ON beehome.book_tags(tag_id, book_id);
CREATE TABLE beehome.study_session_tags (
    family_id UUID NOT NULL,
    session_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    PRIMARY KEY (session_id, tag_id),
    FOREIGN KEY (family_id, session_id) REFERENCES beehome.study_sessions(family_id, id) ON DELETE CASCADE,
    FOREIGN KEY (family_id, tag_id) REFERENCES beehome.tags(family_id, id) ON DELETE CASCADE
);
CREATE INDEX study_session_tags_tag_session ON beehome.study_session_tags(tag_id, session_id);

ALTER TABLE beehome.routine_items ADD CONSTRAINT routine_items_family_id_unique UNIQUE (family_id, id);
CREATE TABLE beehome.routine_item_tags (
    family_id UUID NOT NULL,
    routine_item_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    PRIMARY KEY (routine_item_id, tag_id),
    FOREIGN KEY (family_id, routine_item_id) REFERENCES beehome.routine_items(family_id, id) ON DELETE CASCADE,
    FOREIGN KEY (family_id, tag_id) REFERENCES beehome.tags(family_id, id) ON DELETE CASCADE
);
CREATE INDEX routine_item_tags_tag_item ON beehome.routine_item_tags(tag_id, routine_item_id);

ALTER TABLE beehome.daily_plan_items ADD COLUMN family_id UUID;
UPDATE beehome.daily_plan_items i SET family_id = p.family_id FROM beehome.daily_plans p WHERE p.id = i.daily_plan_id;
ALTER TABLE beehome.daily_plan_items ALTER COLUMN family_id SET NOT NULL;
ALTER TABLE beehome.daily_plans ADD CONSTRAINT daily_plans_family_id_unique UNIQUE (family_id, id);
ALTER TABLE beehome.daily_plan_items ADD CONSTRAINT daily_plan_items_family_plan_fk
    FOREIGN KEY (family_id, daily_plan_id) REFERENCES beehome.daily_plans(family_id, id);
ALTER TABLE beehome.daily_plan_items ADD CONSTRAINT daily_plan_items_family_id_unique UNIQUE (family_id, id);
CREATE TABLE beehome.daily_plan_item_tags (
    family_id UUID NOT NULL,
    daily_plan_item_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    PRIMARY KEY (daily_plan_item_id, tag_id),
    FOREIGN KEY (family_id, daily_plan_item_id) REFERENCES beehome.daily_plan_items(family_id, id) ON DELETE CASCADE,
    FOREIGN KEY (family_id, tag_id) REFERENCES beehome.tags(family_id, id) ON DELETE CASCADE
);
CREATE INDEX daily_plan_item_tags_tag_item ON beehome.daily_plan_item_tags(tag_id, daily_plan_item_id);
