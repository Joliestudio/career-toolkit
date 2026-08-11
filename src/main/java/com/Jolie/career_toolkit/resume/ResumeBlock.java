package com.Jolie.career_toolkit.resume;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 履歷版本與積木的關聯，帶著排序與客製化內容。
 *
 * 用複合主鍵（version + block）而不是另外給一個 id：
 * 「同一個積木在同一份履歷裡出現兩次」本來就不該發生，讓主鍵直接擋掉它，
 * 比事後在 Service 裡檢查可靠。
 */
@Entity
@Table(name = "resume_blocks")
@IdClass(ResumeBlock.Key.class)
public class ResumeBlock {

    /** 複合主鍵。必須 Serializable、必須有 equals/hashCode，這是 JPA 的硬性要求。 */
    public static class Key implements Serializable {
        private UUID resumeVersionId;
        private UUID blockId;

        public Key() {}

        public Key(UUID resumeVersionId, UUID blockId) {
            this.resumeVersionId = resumeVersionId;
            this.blockId = blockId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(resumeVersionId, key.resumeVersionId)
                    && Objects.equals(blockId, key.blockId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resumeVersionId, blockId);
        }
    }

    @Id
    @Column(name = "resume_version_id")
    private UUID resumeVersionId;

    @Id
    @Column(name = "block_id")
    private UUID blockId;

    /** gap-based：100 / 200 / 300。插到中間就給 150。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(length = 40)
    private String section;

    /** 針對這份履歷客製化的措辭，不影響原本的 block。 */
    @Column(name = "content_override", columnDefinition = "TEXT")
    private String contentOverride;

    /** 鎖定當下凍結的內容。鎖定後渲染一律走這裡。 */
    @Column(name = "content_snapshot", columnDefinition = "TEXT")
    private String contentSnapshot;

    protected ResumeBlock() {}

    public ResumeBlock(UUID resumeVersionId, UUID blockId, int sortOrder) {
        this.resumeVersionId = resumeVersionId;
        this.blockId = blockId;
        this.sortOrder = sortOrder;
    }

    /**
     * 這份履歷實際要顯示的內容，依優先序：
     *   1. 快照（已鎖定 → 當時凍結的就是事實）
     *   2. 客製化措辭
     *   3. 原始 block 內容
     */
    public String effectiveContent(String blockContent) {
        if (contentSnapshot != null) return contentSnapshot;
        if (contentOverride != null) return contentOverride;
        return blockContent;
    }

    void freeze(String content) {
        this.contentSnapshot = content;
    }

    public UUID getResumeVersionId() { return resumeVersionId; }
    public UUID getBlockId() { return blockId; }
    public Integer getSortOrder() { return sortOrder; }
    public String getSection() { return section; }
    public String getContentOverride() { return contentOverride; }
    public String getContentSnapshot() { return contentSnapshot; }

    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public void setSection(String section) { this.section = section; }
    public void setContentOverride(String contentOverride) { this.contentOverride = contentOverride; }
}
