package com.Jolie.career_toolkit.block;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    // ddl-auto: validate 是單向檢查——它確認 entity 的屬性在 DB 有對應欄位，
    // 但不會反過來檢查 DB 欄位有沒有被映射。tags 之前沒映射卻通過驗證就是這個原因。
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

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
    public Block(UUID userId, BlockType type, String title, String content) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.charCount = countChars(content);
    }

    /**
     * 用 code point 而不是 String.length() 計算字數。
     *
     * String.length() 回傳的是 UTF-16 code unit 數量。BMP 內的中文是 1:1 所以看不出差別，
     * 但 emoji 這類補充平面字元會被算成 2。字數計數器是本產品的招牌功能，
     * 一旦使用者開始信任這個數字，事後再改語意會讓所有既有數字集體偏移。
     */
    private static int countChars(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    // Getter 方法
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public BlockType getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Integer getCharCount() { return charCount; }
    public List<String> getTags() { return tags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    //Setter 方法
    public void setTitle(String title) {
        this.title = title;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    // 封裝的展現：修改內容時，字數統計跟著聯動更新，不開放獨立的 setCharCount
    public void updateContent(String content) {
        this.content = content;
        this.charCount = countChars(content);
    }

    public void markAsDeleted() {
        this.deletedAt = Instant.now();
    }
}
