package com.beehome.reading.repository;

import com.beehome.reading.entity.BookTag;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface BookTagRepository extends JpaRepository<BookTag, BookTag.Key> {
    List<BookTag> findByBookIdIn(Collection<UUID> bookIds);
    void deleteByBookId(UUID bookId);
}
