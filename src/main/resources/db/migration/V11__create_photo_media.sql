CREATE TABLE beehome.media (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES beehome.families(id),
    uploaded_by_user_id UUID NOT NULL REFERENCES beehome.users(id),
    type VARCHAR(10) NOT NULL CHECK (type = 'IMAGE'),
    storage_key VARCHAR(120) NOT NULL UNIQUE,
    original_filename VARCHAR(255),
    mime_type VARCHAR(20) NOT NULL CHECK (mime_type IN ('image/jpeg','image/png','image/webp')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT media_id_family_unique UNIQUE (id, family_id)
);
CREATE INDEX media_family_created ON beehome.media(family_id, created_at DESC, id DESC);

CREATE TABLE beehome.photo_records (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES beehome.families(id),
    child_id UUID NOT NULL,
    record_date DATE NOT NULL,
    description VARCHAR(2000) CHECK (description IS NULL OR length(btrim(description)) > 0),
    created_by_user_id UUID NOT NULL REFERENCES beehome.users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT photo_records_child_fk FOREIGN KEY (family_id, child_id) REFERENCES beehome.family_members(family_id, id),
    CONSTRAINT photo_records_id_family_unique UNIQUE (id, family_id)
);
CREATE INDEX photo_records_history ON beehome.photo_records(family_id, child_id, record_date DESC, created_at DESC, id DESC);

CREATE TABLE beehome.photo_record_media (
    id UUID PRIMARY KEY,
    photo_record_id UUID NOT NULL,
    family_id UUID NOT NULL,
    media_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0 AND position < 20),
    CONSTRAINT photo_record_media_record_fk FOREIGN KEY (photo_record_id, family_id) REFERENCES beehome.photo_records(id, family_id) ON DELETE CASCADE,
    CONSTRAINT photo_record_media_media_fk FOREIGN KEY (media_id, family_id) REFERENCES beehome.media(id, family_id),
    CONSTRAINT photo_record_media_unique UNIQUE (photo_record_id, media_id),
    CONSTRAINT photo_record_media_position_unique UNIQUE (photo_record_id, position)
);
CREATE INDEX photo_record_media_reverse ON beehome.photo_record_media(media_id);
CREATE INDEX photo_record_media_order ON beehome.photo_record_media(photo_record_id, position);

CREATE FUNCTION beehome.require_photo_record_media() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM beehome.photo_records WHERE id = COALESCE(NEW.photo_record_id, OLD.photo_record_id))
       AND NOT EXISTS (SELECT 1 FROM beehome.photo_record_media WHERE photo_record_id = COALESCE(NEW.photo_record_id, OLD.photo_record_id)) THEN
        RAISE EXCEPTION 'Photo record requires at least one image';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER photo_record_media_nonempty_after_link
    AFTER DELETE OR UPDATE ON beehome.photo_record_media
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION beehome.require_photo_record_media();

CREATE FUNCTION beehome.require_new_photo_record_media() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM beehome.photo_records WHERE id = NEW.id)
       AND NOT EXISTS (SELECT 1 FROM beehome.photo_record_media WHERE photo_record_id = NEW.id) THEN
        RAISE EXCEPTION 'Photo record requires at least one image';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER photo_record_media_nonempty_after_record
    AFTER INSERT ON beehome.photo_records
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION beehome.require_new_photo_record_media();
