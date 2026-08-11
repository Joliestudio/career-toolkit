-- 選擇器的呼叫紀錄。
--
-- 現在記的是本機關鍵字選擇器的呼叫（零成本），看起來好像沒必要。
-- 但這張表存在的真正理由是：日後接了 LLM 之後，同一張表就能直接比較
-- 「關鍵字選出來的 vs LLM 選出來的」效果與成本。
-- 等到接了 LLM 才開始記，就沒有基準可以比了。
CREATE TABLE selection_calls (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 哪個實作處理的：KEYWORD_V1 / LLM_<model>
    selector      VARCHAR(40)  NOT NULL,
    purpose       VARCHAR(40)  NOT NULL,   -- JD_BLOCK_SELECTION 等

    -- hash(JD + 積木清單)。同樣的輸入直接回快取，不重算也不重新計費。
    request_hash  VARCHAR(64)  NOT NULL,

    -- 關鍵字選擇器沒有 token，留著是為了 LLM 接上來時同一張表可以直接用
    input_tokens  INT,
    output_tokens INT,
    cost_estimate NUMERIC(10,6),

    latency_ms    INT          NOT NULL,
    block_count   INT          NOT NULL,   -- 送進去幾個積木
    selected_count INT         NOT NULL,   -- 選出來幾個

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_selection_calls_user_day ON selection_calls (user_id, created_at DESC);
CREATE INDEX ix_selection_calls_hash ON selection_calls (user_id, request_hash);
