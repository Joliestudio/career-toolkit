package com.Jolie.career_toolkit.offer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    Optional<Offer> findByApplicationId(UUID applicationId);

    /**
     * 我手上所有的 offer——跨投遞的查詢，必須 join 回 applications 綁 user。
     * 少了 a.userId = :userId 就是把所有人的 offer 都撈出來。
     */
    @Query("""
            select o from Offer o
            join JobApplication a on a.id = o.applicationId
            where a.userId = :userId and a.deletedAt is null
            order by o.replyDeadline asc nulls last, o.createdAt desc
            """)
    List<Offer> findAllByUserId(UUID userId);

    @Query("""
            select o from Offer o
            join JobApplication a on a.id = o.applicationId
            where a.userId = :userId and a.deletedAt is null and o.id = :id
            """)
    Optional<Offer> findByIdAndUserId(UUID id, UUID userId);
}
