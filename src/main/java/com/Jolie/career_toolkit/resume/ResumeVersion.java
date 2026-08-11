package com.Jolie.career_toolkit.resume;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resume_versions")
public class ResumeVersion {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "target_role", length = 120)
    private String targetRole;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ResumeVersion() {}

    public ResumeVersion(UUID userId, String label, String targetRole) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.label = label;
        this.targetRole = targetRole;
        this.locale = "zh-TW";
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    void lock() {
        this.lockedAt = Instant.now();
    }

    void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getLabel() { return label; }
    public String getTargetRole() { return targetRole; }
    public String getLocale() { return locale; }
    public Instant getLockedAt() { return lockedAt; }
    public UUID getParentId() { return parentId; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setLabel(String label) { this.label = label; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public void setLocale(String locale) { this.locale = locale; }

    public void markAsDeleted() { this.deletedAt = Instant.now(); }
}
