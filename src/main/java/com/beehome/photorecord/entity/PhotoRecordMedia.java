package com.beehome.photorecord.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="photo_record_media", schema="beehome")
public class PhotoRecordMedia {
    @Id private UUID id;
    @Column(name="photo_record_id", nullable=false) private UUID photoRecordId;
    @Column(name="family_id", nullable=false) private UUID familyId;
    @Column(name="media_id", nullable=false) private UUID mediaId;
    @Column(nullable=false) private int position;
    protected PhotoRecordMedia() {}
    public PhotoRecordMedia(UUID record, UUID family, UUID media, int position) {
        id=UUID.randomUUID(); photoRecordId=record; familyId=family; mediaId=media; this.position=position;
    }
    public UUID getPhotoRecordId() { return photoRecordId; }
    public UUID getMediaId() { return mediaId; }
    public int getPosition() { return position; }
}
