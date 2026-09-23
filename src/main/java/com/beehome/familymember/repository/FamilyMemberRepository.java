package com.beehome.familymember.repository;

import com.beehome.familymember.entity.FamilyMember;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.Optional;
import com.beehome.familymember.entity.MemberType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {
    boolean existsByFamilyIdAndLinkedUserId(UUID familyId, UUID linkedUserId);

    Optional<FamilyMember> findByFamilyIdAndId(UUID familyId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from FamilyMember m where m.familyId = :familyId and m.id = :id")
    Optional<FamilyMember> lockByFamilyIdAndId(UUID familyId, UUID id);

    @Query("select m from FamilyMember m where m.familyId = :familyId and (:includeInactive = true or m.active = true) "
            + "and (:type is null or m.memberType = :type) order by m.createdAt, m.id")
    Slice<FamilyMember> list(UUID familyId, boolean includeInactive, MemberType type, Pageable pageable);
}
