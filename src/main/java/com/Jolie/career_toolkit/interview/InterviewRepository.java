package com.Jolie.career_toolkit.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 面試掛在 application 底下，所有權在讀取 application 時就檢查過了。
 *
 * 例外是下面那個「未來面試」的查詢——它是跨 application 的，
 * 所以必須自己帶 userId，而且方法名裡就寫著 UserId。
 */
public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    List<Interview> findByApplicationIdOrderByRoundAsc(UUID applicationId);

    Optional<Interview> findByApplicationIdAndRound(UUID applicationId, short round);

    /**
     * 「我未來有幾間面試」——跨投遞的查詢，所以必須自己 join 回 applications 綁 user。
     *
     * 這是唯一需要 join 才能確認所有權的地方，所以條件寫得很明確：
     * 少了 a.userId = :userId 這一行就是把所有人的面試都撈出來。
     */
    @Query("""
            select i from Interview i
            join JobApplication a on a.id = i.applicationId
            where a.userId = :userId
              and a.deletedAt is null
              and i.status = com.Jolie.career_toolkit.interview.Interview$Status.SCHEDULED
              and i.scheduledAt >= :from
              and i.scheduledAt < :to
            order by i.scheduledAt asc
            """)
    List<Interview> findUpcomingByUserId(UUID userId, Instant from, Instant to);

    @Query("""
            select i from Interview i
            join JobApplication a on a.id = i.applicationId
            where a.userId = :userId and a.deletedAt is null and i.id = :id
            """)
    Optional<Interview> findByIdAndUserId(UUID id, UUID userId);
}
