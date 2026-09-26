package com.beehome.activity.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.*;

@Entity
@Table(name = "extracurricular_record_tags", schema = "beehome")
@IdClass(ExtracurricularRecordTag.Key.class)
public class ExtracurricularRecordTag {
    @Id private UUID recordId;
    @Id private UUID tagId;
    private UUID familyId;
    protected ExtracurricularRecordTag() {}
    public ExtracurricularRecordTag(UUID family, UUID record, UUID tag) { familyId = family; recordId = record; tagId = tag; }
    public UUID getRecordId() { return recordId; }
    public UUID getTagId() { return tagId; }
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        public UUID recordId;
        public UUID tagId;
        public Key() {}
        public Key(UUID record, UUID tag) { recordId = record; tagId = tag; }
        @Override public boolean equals(Object other) { return other instanceof Key key && Objects.equals(recordId, key.recordId) && Objects.equals(tagId, key.tagId); }
        @Override public int hashCode() { return Objects.hash(recordId, tagId); }
    }
}
