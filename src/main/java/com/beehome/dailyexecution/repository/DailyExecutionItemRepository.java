package com.beehome.dailyexecution.repository;
import com.beehome.dailyexecution.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface DailyExecutionItemRepository extends JpaRepository<DailyExecutionItem, UUID> {
    List<DailyExecutionItem> findByExecutionId(UUID execution);
    @Query("select count(i) from DailyExecutionItem i join DailyExecution e on e.id = i.executionId where i.id = :item and e.familyId = :family and e.familyMemberId = :member")
    long countOwned(UUID family, UUID member, UUID item);
    @Query("select i.executionId as executionId, i.status as status, count(i) as count from DailyExecutionItem i where i.executionId in :ids group by i.executionId, i.status")
    List<StateCount> counts(Collection<UUID> ids);
    interface StateCount { UUID getExecutionId(); ItemStatus getStatus(); long getCount(); }
}
