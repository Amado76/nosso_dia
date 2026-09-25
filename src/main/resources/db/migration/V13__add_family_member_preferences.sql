ALTER TABLE beehome.family_members
    ADD COLUMN preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD CONSTRAINT family_members_preferences_object CHECK (jsonb_typeof(preferences) = 'object');
