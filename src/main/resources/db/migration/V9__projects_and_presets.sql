-- Side project 的作品集資料。
--
-- 為什麼 projects 表與 PROJECT 積木「兩邊都要」：
--   積木存的是可重用的敘述（才能組進履歷、組進答題）
--   這張表存的是作品集才需要的 metadata（連結、技術棧、封面、公開與否）
-- 硬把其中一個當成另一個，不是失去可組合性就是失去結構性。
-- block_id 可為 NULL：有些專案只想放在作品集，不一定要寫進履歷。
CREATE TABLE projects (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    block_id        UUID         REFERENCES blocks(id) ON DELETE SET NULL,

    name            VARCHAR(200) NOT NULL,
    summary         TEXT,
    repo_url        VARCHAR(500),
    demo_url        VARCHAR(500),
    tech_stack      TEXT,
    role            VARCHAR(200),
    started_on      DATE,
    ended_on        DATE,

    -- 公開作品集頁只回傳這一欄為 true 的專案。
    -- 預設 false —— 公開必須是明確的動作，不能是忘了關的預設值。
    is_public       BOOLEAN      NOT NULL DEFAULT FALSE,

    sort_order      INT          NOT NULL DEFAULT 100,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_projects_user ON projects (user_id, sort_order) WHERE deleted_at IS NULL;
CREATE INDEX ix_projects_public ON projects (user_id, sort_order) WHERE is_public AND deleted_at IS NULL;

-- 題型：自我介紹、為什麼選我們、你的優勢…
CREATE TABLE question_types (
    id         SMALLSERIAL PRIMARY KEY,
    code       VARCHAR(40) NOT NULL UNIQUE,
    name_zh    VARCHAR(80) NOT NULL,
    hint       TEXT,
    sort_order INT         NOT NULL DEFAULT 100,
    active     BOOLEAN     NOT NULL DEFAULT TRUE
);

-- 題組：某個題型要用哪幾塊積木組。
--
-- 為什麼不是在 blocks 上加一個 question_type 欄位：
-- 需求明確是「每個題型對應一組積木」，那是有序多對多，一個欄位表達不了。
--
-- 注意這張表跟 resume_versions 結構幾乎相同（都是「有標籤、有順序的積木選集」）。
-- 刻意不合併：它們很快就會分歧（履歷要鎖定與快照，題組要字數上限），
-- 過早合併換來的是每個查詢都要帶一個 discriminator。
CREATE TABLE answer_presets (
    id               UUID        PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_type_id SMALLINT    NOT NULL REFERENCES question_types(id),
    label            VARCHAR(120) NOT NULL,

    -- 目標字數上限。很多表單限 300 或 500 字，組完的答案要直接對照這個數字。
    target_char_limit INT,

    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (user_id, question_type_id, label)
);

CREATE INDEX ix_presets_user ON answer_presets (user_id);

CREATE TABLE answer_preset_blocks (
    preset_id  UUID NOT NULL REFERENCES answer_presets(id) ON DELETE CASCADE,
    block_id   UUID NOT NULL REFERENCES blocks(id) ON DELETE CASCADE,
    -- gap-based，理由跟 resume_blocks 一樣
    sort_order INT  NOT NULL,

    PRIMARY KEY (preset_id, block_id)
);

CREATE INDEX ix_preset_blocks_order ON answer_preset_blocks (preset_id, sort_order);

INSERT INTO question_types (code, name_zh, hint, sort_order) VALUES
    ('SELF_INTRO',      '自我介紹',       '通常 1 分鐘或 300 字。先講現在做什麼，再講最相關的一段經歷。', 10),
    ('WHY_US',          '為什麼選我們',   '要講出對方公司的具體資訊，不能是任何公司都適用的答案。',       20),
    ('STRENGTHS',       '你的優勢',       '挑跟這個職缺最相關的兩到三個，每個都要有例子。',               30),
    ('WEAKNESS',        '你的弱點',       '講真的弱點加上你怎麼處理它。假弱點面試官一聽就知道。',         40),
    ('CAREER_GOAL',     '職涯規劃',       '三年內想成為什麼樣的人，跟這個職位怎麼接上。',                 50),
    ('WHY_LEAVE',       '為什麼離職',     '講往前的理由，不要抱怨前公司。',                               60),
    ('PROJECT_DEEP',    '專案深入問答',   '準備一個能講二十分鐘的專案：決策、取捨、如果重做會怎麼改。',   70),
    ('TEAM_CONFLICT',   '團隊衝突處理',   '要有具體事件、你的行動、以及結果。',                           80),
    ('SALARY',          '期望待遇',       '先問對方的區間，被追問時給一個有根據的範圍。',                 90),
    ('QUESTIONS_FOR_US','你有什麼問題',   '至少準備三個。問團隊怎麼運作，不要只問福利。',                100);
