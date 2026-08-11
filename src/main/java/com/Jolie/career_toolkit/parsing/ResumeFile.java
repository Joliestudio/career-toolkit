package com.Jolie.career_toolkit.parsing;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resume_files")
public class ResumeFile {

    public enum ParseStatus {
        PENDING,
        PARSED,
        /**
         * 抽不出任何文字。
         *
         * 這個狀態一定要跟 FAILED 分開：掃描檔（整頁都是圖片）沒有文字層，
         * 那不是錯誤，是這份檔案本來就沒有東西可以抽。
         * 混成一個狀態的話，使用者只會看到「解析失敗」然後不知道該怎麼辦——
         * 而正確的建議是「這是掃描檔，請提供有文字層的版本」。
         */
        NO_TEXT_LAYER,
        FAILED
    }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 只用來顯示。絕不能拿它當檔案系統路徑。 */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 400)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 20)
    private ParseStatus parseStatus;

    @Column(name = "parse_error", columnDefinition = "TEXT")
    private String parseError;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ResumeFile() {}

    public ResumeFile(UUID userId, String originalName, String contentType,
                      long sizeBytes, String sha256, String storageKey) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.parseStatus = ParseStatus.PENDING;
    }

    public void markParsed(String text) {
        this.extractedText = text;
        this.parseStatus = ParseStatus.PARSED;
        this.parseError = null;
    }

    public void markNoTextLayer() {
        this.parseStatus = ParseStatus.NO_TEXT_LAYER;
        this.parseError = "這個檔案沒有文字層（很可能是掃描檔或純圖片的 PDF）。"
                + "請提供可以複製文字的版本。";
    }

    public void markFailed(String reason) {
        this.parseStatus = ParseStatus.FAILED;
        this.parseError = reason;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public ParseStatus getParseStatus() { return parseStatus; }
    public String getParseError() { return parseError; }
    public String getExtractedText() { return extractedText; }
    public Instant getCreatedAt() { return createdAt; }
}
