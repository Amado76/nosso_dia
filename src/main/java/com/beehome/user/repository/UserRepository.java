package com.beehome.user.repository;

import com.beehome.user.entity.User;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    @Modifying
    @Query(value = """
            INSERT INTO beehome.users (id, name, email, password_hash, created_at, updated_at)
            VALUES (:id, :name, :email, :hash, :now, :now)
            ON CONFLICT (email) DO NOTHING
            """, nativeQuery = true)
    int insertIfEmailAvailable(@Param("id") UUID id, @Param("name") String name,
            @Param("email") String email, @Param("hash") String hash, @Param("now") Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") UUID id);
}
