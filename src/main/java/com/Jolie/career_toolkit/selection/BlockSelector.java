package com.Jolie.career_toolkit.selection;

import com.Jolie.career_toolkit.block.Block;

import java.util.List;
import java.util.UUID;

/**
 * 領域層介面：職缺描述 + 積木清單 → 排序後的選擇 + 理由。
 *
 * **產品立場（即使之後接了 LLM 也不變）**：
 * 選擇器負責「挑選與排序」你的積木，**不負責「寫」積木**。
 *
 * 這不是技術限制，是刻意的設計。積木維持你自己的語氣，幻覺就沒有攻擊面，
 * 失敗模式是「排序不好」而不是「捏造經歷」——後者在求職文件上是會被開除的謊言。
 * 而且排序不好你一眼就看得出來，捏造的經歷你可能到面試當場才發現。
 */
public interface BlockSelector {

    /** 這個實作叫什麼，會記進 selection_calls.selector。 */
    String name();

    List<Selection> select(String jobDescription, List<Block> candidates);

    /**
     * 一個被選中的積木。
     *
     * @param score       排序用的分數，不是機率，所以刻意不叫 confidence
     * @param reasons     為什麼選它。**這一欄是必要的不是裝飾**——
     *                    使用者要判斷「這個推薦到底有沒有道理」，
     *                    只給一個排名沒辦法判斷，只能盲目相信或盲目忽略。
     */
    record Selection(UUID blockId, double score, List<String> reasons) {}
}
