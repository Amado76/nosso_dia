package com.beehome.dailyplan.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "daily_plan_item_tags", schema = "beehome")
@IdClass(DailyPlanItemTag.Key.class)
public class DailyPlanItemTag {
    @Id private UUID dailyPlanItemId;
    @Id private UUID tagId;
    @SuppressWarnings("unused") private UUID familyId;
    protected DailyPlanItemTag() {}
    public DailyPlanItemTag(UUID familyId,UUID dailyPlanItemId,UUID tagId) { this.familyId=familyId; this.dailyPlanItemId=dailyPlanItemId; this.tagId=tagId; }
    public UUID getDailyPlanItemId() { return dailyPlanItemId; }
    public UUID getTagId() { return tagId; }
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        public UUID dailyPlanItemId;
        public UUID tagId;
        public Key() {}
        public Key(UUID dailyPlanItemId,UUID tagId) { this.dailyPlanItemId=dailyPlanItemId; this.tagId=tagId; }
        @Override public boolean equals(Object other) { return other instanceof Key key && java.util.Objects.equals(dailyPlanItemId,key.dailyPlanItemId) && java.util.Objects.equals(tagId,key.tagId); }
        @Override public int hashCode() { return java.util.Objects.hash(dailyPlanItemId,tagId); }
    }
}
