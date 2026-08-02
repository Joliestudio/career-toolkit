package com.Jolie.career_toolkit.block.dto;

import com.Jolie.career_toolkit.block.BlockType;
import com.Jolie.career_toolkit.block.Block;
import java.time.Instant;
import java.util.UUID;

public record BlockResponse (
    UUID id,
    String title,
    String content,
    BlockType type,
    int charCount,
    Instant updatedAt
) {
    // 將 Entity 轉換為 DTO 的工廠方法
    public static BlockResponse from(Block block) {
        return new BlockResponse(
            block.getId(),
            block.getTitle(),
            block.getContent(),
            block.getType(),
            block.getCharCount(),
            block.getUpdatedAt()
        );
    }
}
