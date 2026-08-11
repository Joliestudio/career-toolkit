# 驗收清單

每一條都附上「怎麼自己重跑一次」的指令。**沒辦法自己重現的驗收等於沒有驗收** ——
所以這份文件不寫「已通過」，只寫「這樣跑，應該看到什麼」。

---

## 1. 測試不依賴任何外部環境

```bash
docker compose stop db
./mvnw.cmd clean verify
```

**應該看到**：`Tests run: 165, Failures: 0, Errors: 0` 且 `BUILD SUCCESS`。

本機資料庫是關掉的 —— 測試用 Testcontainers 自己起一個 PostgreSQL。
這條同時證明了 CI 會過：GitHub runner 上沒有 `localhost:5435`。

跑完記得 `docker compose start db`。

---

## 2. 一鍵起整套

```bash
cp .env.example .env      # 第一次才需要，然後把 DB_PASSWORD 換掉
docker compose up --build -d
docker compose ps
```

**應該看到**：`ct-app` 與 `ct-postgres` 都是 `Up ... (healthy)`。

兩個 port 都綁在 `127.0.0.1`（`127.0.0.1:8082->8080`、`127.0.0.1:5435->5432`），
不是 `0.0.0.0` —— 同網段的人連不進來。

---

## 3. 缺少 DB_PASSWORD 時應該啟動失敗

```bash
docker run --rm career-toolkit-app 2>&1 | grep -i "DB_PASSWORD"
```

**應該看到**：因為找不到 `DB_PASSWORD` 而啟動失敗。

這是**預期行為不是 bug**。`application.yml` 刻意沒給預設值 ——
有預設值的話，環境變數漏設時會靜默退回一個公開在 GitHub 上的密碼，
而「用弱密碼啟動成功」比「啟動失敗」危險得多，因為沒有人會發現。

---

## 4. 完整的使用路徑

瀏覽器開 `http://localhost:8082`，依序走完：

| 步驟 | 分頁 | 要確認的事 |
|---|---|---|
| 註冊 → 登入 | — | 重整之後仍在登入狀態 |
| 上傳履歷 | 匯入 | 掃描檔要明確回報「沒有文字層」，不是假裝解析成功 |
| 接受幾個候選 | 匯入 | 接受後「積木」分頁多出對應的積木，標題是正規名稱（`k8s` → `Kubernetes`） |
| 建一塊積木 | 積木 | 字數計數器：貼中文 + 一個 emoji，數字要跟目標網站一致 |
| 組一份履歷 | 履歷 | 拖曳排序，重整後順序保持 |
| **鎖定履歷** | 履歷 | 鎖定後回「積木」分頁改內容 → 回來看履歷，內容**不變** |
| 匯出 | 履歷 | 下載 Markdown；「列印」只印履歷內容，不印導覽列 |
| 貼一份 JD | 配對 JD | 選出的積木有排序、有「命中了哪些詞」 |
| 建投遞 | 投遞 | 狀態下拉只出現合法選項 |
| 改狀態 | 投遞 | 試著跳過中間狀態 → 400，訊息說得出可以改成什麼 |
| 排面試、寫檢討 | 投遞 | rating 與 interest 是兩個獨立的滑桿 |
| 記 offer | 投遞 | 月薪 50000 × 保證 14 個月 → 年總額顯示 700,000 |
| 婉拒 offer | 投遞 | 投遞狀態連動變「已婉拒」，理由存下來 |
| 做一個題組 | 題組 | 組完的答案顯示字數並可一鍵複製 |
| 公開一個專案 | 作品集 | 複製公開連結，**用無痕視窗開** → 看得到；私人的專案看不到 |

---

## 5. 跨帳號隔離（最重要的一條）

用第二個帳號對第一個帳號的每一種資源打 GET / PATCH / DELETE，
**全部必須 404**（不是 403 —— 403 等於確認「這個 id 存在」）。

自動化的部分已經在測試裡：

