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
}
