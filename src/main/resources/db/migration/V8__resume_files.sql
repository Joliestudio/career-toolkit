-- 上傳進來要解析的履歷檔。
--
-- 這跟 V7 的 resume_versions 是兩件完全不同的事，只有「resume」這個詞相同：
--   resume_files    = 使用者上傳的二進位檔（輸入，要解析的原料）
--   resume_versions = 使用者組裝出來的成品（輸出）
-- 把它們合成一張表是很容易犯的錯，之後每個查詢都要帶一個「這是哪一種」的判斷。
CREATE TABLE resume_files (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 只用來顯示。絕對不能拿它當檔案系統路徑——那是路徑穿越漏洞。
    original_name VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,

    -- 內容雜湊。同一個人重複上傳同一份檔案時直接回既有那筆，
    -- 不要在積木庫裡堆出一堆重複的候選。
    --
    -- 用 VARCHAR(64) 不用 CHAR(64)：Postgres 的 CHAR(n) 是 bpchar，
    -- Hibernate 對 String 欄位期待的是 varchar，ddl-auto: validate 會直接擋下來。
    -- （V6 的 offers.currency 犯過同一個錯。結論很簡單：JPA 的 String 欄位不要用 CHAR(n)。）
    sha256        VARCHAR(64)  NOT NULL,

    -- 伺服器自己產生的儲存 key，格式是 {userId}/{uuid}.{ext}。
    -- 跟使用者給的檔名完全無關。
    storage_key   VARCHAR(400) NOT NULL,

    parse_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING/PARSED/NO_TEXT_LAYER/FAILED
    parse_error   TEXT,
    extracted_text TEXT,

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (user_id, sha256)
);

CREATE INDEX ix_resume_files_user ON resume_files (user_id, created_at DESC);

-- 解析結果一律先變成「候選」，由人工審核後才升級成 Block。
--
-- 為什麼不直接產生 Block：
--   1. 擋掉垃圾進入積木庫——解析出來的東西品質參差，尤其中文 PDF
--   2. 留下一組有標籤的資料，日後要比較「字典比對 vs LLM 抽取」時，
--      靠 extractor 欄位就能在同一份履歷上直接對照
--   3. 被拒絕的候選會被記住，重新解析同一個檔案不會讓它們復活
CREATE TABLE extraction_candidates (
    id             UUID         PRIMARY KEY,
    resume_file_id UUID         NOT NULL REFERENCES resume_files(id) ON DELETE CASCADE,
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    kind           VARCHAR(20)  NOT NULL,   -- SKILL / CERTIFICATION
    value          TEXT         NOT NULL,   -- 原文中出現的樣子
    normalized     TEXT         NOT NULL,   -- 正規化後的標準名稱（JS → JavaScript）
    confidence     NUMERIC(4,3),

    -- 誰抽出來的。之後接 LLM 時，同一份履歷可以同時有 DICTIONARY_V1 與 LLM_xxx 的候選，
    -- 直接比較哪個抓得比較準。沒有這一欄就沒辦法評估「LLM 到底值不值那筆錢」。
    extractor      VARCHAR(40)  NOT NULL,

    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING/ACCEPTED/REJECTED
    block_id       UUID         REFERENCES blocks(id),       -- 接受後產生的積木

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- 同一個檔案裡同一個正規化名稱只留一筆，不要因為原文出現三次就產生三個候選
    UNIQUE (resume_file_id, kind, normalized, extractor)
);

CREATE INDEX ix_candidates_file ON extraction_candidates (resume_file_id, status);
CREATE INDEX ix_candidates_user ON extraction_candidates (user_id, status);
