ALTER SCHEMA nosso_dia RENAME TO beehome;

-- PostgreSQL keeps schema-qualified names in this PL/pgSQL function body as text.
CREATE OR REPLACE FUNCTION beehome.require_routine_days() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE target_id UUID;
BEGIN
    IF TG_TABLE_NAME = 'routines' THEN target_id := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN target_id := OLD.routine_id;
    ELSE target_id := OLD.routine_id;
    END IF;
    IF EXISTS (SELECT 1 FROM beehome.routines WHERE id = target_id)
       AND NOT EXISTS (SELECT 1 FROM beehome.routine_days WHERE routine_id = target_id) THEN
        RAISE EXCEPTION 'A routine requires weekdays' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
