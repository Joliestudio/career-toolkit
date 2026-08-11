package com.Jolie.career_toolkit.interview;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 面試檢討。
 *
 * 欄位刻意拆開而不是塞進一個 notes：需求講的是三件不同的事
 * （今天的流程 / 這份工作實際在做什麼 / 我要調整的部分），
 * 全部塞進同一欄之後就沒辦法分別查詢或比較。
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    private UUID id;

    @Column(name = "interview_id", nullable = false)
    private UUID interviewId;

    /** 今天的面試流程 */
    @Column(name = "process_notes", columnDefinition = "TEXT")
    private String processNotes;

    /** 這份工作實際在做什麼 */
    @Column(name = "job_reality", columnDefinition = "TEXT")
    private String jobReality;

    /** 我需要調整的部分 */
    @Column(columnDefinition = "TEXT")
    private String gaps;

    /**
     * 被問到的題目。
     * 這一欄把面試檢討接回 P6 的題型對應——真正被問過的題目，變成下次要準備的題組。
     */
    @Column(name = "questions_asked", columnDefinition = "TEXT")
    private String questionsAsked;

    @Column(name = "red_flags", columnDefinition = "TEXT")
    private String redFlags;

    /**
     * rating 和 interestLevel 一定要分開。
     *
     * 「面得好不好」跟「我多想去」經常是相反的——面試順利但公司文化有疑慮，
     * 或是面得很掙扎但真的很想要那個機會。合成一個分數之後，
     * 收到 offer 要做決策時就完全用不上了。
     */
    @Column
    private Short rating;

    @Column(name = "interest_level")
    private Short interestLevel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Review() {}

    public Review(UUID interviewId) {
        this.id = UUID.randomUUID();
        this.interviewId = interviewId;
    }

    public UUID getId() { return id; }
    public UUID getInterviewId() { return interviewId; }
    public String getProcessNotes() { return processNotes; }
    public String getJobReality() { return jobReality; }
    public String getGaps() { return gaps; }
    public String getQuestionsAsked() { return questionsAsked; }
    public String getRedFlags() { return redFlags; }
    public Short getRating() { return rating; }
    public Short getInterestLevel() { return interestLevel; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setProcessNotes(String processNotes) { this.processNotes = processNotes; }
    public void setJobReality(String jobReality) { this.jobReality = jobReality; }
    public void setGaps(String gaps) { this.gaps = gaps; }
    public void setQuestionsAsked(String questionsAsked) { this.questionsAsked = questionsAsked; }
    public void setRedFlags(String redFlags) { this.redFlags = redFlags; }
    public void setRating(Short rating) { this.rating = rating; }
    public void setInterestLevel(Short interestLevel) { this.interestLevel = interestLevel; }
}
