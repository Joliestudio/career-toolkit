package com.Jolie.career_toolkit.answers;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 某個題型要用哪幾塊積木組。
 *
 * 結構上跟 ResumeVersion 幾乎相同（都是「有標籤、有順序的積木選集」），
 * 但刻意不合併：履歷要鎖定與快照，題組要字數上限，兩者很快就會分歧。
 * 過早合併換來的是每個查詢都得帶一個 discriminator。
 */
@Entity
@Table(name = "answer_presets")
public class AnswerPreset {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "question_type_id", nullable = false)
    private Short questionTypeId;

    @Column(nullable = false, length = 120)
    private String label;

    /** 目標字數上限。組完的答案要直接對照這個數字。 */
    @Column(name = "target_char_limit")
    private Integer targetCharLimit;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AnswerPreset() {}

    public AnswerPreset(UUID userId, Short questionTypeId, String label, Integer targetCharLimit) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.questionTypeId = questionTypeId;
        this.label = label;
        this.targetCharLimit = targetCharLimit;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Short getQuestionTypeId() { return questionTypeId; }
    public String getLabel() { return label; }
    public Integer getTargetCharLimit() { return targetCharLimit; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setLabel(String label) { this.label = label; }
    public void setTargetCharLimit(Integer targetCharLimit) { this.targetCharLimit = targetCharLimit; }
    public void setNotes(String notes) { this.notes = notes; }
}
