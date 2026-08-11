package com.Jolie.career_toolkit.interview;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interviews")
public class Interview {

    public enum Stage { HR_SCREEN, TECH, MANAGER, FINAL, OTHER }

    public enum Format { ONSITE, VIDEO, PHONE }

    public enum Status { SCHEDULED, COMPLETED, CANCELLED, RESCHEDULED }

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private Short round;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Format format;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "duration_minutes")
    private Short durationMinutes;

    @Column(length = 300)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String interviewers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Interview() {}

    public Interview(UUID applicationId, short round, Instant scheduledAt) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.round = round;
        this.scheduledAt = scheduledAt;
        this.status = Status.SCHEDULED;
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public Short getRound() { return round; }
    public Stage getStage() { return stage; }
    public Format getFormat() { return format; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Short getDurationMinutes() { return durationMinutes; }
    public String getLocation() { return location; }
    public String getInterviewers() { return interviewers; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStage(Stage stage) { this.stage = stage; }
    public void setFormat(Format format) { this.format = format; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public void setDurationMinutes(Short durationMinutes) { this.durationMinutes = durationMinutes; }
    public void setLocation(String location) { this.location = location; }
    public void setInterviewers(String interviewers) { this.interviewers = interviewers; }
    public void setStatus(Status status) { this.status = status; }
}
