package com.Jolie.career_toolkit.selection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface SelectionCallRepository extends JpaRepository<SelectionCall, UUID> {

    /** 今天用了幾次——配額用的。 */
    @Query("""
            select count(c) from SelectionCall c
            where c.userId = :userId and c.createdAt >= :since
            """)
    long countByUserIdSince(UUID userId, Instant since);

    boolean existsByUserIdAndRequestHash(UUID userId, String requestHash);
}
