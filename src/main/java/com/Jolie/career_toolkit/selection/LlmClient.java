package com.Jolie.career_toolkit.selection;

/**
 * LLM 的傳輸層介面。
 *
 * **這個階段刻意只有 FakeLlmClient 一個實作，不接任何供應商。**
 *
 * 為什麼還是先把介面定出來：等到真的要接的時候，Service 與 Controller
 * 一行都不用改。而且更重要的是——它逼出一個設計上的分層：
 *
 *   LlmClient      「怎麼把字串送出去、把字串收回來」（傳輸）
 *   BlockSelector  「JD + 積木 → 排序後的選擇 + 理由」（領域）
 *
 * 少了這層分隔，Controller 會直接呼叫某家供應商的 SDK，
 * 之後想換、想加快取、想在測試裡繞過它，每一件都要動到 Controller。
 *
 * 真正要接供應商時的注意事項（現在不做，但先寫下來免得忘記）：
 *   - API key 只從環境變數讀，application.yml 裡不給預設值
 *     （給了預設值就是 key 被 commit 的方式）
 *   - prompt 組裝拆成純函式，才能對輸出做快照測試
 *   - 強制 structured output，回應解析也是純函式，對錄下來的 fixture 測
 *   - 解析失敗要回乾淨的 502，不是 500 stack trace
 */
public interface LlmClient {

    /** 這個實作叫什麼，會記進 selection_calls.selector。 */
    String name();

    /** 現在可以用嗎（例如：有沒有設定 API key）。 */
    boolean isAvailable();

    String complete(String prompt);
}
