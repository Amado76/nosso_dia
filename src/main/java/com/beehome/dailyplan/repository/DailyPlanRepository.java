package com.beehome.dailyplan.repository;
import com.beehome.dailyplan.entity.DailyPlan;
import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DailyPlanRepository extends JpaRepository<DailyPlan, UUID> {
    Optional<DailyPlan> findByFamilyIdAndFamilyMemberIdAndPlanDate(UUID familyId, UUID memberId, LocalDate date);
    @Modifying
    @Query(value = """
        insert into beehome.daily_plans(id, family_id, family_member_id, plan_date, created_at, updated_at)
        values (:id, :familyId, :memberId, :date, :now, :now)
        on conflict (family_id, family_member_id, plan_date) do nothing
        """, nativeQuery = true)
    void createIfAbsent(UUID id, UUID familyId, UUID memberId, LocalDate date, Instant now);
}
