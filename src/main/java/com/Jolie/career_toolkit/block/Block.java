package com.Jolie.career_toolkit.block;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "blocks")

public class Block {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BlockType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "char_count", nullable = false)
    private Integer charCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Hibernate 專用的 protected 無參數建構子
    protected Block() {}
        // 真正用來建立物件的建構子
    public Block(UUID userId, BlockType type, String title, String content){
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.charCount = content != null ? content.length() : 0;
    }

    // Getter 方法
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public BlockType getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Integer getCharCount() { return charCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    //Setter 方法
    public void SetTitle(String title) {
        this.title = title;
    }
    
    // 封裝的展現：修改內容時，字數統計跟著聯動更新，不開放獨立的 setCharCount
    public void updateContent(String content) {
        this.content = content;
        this.charCount = content != null ? content.length() : 0;
    }
    public void markAsDeleted() {
        this.deletedAt = Instant.now();
    }
}
