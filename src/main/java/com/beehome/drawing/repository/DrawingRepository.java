package com.beehome.drawing.repository;

import com.beehome.drawing.entity.Drawing;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrawingRepository extends JpaRepository<Drawing, UUID> {
    Optional<Drawing> findByFamilyIdAndMemberIdAndSurface(UUID familyId, UUID memberId, String surface);
}
