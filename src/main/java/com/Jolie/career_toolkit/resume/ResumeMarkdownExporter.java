package com.Jolie.career_toolkit.resume;

import com.Jolie.career_toolkit.block.BlockType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把渲染結果轉成 Markdown。
 *
 * 純函式：輸入 (版本, 已渲染的積木) → 輸出字串，不碰資料庫也不碰 HTTP。
 * 所以測試可以直接比對整份輸出，不需要啟動任何東西。
 */
@Component
public class ResumeMarkdownExporter {

    private static final Map<BlockType, String> SECTION_NAMES = Map.of(
            BlockType.EXPERIENCE, "工作經歷",
            BlockType.SKILL, "技能",
            BlockType.PROJECT, "專案",
            BlockType.EDUCATION, "學歷",
            BlockType.CERTIFICATION, "證照",
            BlockType.ANSWER_SNIPPET, "其他"
    );

    public String toMarkdown(ResumeVersion version, List<RenderedBlock> blocks) {
        StringBuilder out = new StringBuilder();

        out.append("# ").append(version.getLabel()).append('\n');
        if (version.getTargetRole() != null && !version.getTargetRole().isBlank()) {
            out.append('\n').append("**目標職位**：").append(version.getTargetRole()).append('\n');
        }

        // 依 section 分組；沒有指定 section 的就用積木類型當標題。
        // 用 LinkedHashMap 保留第一次出現的順序——分組不該打亂使用者排好的順序。
        Map<String, List<RenderedBlock>> grouped = new LinkedHashMap<>();
        for (RenderedBlock block : blocks) {
            String section = block.section() != null && !block.section().isBlank()
                    ? block.section()
                    : SECTION_NAMES.getOrDefault(block.type(), "其他");
            grouped.computeIfAbsent(section, k -> new java.util.ArrayList<>()).add(block);
        }

        for (Map.Entry<String, List<RenderedBlock>> entry : grouped.entrySet()) {
            out.append("\n## ").append(entry.getKey()).append('\n');

            for (RenderedBlock block : entry.getValue()) {
                out.append("\n### ").append(block.title()).append('\n');
                out.append('\n').append(block.content().strip()).append('\n');
            }
        }

        return out.toString();
    }
}
