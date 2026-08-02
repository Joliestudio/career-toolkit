package com.Jolie.career_toolkit.block;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 純單元測試，不需要 Spring 也不需要資料庫——建構子注入的好處之一就是能直接 new。
 *
 * 字數計數器是本產品的招牌功能。這組測試把「用 code point 不是 code unit」這個決定釘住，
 * 免得日後有人為了圖方便改回 String.length()。
 */
class BlockCharCountTest {

    private static final UUID USER = UUID.randomUUID();

    @Test
    void charCount_shouldCountCjkCharactersAsOneEach() {
        Block block = new Block(USER, BlockType.SKILL, "標題", "後端工程師");

        assertThat(block.getCharCount()).isEqualTo(5);
    }

    @Test
    void charCount_shouldCountEmojiAsOne() {
        String rocket = "🚀";

        // 先把問題本身測出來：String.length() 回傳的是 UTF-16 code unit 數量，
        // 補充平面的字元（emoji）會被算成 2。
        assertThat(rocket.length()).isEqualTo(2);

        Block block = new Block(USER, BlockType.SKILL, "標題", rocket);

        // 使用者眼裡這是一個字，表單的字數上限也是這樣算的。
        assertThat(block.getCharCount()).isEqualTo(1);
    }

    @Test
    void updateContent_shouldKeepCharCountInSync() {
        Block block = new Block(USER, BlockType.SKILL, "標題", "abc");
        assertThat(block.getCharCount()).isEqualTo(3);

        block.updateContent("後端工程師🚀");

        assertThat(block.getCharCount()).isEqualTo(6);
    }

    @Test
    void charCount_shouldBeZeroForNullContent() {
        Block block = new Block(USER, BlockType.SKILL, "標題", null);

        assertThat(block.getCharCount()).isZero();
    }
}
