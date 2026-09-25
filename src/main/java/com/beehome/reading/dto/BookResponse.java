package com.beehome.reading.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import java.time.*;
import com.beehome.reading.entity.*;
@Schema(description="Family book metadata. Author, ISBN, totalPages, and coverMediaId are nullable. Audit and identity fields are server controlled.", requiredProperties={"id","familyId","title","author","isbn","totalPages","coverMediaId","createdBy","createdAt","updatedAt"})
public record BookResponse(UUID id, UUID familyId, String title, String author, String isbn, Integer totalPages, UUID coverMediaId, UUID createdBy, Instant createdAt, Instant updatedAt) {
    public static BookResponse from(Book row) { return new BookResponse(row.getId(), row.getFamilyId(), row.getTitle(), row.getAuthor(), row.getIsbn(), row.getTotalPages(), row.getCoverMediaId(), row.getCreatedBy(), row.getCreatedAt(), row.getUpdatedAt()); }
}
