package com.nossodia.family.repository;

import com.nossodia.family.entity.FamilyMembership;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyMembershipRepository extends JpaRepository<FamilyMembership, UUID> {
    Optional<FamilyMembership> findByFamilyIdAndUserId(UUID familyId, UUID userId);
}
