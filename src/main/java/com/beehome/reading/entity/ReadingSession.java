package com.beehome.reading.entity;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.*;
import com.beehome.shared.dto.JsonFields;
import com.beehome.reading.exception.ReadingException;
@Entity
@Table(name="reading_sessions",schema="beehome")
public class ReadingSession {
    @Id private UUID id;
    private UUID familyId;
    private UUID childId;
    private UUID childBookId;
    private LocalDate date;
    private Integer minutes;
    private Integer pagesRead;
    private Integer startPage;
    private Integer endPage;
    @Column(length=2000) private String notes;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    protected ReadingSession() {}
    public ReadingSession(UUID familyId, UUID childId, UUID childBookId, UUID createdBy, LocalDate date,
            Integer minutes, Integer pagesRead, Integer startPage, Integer endPage, String notes, Integer totalPages, Instant now) {
        id=UUID.randomUUID(); this.familyId=familyId; this.childId=childId; this.childBookId=childBookId;
        this.createdBy=createdBy; createdAt=now;
        replace(date,minutes,pagesRead,startPage,endPage,notes,totalPages,now);
    }
    public void replace(LocalDate date, Integer minutes, Integer pagesRead, Integer startPage, Integer endPage,
            String notes, Integer totalPages, Instant now) {
        if (date==null) throw ReadingException.invalid("INVALID_READING_SESSION");
        if (minutes!=null && minutes<0) throw ReadingException.invalid("INVALID_READING_DURATION");
        if ((pagesRead!=null && pagesRead<0) || (startPage!=null && startPage<0) || (endPage!=null && endPage<0)
                || (startPage==null)!=(endPage==null) || (startPage!=null && endPage<startPage)
                || (totalPages!=null && ((pagesRead!=null && pagesRead>totalPages) || (endPage!=null && endPage>totalPages))))
            throw ReadingException.invalid("INVALID_PAGE_RANGE");
        if (startPage!=null) {
            int calculated=endPage-startPage;
            if (pagesRead!=null && pagesRead!=calculated) throw ReadingException.invalid("INVALID_PAGE_RANGE");
            pagesRead=calculated;
        }
        this.date=date; this.minutes=minutes; this.pagesRead=pagesRead; this.startPage=startPage;
        this.endPage=endPage; this.notes=JsonFields.text(notes,2000,false); updatedAt=now;
    }
    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getChildId() { return childId; }
    public UUID getChildBookId() { return childBookId; }
    public LocalDate getDate() { return date; }
    public Integer getMinutes() { return minutes; }
    public Integer getPagesRead() { return pagesRead; }
    public Integer getStartPage() { return startPage; }
    public Integer getEndPage() { return endPage; }
    public String getNotes() { return notes; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
