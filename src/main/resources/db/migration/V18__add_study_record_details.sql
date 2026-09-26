ALTER TABLE beehome.study_sessions
    ADD COLUMN topic VARCHAR(120),
    ADD COLUMN description VARCHAR(10000),
    ADD COLUMN comments VARCHAR(10000),
    ADD COLUMN material VARCHAR(1000),
    ADD COLUMN start_page INTEGER CHECK (start_page > 0),
    ADD COLUMN end_page INTEGER CHECK (end_page > 0),
    ADD COLUMN report_record BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT study_sessions_pages CHECK (start_page IS NULL OR end_page IS NULL OR end_page >= start_page),
    ADD CONSTRAINT study_sessions_report_subject CHECK (NOT report_record OR subject_id IS NOT NULL);

CREATE FUNCTION beehome.require_new_study_subject() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.subject_id IS NULL THEN
            RAISE EXCEPTION 'Study subject required' USING ERRCODE = '23514', CONSTRAINT = 'study_sessions_subject_required';
        END IF;
    ELSIF OLD.subject_id IS NOT NULL AND NEW.subject_id IS NULL THEN
        RAISE EXCEPTION 'Study subject required' USING ERRCODE = '23514', CONSTRAINT = 'study_sessions_subject_required';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER study_sessions_require_subject
    BEFORE INSERT OR UPDATE OF subject_id ON beehome.study_sessions
    FOR EACH ROW EXECUTE FUNCTION beehome.require_new_study_subject();
