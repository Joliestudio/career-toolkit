-- 履歷版本：一份針對特定職位組出來的積木選集。
--
-- 注意這跟 P5 要加的 resume_files 是兩件完全不同的事：
-- resume_files 是「上傳進來要解析的二進位檔」（輸入），
-- resume_versions 是「你組裝出來的成品」（輸出）。它們只有 resume 這個詞相同。
CREATE TABLE resume_versions (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label       VARCHAR(120) NOT NULL,
    target_role VARCHAR(120),
    locale      VARCHAR(10)  NOT NULL DEFAULT 'zh-TW',

    -- 鎖定時間。設定之後這份履歷就不再跟著底層 block 變動，改從快照渲染。
    --
    -- 沒有這個機制的話：改一個 block，所有過去的履歷版本會「靜默」跟著變。
    -- 「我三月寄給台積電的版本」會在你不知情的狀況下變成謊言，而且無法還原。
    -- 這是整個 schema 裡少數「不做就永遠補不回來」的東西之一。
    locked_at   TIMESTAMPTZ,

    -- 從哪一版複製出來的。求職時「針對這家改一點」是常態，
    -- 記下來源之後才看得出各版本之間的演化關係。
    parent_id   UUID         REFERENCES resume_versions(id) ON DELETE SET NULL,

    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- 這個看起來多餘的唯一約束（id 已經是 PK）是為了讓下面的複合外鍵成立。
    UNIQUE (user_id, id)
);

CREATE INDEX ix_resume_versions_user ON resume_versions (user_id) WHERE deleted_at IS NULL;

CREATE TABLE resume_blocks (
    resume_version_id UUID NOT NULL REFERENCES resume_versions(id) ON DELETE CASCADE,
    block_id          UUID NOT NULL REFERENCES blocks(id),

    -- gap-based 排序：100 / 200 / 300。要把某項插到中間就給 150，不用重排全部。
    --
    -- 刻意「不」加 UNIQUE (resume_version_id, sort_order)：
    -- 一般的唯一約束會讓拖曳排序在交換到一半時就失敗（A 要變 B 的值，但 B 還在），
    -- 而 deferrable constraint 的複雜度遠超過這個問題本身。
    sort_order        INT  NOT NULL,

    section           VARCHAR(40),

    -- 針對這一份履歷客製化的措辭，不影響原本的 block。
    -- 沒有這一欄的話，「同一段經歷換個說法」只能複製出第二個 block，
    -- 然後兩份就會各自漂移，再也對不起來。
    content_override  TEXT,

    -- 鎖定當下凍結的內容。鎖定後渲染一律走這裡。
    content_snapshot  TEXT,

    PRIMARY KEY (resume_version_id, block_id)
);

CREATE INDEX ix_resume_blocks_order ON resume_blocks (resume_version_id, sort_order);

-- P3 刻意延後的複合外鍵，現在 resume_versions 存在了才補得上。
--
-- 它讓「把投遞掛到別人的履歷版本」變成資料庫層面不可能，
-- 而不是靠 Service 每次記得檢查。單純的 REFERENCES resume_versions(id) 做不到這件事——
-- 那只保證「那個版本存在」，不保證「是你的」。
ALTER TABLE applications
    ADD CONSTRAINT fk_applications_resume_version
    FOREIGN KEY (user_id, resume_version_id)
    REFERENCES resume_versions (user_id, id);
