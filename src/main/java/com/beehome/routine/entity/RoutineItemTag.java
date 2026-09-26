package com.beehome.routine.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "routine_item_tags", schema = "beehome")
@IdClass(RoutineItemTag.Key.class)
public class RoutineItemTag {
    @Id private UUID routineItemId;
    @Id private UUID tagId;
    @SuppressWarnings("unused") private UUID familyId;
    protected RoutineItemTag() {}
    public RoutineItemTag(UUID familyId,UUID routineItemId,UUID tagId) { this.familyId=familyId; this.routineItemId=routineItemId; this.tagId=tagId; }
    public UUID getRoutineItemId() { return routineItemId; }
    public UUID getTagId() { return tagId; }
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        public UUID routineItemId;
        public UUID tagId;
        public Key() {}
        public Key(UUID routineItemId,UUID tagId) { this.routineItemId=routineItemId; this.tagId=tagId; }
        @Override public boolean equals(Object other) { return other instanceof Key key && java.util.Objects.equals(routineItemId,key.routineItemId) && java.util.Objects.equals(tagId,key.tagId); }
        @Override public int hashCode() { return java.util.Objects.hash(routineItemId,tagId); }
    }
}
