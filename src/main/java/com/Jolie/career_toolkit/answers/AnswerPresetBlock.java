package com.Jolie.career_toolkit.answers;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "answer_preset_blocks")
@IdClass(AnswerPresetBlock.Key.class)
public class AnswerPresetBlock {

    public static class Key implements Serializable {
        private UUID presetId;
        private UUID blockId;

        public Key() {}

        public Key(UUID presetId, UUID blockId) {
            this.presetId = presetId;
            this.blockId = blockId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(presetId, key.presetId) && Objects.equals(blockId, key.blockId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(presetId, blockId);
        }
    }

    @Id
    @Column(name = "preset_id")
    private UUID presetId;

    @Id
    @Column(name = "block_id")
    private UUID blockId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected AnswerPresetBlock() {}

    public AnswerPresetBlock(UUID presetId, UUID blockId, int sortOrder) {
        this.presetId = presetId;
        this.blockId = blockId;
        this.sortOrder = sortOrder;
    }

    public UUID getPresetId() { return presetId; }
    public UUID getBlockId() { return blockId; }
    public Integer getSortOrder() { return sortOrder; }

    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
