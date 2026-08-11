package com.Jolie.career_toolkit.parsing;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 從履歷抽出來的候選項目，等待人工審核。
 *
 * 解析**不會**直接產生 Block。理由有三個：
 *   1. 擋掉垃圾進入積木庫——解析品質參差，尤其中文 PDF
 *   2. 留下有標籤的資料，日後比較「字典 vs LLM」時可以在同一份履歷上直接對照
 *   3. 被拒絕的候選會被記住，重新解析同一個檔案不會讓它們復活
 */
@Entity
@Table(name = "extraction_candidates")
public class ExtractionCandidate {

    public enum Kind { SKILL, CERTIFICATION }

    public enum Status { PENDING, ACCEPTED, REJECTED }

    @Id
    private UUID id;

    @Column(name = "resume_file_id", nullable = false)
    private UUID resumeFileId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    /** 原文中出現的樣子，例如 "k8s"。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String value;

    /** 正規化後的標準名稱，例如 "Kubernetes"。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String normalized;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    /** 誰抽出來的。日後接 LLM 時靠這欄比較兩種抽取方式的準確度。 */
    @Column(nullable = false, length = 40)
    private String extractor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** 接受之後產生的積木。 */
    @Column(name = "block_id")
    private UUID blockId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExtractionCandidate() {}

    public ExtractionCandidate(UUID resumeFileId, UUID userId, Kind kind,
                               String value, String normalized,
                               BigDecimal confidence, String extractor) {
        this.id = UUID.randomUUID();
        this.resumeFileId = resumeFileId;
        this.userId = userId;
        this.kind = kind;
        this.value = value;
        this.normalized = normalized;
        this.confidence = confidence;
        this.extractor = extractor;
        this.status = Status.PENDING;
    }

    public void accept(UUID blockId) {
        this.status = Status.ACCEPTED;
        this.blockId = blockId;
    }

    public void reject() {
        this.status = Status.REJECTED;
    }

    public UUID getId() { return id; }
    public UUID getResumeFileId() { return resumeFileId; }
    public UUID getUserId() { return userId; }
    public Kind getKind() { return kind; }
    public String getValue() { return value; }
    public String getNormalized() { return normalized; }
    public BigDecimal getConfidence() { return confidence; }
    public String getExtractor() { return extractor; }
    public Status getStatus() { return status; }
    public UUID getBlockId() { return blockId; }
    public Instant getCreatedAt() { return createdAt; }
}
