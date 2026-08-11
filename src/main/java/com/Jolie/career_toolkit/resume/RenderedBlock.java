package com.Jolie.career_toolkit.resume;

import com.Jolie.career_toolkit.block.BlockType;

import java.util.UUID;

/**
 * 渲染後的一個積木：已經套用過「快照 → 客製化 → 原文」的優先序。
 *
 * 匯出（Markdown / 列印 HTML）與預覽都走這個型別，
 * 這樣「畫面上看到的」跟「匯出的」不可能不一致——因為它們是同一份資料。
 */
public record RenderedBlock(
        UUID blockId,
        BlockType type,
        String title,
        String content,
        String section,
        int sortOrder,
        /** 這一段是不是被客製化過（UI 上標示出來，免得使用者以為改了原始積木） */
        boolean overridden
) {}
