package com.beehome.reading.repository;
import com.beehome.reading.entity.*;
import java.util.*;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
public interface ChildBookRepository extends JpaRepository<ChildBook,UUID> {
    Optional<ChildBook> findByFamilyIdAndChildIdAndId(UUID familyId,UUID childId,UUID id);
    boolean existsByBookId(UUID bookId);
    boolean existsByChildIdAndBookIdAndStatusInAndIdNot(UUID childId,UUID bookId,Collection<ChildBookStatus> statuses,UUID id);
    @Query("select c from ChildBook c where c.familyId=:family and c.childId=:child and (:status is null or c.status=:status) order by c.createdAt desc,c.id desc")
    Slice<ChildBook> list(UUID family,UUID child,ChildBookStatus status,Pageable page);
    @Query("select count(c) from ChildBook c where c.familyId=:family and c.childId=:child and c.status=com.beehome.reading.entity.ChildBookStatus.COMPLETED and c.completedOn between :from and :to")
    long completed(UUID family,UUID child,LocalDate from,LocalDate to);
}
