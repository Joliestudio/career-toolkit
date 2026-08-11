package com.Jolie.career_toolkit.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByInterviewId(UUID interviewId);

    /**
     * 一筆投遞底下所有場次的檢討，依輪次排序。
     * 收到 offer 要做決策時，就是靠這個把每一輪的印象並排看。
     */
    @Query("""
            select r from Review r
            join Interview i on i.id = r.interviewId
            where i.applicationId = :applicationId
            order by i.round asc
            """)
    List<Review> findByApplicationId(UUID applicationId);

    /**
     * 這個人在所有面試裡被問過的題目，最近的排前面。
     *
     * 這條查詢把 P3 的面試檢討接回 P6 的題型準備——
     * 真正被問過的題目，變成下次要準備的題組。
     * 沒有這條線，檢討就只是寫完不會再看的日記。
     */
    @Query("""
            select r.questionsAsked from Review r
            join Interview i on i.id = r.interviewId
            join JobApplication a on a.id = i.applicationId
            where a.userId = :userId
              and a.deletedAt is null
              and r.questionsAsked is not null
            order by r.updatedAt desc
            """)
    List<String> findQuestionsAskedByUserId(UUID userId);
}
