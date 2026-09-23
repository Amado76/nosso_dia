package com.nossodia.family.repository;

import com.nossodia.family.entity.Family;
import com.nossodia.family.entity.FamilyMembership;

import com.nossodia.family.dto.FamilyResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FamilyRepository extends JpaRepository<Family, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Family f where f.id = :id")
    Optional<Family> lockById(UUID id);

    @Query("""
            select new com.nossodia.family.dto.FamilyResponse(f.id, f.name, f.timezone, m.role, f.createdAt, f.updatedAt)
            from Family f join FamilyMembership m on m.familyId = f.id
            where m.userId = :userId order by f.createdAt asc, f.id asc
            """)
    Slice<FamilyResponse> findAccessible(UUID userId, Pageable pageable);

    @Query("""
            select new com.nossodia.family.dto.FamilyResponse(f.id, f.name, f.timezone, m.role, f.createdAt, f.updatedAt)
            from Family f join FamilyMembership m on m.familyId = f.id
            where m.userId = :userId and f.id = :familyId
            """)
    Optional<FamilyResponse> findAccessible(UUID userId, UUID familyId);
}
