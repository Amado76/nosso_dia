CREATE TABLE beehome.photo_record_tags (
    family_id UUID NOT NULL,
    photo_record_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    PRIMARY KEY (photo_record_id, tag_id),
    FOREIGN KEY (photo_record_id, family_id) REFERENCES beehome.photo_records(id, family_id) ON DELETE CASCADE,
    FOREIGN KEY (family_id, tag_id) REFERENCES beehome.tags(family_id, id) ON DELETE CASCADE
);
CREATE INDEX photo_record_tags_tag_record ON beehome.photo_record_tags(tag_id, photo_record_id);
