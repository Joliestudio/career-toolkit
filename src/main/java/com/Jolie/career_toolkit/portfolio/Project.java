package com.Jolie.career_toolkit.portfolio;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 可為 NULL：有些專案只想放作品集，不一定要寫進履歷。 */
    @Column(name = "block_id")
    private UUID blockId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @Column(name = "demo_url", length = 500)
    private String demoUrl;

    @Column(name = "tech_stack", columnDefinition = "TEXT")
    private String techStack;

    @Column(length = 200)
    private String role;

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    /** 預設 false —— 公開必須是明確的動作，不能是忘了關的預設值。 */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Project() {}

    public Project(UUID userId, String name) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.isPublic = false;
        this.sortOrder = 100;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getBlockId() { return blockId; }
    public String getName() { return name; }
    public String getSummary() { return summary; }
    public String getRepoUrl() { return repoUrl; }
    public String getDemoUrl() { return demoUrl; }
    public String getTechStack() { return techStack; }
    public String getRole() { return role; }
    public LocalDate getStartedOn() { return startedOn; }
    public LocalDate getEndedOn() { return endedOn; }
    public Boolean getIsPublic() { return isPublic; }
    public Integer getSortOrder() { return sortOrder; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBlockId(UUID blockId) { this.blockId = blockId; }
    public void setName(String name) { this.name = name; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public void setDemoUrl(String demoUrl) { this.demoUrl = demoUrl; }
    public void setTechStack(String techStack) { this.techStack = techStack; }
    public void setRole(String role) { this.role = role; }
    public void setStartedOn(LocalDate startedOn) { this.startedOn = startedOn; }
    public void setEndedOn(LocalDate endedOn) { this.endedOn = endedOn; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public void markAsDeleted() { this.deletedAt = Instant.now(); }
}
