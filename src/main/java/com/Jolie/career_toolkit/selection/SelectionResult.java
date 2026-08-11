package com.Jolie.career_toolkit.selection;

import com.Jolie.career_toolkit.block.BlockType;

import java.util.List;
import java.util.UUID;

public record SelectionResult(
        /** 哪個實作處理的。前端顯示出來，使用者才知道現在跑的是本機還是 LLM。 */
        String selector,
        boolean cached,
        int latencyMs,
        int candidateCount,
        List<Item> items
) {
    public record Item(
            UUID blockId,
            BlockType type,
            String title,
            String content,
            double score,
            /**
             * 為什麼選它。
             *
             * 這一欄是必要的不是裝飾——只給一個排名，使用者沒辦法判斷
             * 「這個推薦到底有沒有道理」，只能盲目相信或盲目忽略。
             * 給出命中的關鍵字，他一眼就能看出這是不是誤判。
             */
            List<String> reasons
    ) {}
}
