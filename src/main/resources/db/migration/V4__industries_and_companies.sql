-- 產業用 lookup table 而不是 enum。
--
-- 判斷準則：程式會 branch 且集合封閉 → enum；只是使用者可擴充的標籤 → 表。
-- status 與 decision 會 branch（狀態機要判斷合法轉換），所以是 enum。
-- 產業從來不會 branch，而且你會想加「半導體設備」而不想為此跑一次 migration 再重新部署。
--
-- 這是整個 schema 裡最貴的反悔決定：從 enum 換成表要改動所有引用它的地方，
-- 而且既有資料要重新對應。
CREATE TABLE industries (
    id         SMALLSERIAL PRIMARY KEY,
    code       VARCHAR(40)  NOT NULL UNIQUE,
    name_zh    VARCHAR(60)  NOT NULL,
    sort_order INT          NOT NULL DEFAULT 100,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

-- 公司設計為全域共用，不是每個使用者一份。
--
-- 理由：願景裡有後台管理與產業分類，那只有在共享目錄上才有意義。
-- 使用者可以自由新增（created_by_user_id 記錄是誰建的），
-- 管理員再合併重複的並標記 verified。
--
-- 這也是為什麼 companies 沒有 user_id——它不屬於任何人。
-- 所有權是在 applications 那一層才出現的。
CREATE TABLE companies (
    id                 UUID         PRIMARY KEY,
    name               VARCHAR(200) NOT NULL,
    industry_id        SMALLINT     REFERENCES industries(id),
    website            VARCHAR(300),
    notes              TEXT,
    created_by_user_id UUID         REFERENCES users(id) ON DELETE SET NULL,
    verified           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 用 lower(name) 建唯一索引：「台積電」和「台積電 」要能擋掉，
-- 英文公司名的大小寫差異也不該變成兩筆。
CREATE UNIQUE INDEX ux_companies_name ON companies (lower(name));
CREATE INDEX ix_companies_industry ON companies (industry_id);

INSERT INTO industries (code, name_zh, sort_order) VALUES
    ('SOFTWARE',        '軟體／網路',     10),
    ('SEMICONDUCTOR',   '半導體',         20),
    ('HARDWARE',        '電子硬體',       30),
    ('FINANCE',         '金融',           40),
    ('ECOMMERCE',       '電商／零售',     50),
    ('GAMING',          '遊戲',           60),
    ('BIOTECH',         '生技醫療',       70),
    ('MANUFACTURING',   '傳統製造',       80),
    ('EDUCATION',       '教育',           90),
    ('MEDIA',           '媒體／內容',    100),
    ('CONSULTING',      '顧問',          110),
    ('GOVERNMENT',      '政府／法人',    120),
    ('OTHER',           '其他',          999);
