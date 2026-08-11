package com.Jolie.career_toolkit.answers;

import java.util.List;
import java.util.UUID;

/**
 * 組好的答案。
 *
 * charCount 用 code point 算，跟前端的計數器與 Block.charCount 完全一致——
 * 三個地方算出不同數字的話，使用者會在「這裡顯示 299 但送出去被擋」之間反覆卡住。
 */
public record AssembledAnswer(
        UUID presetId,
        String label,
        Integer targetCharLimit,
        List<Part> parts,
        /** 串好的完整文字，可以直接複製貼進對方的表單 */
        String text,
        int charCount
) {
    public record Part(UUID blockId, String title, String content, int sortOrder) {}

    /** 超出上限幾個字。沒設上限就是 0。 */
    public int overBy() {
        return targetCharLimit == null ? 0 : Math.max(0, charCount - targetCharLimit);
    }
}
