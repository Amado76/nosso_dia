package com.beehome.reading.entity;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.*;
import com.beehome.reading.exception.ReadingException;
@Entity
@Table(name="child_books",schema="beehome")
public class ChildBook {
    @Id private UUID id;
    private UUID familyId;
    private UUID childId;
    private UUID bookId;
    @Enumerated(EnumType.STRING) private ChildBookStatus status;
    private LocalDate startedOn;
    private LocalDate completedOn;
    private Instant createdAt;
    private Instant updatedAt;
    protected ChildBook() {}
    public ChildBook(UUID familyId, UUID childId, UUID bookId, ChildBookStatus status, LocalDate startedOn, LocalDate completedOn, Instant now) {
        id=UUID.randomUUID(); this.familyId=familyId; this.childId=childId; this.bookId=bookId; createdAt=now;
        change(status,startedOn,completedOn,now);
    }
    public void change(ChildBookStatus status, LocalDate startedOn, LocalDate completedOn, Instant now) {
        if (status==null || (status==ChildBookStatus.COMPLETED)!=(completedOn!=null)
                || (startedOn!=null && completedOn!=null && completedOn.isBefore(startedOn)))
            throw ReadingException.invalid("INVALID_READING_SESSION");
        this.status=status; this.startedOn=startedOn; this.completedOn=completedOn; updatedAt=now;
    }
    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getChildId() { return childId; }
    public UUID getBookId() { return bookId; }
    public ChildBookStatus getStatus() { return status; }
    public LocalDate getStartedOn() { return startedOn; }
    public LocalDate getCompletedOn() { return completedOn; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
