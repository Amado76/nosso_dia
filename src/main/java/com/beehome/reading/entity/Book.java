package com.beehome.reading.entity;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.*;
import com.beehome.shared.dto.JsonFields;
import com.beehome.reading.exception.ReadingException;
@Entity
@Table(name="books",schema="beehome")
public class Book {
    @Id private UUID id;
    private UUID familyId;
    @Column(length=300) private String title;
    @Column(length=200) private String author;
    @Column(length=32) private String isbn;
    private Integer totalPages;
    private UUID coverMediaId;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    protected Book() {}
    public Book(UUID familyId, UUID createdBy, String title, String author, String isbn, Integer totalPages, UUID coverMediaId, Instant now) {
        id=UUID.randomUUID(); this.familyId=familyId; this.createdBy=createdBy; createdAt=now;
        replace(title,author,isbn,totalPages,coverMediaId,now);
    }
    public void replace(String title, String author, String isbn, Integer totalPages, UUID coverMediaId, Instant now) {
        this.title=JsonFields.text(title,300,true); this.author=JsonFields.text(author,200,false);
        this.isbn=JsonFields.text(isbn,32,false);
        if (totalPages!=null && totalPages<1) throw ReadingException.invalid("INVALID_PAGE_RANGE");
        this.totalPages=totalPages; this.coverMediaId=coverMediaId; updatedAt=now;
    }
    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getIsbn() { return isbn; }
    public Integer getTotalPages() { return totalPages; }
    public UUID getCoverMediaId() { return coverMediaId; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
