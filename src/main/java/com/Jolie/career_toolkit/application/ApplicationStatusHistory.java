package com.Jolie.career_toolkit.application;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * 每一次狀態轉換都留一筆。
 *
 * 這張表的資料無法事後補——「這家從初篩到面談花了幾天」只有當初記下來才答得出來。
 * 所以即使 UI 還沒有要讀它，也要從第一天就開始寫入。
 */
@Entity
@Table(name = "application_status_history")
public class ApplicationStatusHistory {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    /** 第一筆是 NULL：從「不存在」到 DRAFT 或 APPLIED。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private ApplicationStatus toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(columnDefinition = "TEXT")
    private String note;

    protected ApplicationStatusHistory() {}

    public ApplicationStatusHistory(UUID applicationId,
                                    ApplicationStatus fromStatus,
                                    ApplicationStatus toStatus,
                                    String note) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.changedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public ApplicationStatus getFromStatus() { return fromStatus; }
    public ApplicationStatus getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
    public String getNote() { return note; }
}
