package com.Jolie.career_toolkit.selection;

import org.springframework.stereotype.Component;

/**
 * 目前唯一的 LlmClient 實作。
 *
 * 它不會被真的用到——BlockSelector 現在的預設實作是純本機的關鍵字比對，
 * 完全不經過 LLM。這個類別的作用是：
 *   1. 讓 isAvailable() 這條路徑有東西可以測（整套測試在沒有 key 的狀態下必須全綠）
 *   2. 之後接真實供應商時，介面已經被實際使用過，不是紙上談兵
 *
 * 刻意回傳一個明確的錯誤而不是假資料：假資料會讓「忘記接真的實作」
 * 這件事一路潛伏到正式環境才爆。
 */
@Component
public class FakeLlmClient implements LlmClient {

    @Override
    public String name() {
        return "FAKE";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String complete(String prompt) {
        throw new UnsupportedOperationException(
                "目前沒有接任何 LLM 供應商。積木選擇改用 KeywordBlockSelector（純本機、零成本）。");
    }
}
