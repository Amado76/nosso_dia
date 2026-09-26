package com.beehome.reading.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "book_tags", schema = "beehome")
@IdClass(BookTag.Key.class)
public class BookTag {
    @Id private UUID bookId;
    @Id private UUID tagId;
    @SuppressWarnings("unused") private UUID familyId;
    protected BookTag() {}
    public BookTag(UUID familyId, UUID bookId, UUID tagId) { this.familyId = familyId; this.bookId = bookId; this.tagId = tagId; }
    public UUID getBookId() { return bookId; }
    public UUID getTagId() { return tagId; }
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        public UUID bookId;
        public UUID tagId;
        public Key() {}
        public Key(UUID bookId, UUID tagId) { this.bookId=bookId; this.tagId=tagId; }
        @Override public boolean equals(Object other) { return other instanceof Key key && java.util.Objects.equals(bookId,key.bookId) && java.util.Objects.equals(tagId,key.tagId); }
        @Override public int hashCode() { return java.util.Objects.hash(bookId,tagId); }
    }
}
