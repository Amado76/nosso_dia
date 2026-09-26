package com.beehome.activity.entity;

import com.beehome.shared.exception.InputException;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "extracurricular_records", schema = "beehome")
public class ExtracurricularRecord {
    @Id private UUID id;
    @Column(nullable = false) private UUID familyId;
    @Column(nullable = false) private UUID childId;
    @Column(nullable = false) private UUID activityId;
    @Column(name = "record_date", nullable = false) private LocalDate date;
    @Column(length = 120) private String topic;
    private Integer durationMinutes;
    @Column(length = 10000) private String description;
    @Column(length = 10000) private String comments;
    @Column(length = 1000) private String material;
    private Integer startPage;
    private Integer endPage;
    @Column(nullable = false) private UUID createdByUserId;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;
    protected ExtracurricularRecord() {}
    public ExtracurricularRecord(UUID family, UUID child, UUID actor, UUID activity, LocalDate date,
            String topic, Integer duration, String description, String comments, String material,
            Integer startPage, Integer endPage, Instant now) {
        id = UUID.randomUUID(); familyId = family; childId = child; createdByUserId = actor; createdAt = now;
        edit(activity, date, topic, duration, description, comments, material, startPage, endPage, now);
    }
    public void edit(UUID activity, LocalDate date, String topic, Integer duration, String description,
            String comments, String material, Integer startPage, Integer endPage, Instant now) {
        if (activity == null || date == null || (duration != null && duration <= 0)
                || (startPage != null && startPage <= 0) || (endPage != null && endPage <= 0)
                || (startPage != null && endPage != null && endPage < startPage)) throw new InputException();
        activityId = activity; this.date = date; this.topic = ExtracurricularActivity.clean(topic, 120);
        durationMinutes = duration; this.description = ExtracurricularActivity.clean(description, 10000);
        this.comments = ExtracurricularActivity.clean(comments, 10000);
        this.material = ExtracurricularActivity.clean(material, 1000);
        this.startPage = startPage; this.endPage = endPage; updatedAt = now;
    }
    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getChildId() { return childId; }
    public UUID getActivityId() { return activityId; }
    public LocalDate getDate() { return date; }
    public String getTopic() { return topic; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String getDescription() { return description; }
    public String getComments() { return comments; }
    public String getMaterial() { return material; }
    public Integer getStartPage() { return startPage; }
    public Integer getEndPage() { return endPage; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
