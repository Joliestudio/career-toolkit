package com.Jolie.career_toolkit.company;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 公司是全域共用的目錄，不屬於任何使用者——所以這張表沒有 user_id。
 * 所有權是到 applications 那一層才出現。
 *
 * created_by_user_id 只是記錄是誰第一個建的，方便管理員合併重複資料，
 * 不是所有權欄位。
 */
@Entity
@Table(name = "companies")
public class Company {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "industry_id")
    private Short industryId;

    @Column(length = 300)
    private String website;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(nullable = false)
    private Boolean verified;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Company() {}

    public Company(String name, Short industryId, UUID createdByUserId) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.industryId = industryId;
        this.createdByUserId = createdByUserId;
        this.verified = false;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Short getIndustryId() { return industryId; }
    public String getWebsite() { return website; }
    public String getNotes() { return notes; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Boolean getVerified() { return verified; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setIndustryId(Short industryId) { this.industryId = industryId; }
    public void setWebsite(String website) { this.website = website; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setVerified(Boolean verified) { this.verified = verified; }
}
