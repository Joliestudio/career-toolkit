CREATE TABLE interviews (
    id               UUID        PRIMARY KEY,
    application_id   UUID        NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    round            SMALLINT    NOT NULL,
    stage            VARCHAR(30),          -- HR_SCREEN / TECH / MANAGER / FINAL / OTHER
    format           VARCHAR(20),          -- ONSITE / VIDEO / PHONE
    scheduled_at     TIMESTAMPTZ,
    duration_minutes SMALLINT,
    location         VARCHAR(300),
    interviewers     TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED/COMPLETED/CANCELLED/RESCHEDULED
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (application_id, round)
);

-- 支撐「我未來有幾間面試」這個查詢。
-- 部分索引只收 SCHEDULED 的，已完成或取消的不佔索引空間。
CREATE INDEX ix_interviews_upcoming ON interviews (scheduled_at) WHERE status = 'SCHEDULED';
CREATE INDEX ix_interviews_application ON interviews (application_id, round);

-- 面試檢討。
--
-- 原 ERD 只有 process_notes 一欄，但需求講得很具體：
-- 「紀錄今日的面試流程、工作上會遇到什麼、自己哪部分需要再調整」——那是三件不同的事，
-- 全部塞進同一個 TEXT 欄位之後就沒辦法分別查詢或比較。
CREATE TABLE reviews (
    id              UUID        PRIMARY KEY,

    -- UNIQUE 才真的讓「一場面試對一份檢討」成立。
    -- ERD 上標的 1:1 只是圖上的標記，沒有這個約束的話資料庫不會擋。
    interview_id    UUID        NOT NULL UNIQUE REFERENCES interviews(id) ON DELETE CASCADE,

    process_notes   TEXT,       -- 今天的面試流程
    job_reality     TEXT,       -- 這份工作實際在做什麼
    gaps            TEXT,       -- 我需要調整的部分

    -- 被問到的題目。這一欄把 P3 的檢討接回 P6 的題型對應：
    -- 真正被問過的題目，變成下次要準備的題組。
    questions_asked TEXT,
    red_flags       TEXT,

    -- rating 和 interest_level 一定要分開。
    -- 「面得好不好」跟「我多想去」經常是相反的——面試順利但公司文化有疑慮，
    -- 或是面得很掙扎但真的很想要那個機會。
    -- 合成一個分數之後，收到 offer 要做決策時就完全用不上了。
    rating          SMALLINT CHECK (rating BETWEEN 1 AND 5),
    interest_level  SMALLINT CHECK (interest_level BETWEEN 1 AND 5),

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE offers (
    id                UUID          PRIMARY KEY,

    -- UNIQUE 讓 ERD 上的 0..1 真的成立
    application_id    UUID          NOT NULL UNIQUE REFERENCES applications(id) ON DELETE CASCADE,

    -- 金額用 NUMERIC 不用 INT。錢不該用整數型別扛，
    -- 而且之後要換算不同幣別或計算平均時，整數的截斷會累積成明顯的誤差。
    base_salary       NUMERIC(12,2),

    -- 用 VARCHAR(3) 不用 CHAR(3)：Postgres 的 CHAR(n) 是空白填充的，
    -- 存進去的 'TWD' 讀出來在某些情境會帶著尾隨空白，比較與 trim 都會變得意外。
    -- 固定長度在這裡沒有帶來任何好處。
    currency          VARCHAR(3)    NOT NULL DEFAULT 'TWD',
    salary_period     VARCHAR(10)   NOT NULL DEFAULT 'MONTHLY',   -- MONTHLY / ANNUAL

    -- 台灣的 offer 沒有保證年薪根本沒辦法比較：
    -- 月薪 50k × 14 個月 ≠ 月薪 55k × 12 個月。
    -- 而比較 offer 正是這張表存在的理由。
    guaranteed_months NUMERIC(4,1),
    bonus_note        TEXT,

    offered_at        DATE,
    reply_deadline    DATE,

    -- EXPIRED 是獨立於「婉拒」的結果——死線過了沒回覆，跟主動婉拒是兩件事，
    -- 之後回頭看自己的決策時這個差別很重要。
    decision          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',   -- PENDING/ACCEPTED/DECLINED/EXPIRED
    decided_at        DATE,
    decline_reason    TEXT,

    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ix_offers_deadline ON offers (reply_deadline) WHERE decision = 'PENDING';
