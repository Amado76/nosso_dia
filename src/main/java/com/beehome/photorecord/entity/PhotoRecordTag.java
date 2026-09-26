package com.beehome.photorecord.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="photo_record_tags", schema="beehome")
@IdClass(PhotoRecordTag.Key.class)
public class PhotoRecordTag {
    @Id private UUID photoRecordId;
    @Id private UUID tagId;
    @Column(name="family_id", nullable=false) private UUID familyId;
    protected PhotoRecordTag() {}
    public PhotoRecordTag(UUID familyId, UUID photoRecordId, UUID tagId) {
        this.familyId=familyId; this.photoRecordId=photoRecordId; this.tagId=tagId;
    }
    public UUID getPhotoRecordId() { return photoRecordId; }
    public UUID getTagId() { return tagId; }
    public static class Key implements Serializable {
        private static final long serialVersionUID=1L;
        public UUID photoRecordId;
        public UUID tagId;
        public Key() {}
        public Key(UUID photoRecordId, UUID tagId) { this.photoRecordId=photoRecordId; this.tagId=tagId; }
        @Override public boolean equals(Object value) {
            return value instanceof Key key && Objects.equals(photoRecordId,key.photoRecordId) && Objects.equals(tagId,key.tagId);
        }
        @Override public int hashCode() { return Objects.hash(photoRecordId,tagId); }
    }
}