```bash
./mvnw.cmd test -Dtest='*ControllerTest,ResumeVersionTest,ResumeUploadTest,PortfolioAndPresetsTest,BlockSelectionTest,InterviewAndOfferTest'
```

涵蓋的資源：積木、投遞、面試、檢討、Offer、履歷版本、履歷檔、候選、專案、題組、JD 配對。

還有一條**結構性**的防護：

```bash
./mvnw.cmd test -Dtest=RepositoryOwnershipConventionTest
```

它用反射列舉所有 repository，要求每一個都被明確分類（使用者直屬 / 子實體 / 全域參照 / 身分本身），
而且方法名要符合該類別的規則。**新增一個 repository 卻沒有分類，測試會直接紅燈**，
強迫你先想清楚「這張表的所有權是怎麼成立的」。

---

## 6. 外網

```bash
curl -sI https://career-toolkit.cjinsightflow.com | head -3
```

**應該看到** `HTTP/2 200`。

登入後檢查 cookie：

```bash
curl -si https://career-toolkit.cjinsightflow.com/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"...","password":"..."}' | grep -i set-cookie
```

**應該看到** `Secure; HttpOnly; SameSite=Lax`。

證明流量真的走本機：

```bash
docker compose down
curl -s -o /dev/null -w '%{http_code}\n' https://career-toolkit.cjinsightflow.com   # 502
docker compose up -d
```

### 實測結果（走外網，不是本機）

```
首頁                          200
未登入 /api/blocks            401
/actuator/health              {"status":"UP"}
深連結 /resumes/abc           200   ← SPA fallback，不是 404
註冊                          201
登入 Set-Cookie               SESSION=...; Path=/; Secure; HttpOnly; SameSite=Lax
建積木                        201
POST 的 Location header       https://career-toolkit.cjinsightflow.com/api/blocks/...
```

最後兩行是這一階段真正要證明的東西，而且**只有走外網才驗得出來**：

- **`Secure` 是自動加上的。** `application.yml` 裡刻意沒有明寫
  `server.servlet.session.cookie.secure`，由容器依 `request.isSecure()` 決定。
  本機用 HTTP 測試時它不會出現 —— 所以本機測試永遠證明不了這一條。
- **`Location` 是正式網址不是 `localhost:8082`。** 沒有
  `forward-headers-strategy: framework` 的話，Spring 眼中的請求是
  `http://localhost:8082`，回給客戶端的 `Location` 會指向一個外面連不到的位址。

> 路由設定曾經踩過兩個坑，記在 [DEPLOY.md](DEPLOY.md) 第 1 節：
> Service URL 少打冒號（`localhost8082`）會被當成主機名解析 → 502；
> 以及不能用 8080 / 8081，那兩個 port 這台機器上已經有人。

---

## 7. CI 與 branch protection

```bash
gh pr checks <PR 編號>
```

每個 PR 都要 `Build & Test` 與 `Secret scan` 兩個綠勾。

**防護要看過它擋下東西才算數** —— 故意把一個測試改壞、push、確認 merge 鍵真的被鎖住，
再改回來。沒看過它擋下東西的防護，不算防護。

---

## 已知的限制

| 項目 | 說明 |
|---|---|
| **中文 PDF 解析** | 測試涵蓋不到。CJK 子集字型缺 `ToUnicode` 表會抽出亂碼，多欄排版會交錯 —— 這些沒辦法用合成檔案重現，需要真實世界的壞檔案。解析是**加速器不是自動駕駛**，所有候選都要人工確認 |
| **LLM** | 只做了介面與關鍵字實作。`BlockSelector` 換實作不需要改任何 Controller |
| **電腦要開著** | tunnel 架構的本質。關機或睡眠 = 網站掛掉 |
| **沒有自動備份** | 見 DEPLOY.md。而且**要真的試著還原一次** —— 沒有還原演練過的備份不算備份 |
| **`devpassword` 在 git 歷史裡** | 本機開發用的，而且兩個 port 都只綁 `127.0.0.1`。正式環境用 `.env` 設真密碼 |
