package com.beehome.study.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "study_session_tags", schema = "beehome")
@IdClass(StudySessionTag.Key.class)
public class StudySessionTag {
    @Id private UUID sessionId;
    @Id private UUID tagId;
    @SuppressWarnings("unused") private UUID familyId;
    protected StudySessionTag() {}
    public StudySessionTag(UUID familyId, UUID sessionId, UUID tagId) { this.familyId=familyId; this.sessionId=sessionId; this.tagId=tagId; }
    public UUID getSessionId() { return sessionId; }
    public UUID getTagId() { return tagId; }
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        public UUID sessionId;
        public UUID tagId;
        public Key() {}
        public Key(UUID sessionId,UUID tagId) { this.sessionId=sessionId; this.tagId=tagId; }
        @Override public boolean equals(Object other) { return other instanceof Key key && java.util.Objects.equals(sessionId,key.sessionId) && java.util.Objects.equals(tagId,key.tagId); }
        @Override public int hashCode() { return java.util.Objects.hash(sessionId,tagId); }
    }
}
