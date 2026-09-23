package com.beehome.dailyexecution.repository;
import com.beehome.dailyexecution.entity.DailyExecution;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface DailyExecutionRepository extends JpaRepository<DailyExecution, UUID> {
    Optional<DailyExecution> findByFamilyIdAndFamilyMemberIdAndExecutionDate(UUID family, UUID member, LocalDate date);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from DailyExecution e where e.familyId = :family and e.familyMemberId = :member and e.executionDate = :date")
    Optional<DailyExecution> lock(UUID family, UUID member, LocalDate date);
    @Modifying
    @Query(value = """
        insert into beehome.daily_executions(id,family_id,family_member_id,execution_date,status,reopened,created_at,updated_at)
        values (:id,:family,:member,:date,'OPEN',false,:now,:now)
        on conflict (family_id,family_member_id,execution_date) do nothing
        """, nativeQuery = true)
    void createIfAbsent(UUID id, UUID family, UUID member, LocalDate date, Instant now);
    @Query("select e from DailyExecution e where e.familyId = :family and e.familyMemberId = :member and e.executionDate between :from and :to order by e.executionDate desc, e.id desc")
    Slice<DailyExecution> history(UUID family, UUID member, LocalDate from, LocalDate to, Pageable page);
}
