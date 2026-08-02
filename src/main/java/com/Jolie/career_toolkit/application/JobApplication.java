package com.Jolie.career_toolkit.application;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 一筆投遞。
 *
 * 命名叫 JobApplication 而不是 Application，是為了不要跟 Spring 的
 * CareerToolkitApplication / ApplicationContext 這些字眼在讀程式時混淆。
 * 資料表仍然叫 applications。
 */
@Entity
@Table(name = "applications")
public class JobApplication {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /** 手動填表投遞時沒有履歷版本，所以可為 NULL。 */
    @Column(name = "resume_version_id")
    private UUID resumeVersionId;

    @Column(name = "position_title", nullable = false, length = 200)
    private String positionTitle;

    @Column(name = "job_url", length = 500)
    private String jobUrl;

    @Column(length = 40)
    private String source;

    @Column(name = "applied_at")
    private LocalDate appliedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "next_action_at")
    private Instant nextActionAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobApplication() {}

    public JobApplication(UUID userId, UUID companyId, String positionTitle, ApplicationStatus status) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.companyId = companyId;
        this.positionTitle = positionTitle;
        this.status = status;
        this.statusChangedAt = Instant.now();

        // 已經投出去的話，預設投遞日期就是今天——少一個要填的欄位
        if (status != ApplicationStatus.DRAFT) {
            this.appliedAt = LocalDate.now();
        }
    }

    /**
     * 狀態轉換。合法性由 Service 檢查後才呼叫這裡——
     * entity 只負責讓「改狀態」和「更新時間戳」不可能分開發生。
     */
    void applyStatus(ApplicationStatus target) {
        this.status = target;
        this.statusChangedAt = Instant.now();

        if (this.appliedAt == null && target != ApplicationStatus.DRAFT) {
            this.appliedAt = LocalDate.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getCompanyId() { return companyId; }
    public UUID getResumeVersionId() { return resumeVersionId; }
    public String getPositionTitle() { return positionTitle; }
    public String getJobUrl() { return jobUrl; }
    public String getSource() { return source; }
    public LocalDate getAppliedAt() { return appliedAt; }
    public ApplicationStatus getStatus() { return status; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
    public Instant getNextActionAt() { return nextActionAt; }
    public String getNotes() { return notes; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setPositionTitle(String positionTitle) { this.positionTitle = positionTitle; }
    public void setJobUrl(String jobUrl) { this.jobUrl = jobUrl; }
    public void setSource(String source) { this.source = source; }
    public void setAppliedAt(LocalDate appliedAt) { this.appliedAt = appliedAt; }
    public void setNextActionAt(Instant nextActionAt) { this.nextActionAt = nextActionAt; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setResumeVersionId(UUID resumeVersionId) { this.resumeVersionId = resumeVersionId; }

    public void markAsDeleted() { this.deletedAt = Instant.now(); }
}
