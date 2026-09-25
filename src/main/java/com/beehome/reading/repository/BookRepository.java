package com.beehome.reading.repository;
import com.beehome.reading.entity.*;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
public interface BookRepository extends JpaRepository<Book,UUID> {
    Optional<Book> findByFamilyIdAndId(UUID familyId,UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.familyId=:family and b.id=:id")
    Optional<Book> lock(UUID family,UUID id);
    @Query("select b from Book b where b.familyId=:family order by b.createdAt desc,b.id desc")
    Slice<Book> list(UUID family,Pageable page);
    List<Book> findByFamilyIdAndIdIn(UUID family,Collection<UUID> ids);
}
