-- 投遞紀錄。使用者說這是「把它從小工具變成求職管理工具的那一步」。
CREATE TABLE applications (
    id                UUID         PRIMARY KEY,

    -- user_id 是實體欄位，不是靠 join 推導出來的。
    --
    -- 原本的 ERD 只能透過 resume_version_id 反查使用者，那有兩個問題：
    --   1. 透過公司官網手動填表投遞時根本沒有履歷版本（所以下面那欄必須可為 NULL），
    --      此時該筆資料就無主、查不到、刪不掉。
    --   2. 每次權限檢查都變成 join，漏一次 join 就是跨帳號讀取。
    -- 所有權欄位必須是每張表上一個有索引的實體欄位。
    user_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    company_id        UUID         NOT NULL REFERENCES companies(id),

    -- 必須可為 NULL：手動填表投遞時沒有對應的履歷版本。
    -- FK 等到 P4 建了 resume_versions 之後再補上（含 user_id 的複合 FK，
    -- 讓 Postgres 從結構上擋掉「掛到別人的履歷版本」）。
    resume_version_id UUID,

    -- 不叫 position：POSITION 是 SQL 的保留字（字串函式），
    -- 雖然加引號能用，但每次寫查詢都要記得加引號是不必要的負擔。
    position_title    VARCHAR(200) NOT NULL,
    job_url           VARCHAR(500),
    source            VARCHAR(40),          -- 104 / LINKEDIN / REFERRAL / DIRECT / OTHER

    applied_at        DATE,                 -- DRAFT 階段還沒投，所以可為 NULL
    status            VARCHAR(30)  NOT NULL,
    status_changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    next_action_at    TIMESTAMPTZ,          -- 支撐「該追蹤了」的提醒
    notes             TEXT,

    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_applications_user_status ON applications (user_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_applications_company     ON applications (company_id);
CREATE INDEX ix_applications_next_action ON applications (user_id, next_action_at)
    WHERE deleted_at IS NULL AND next_action_at IS NOT NULL;

-- 這張表不加就永遠補不回來。
--
-- 「這家從初篩到面談花幾天？」「我都卡在哪一關？」——
-- 只有當初把每一次狀態轉換記下來才答得出來。一年後才想到要加，那一年的資料就是沒了。
--
-- 昂貴的不是難遷移的決定，是會毀掉資訊的決定。schema 形狀可以重構，沒記下來的歷史不行。
-- 所以即使 UI 還沒有要讀它，現在就要開始寫入。
CREATE TABLE application_status_history (
    id             UUID        PRIMARY KEY,
    application_id UUID        NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    from_status    VARCHAR(30),            -- 第一筆是 NULL（從無到 DRAFT/APPLIED）
    to_status      VARCHAR(30) NOT NULL,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    note           TEXT
);

CREATE INDEX ix_status_history_application ON application_status_history (application_id, changed_at);
