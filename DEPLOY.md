# 上線到 career-toolkit.cjinsightflow.com

## 架構

```
瀏覽器  →  Cloudflare 邊緣（TLS 終止、Universal SSL）
             ↓  加密通道（由本機主動打出去）
       cloudflared（Windows 服務，讀 C:\ProgramData\cloudflared\token）
             ↓  http://localhost:8080
       ct-app 容器  →  ct-postgres 容器
```

**不需要固定 IP、不用開路由器防火牆** —— 連線是由內往外打的。代價是電腦要開著。

## 這台機器上已經有的東西

`cloudflared` 已經以 **Windows 服務**執行，用的是 `JOLIE_PROJECT` 既有的 `jolie-tunnel`。

**所以不要在 `compose.yaml` 裡再加一個 `cloudflared` 服務** —— 那會變成第二條通道，跟既有的 Windows 服務打架。同一個 tunnel 可以承載多個 public hostname，這就是要用的方式。

## 步驟

### 1. 在 Cloudflare Zero Trust 加一條 public hostname 路由

Zero Trust → Networks → Tunnels → `jolie-tunnel` → Public Hostname → Add：

| 欄位 | 值 |
|---|---|
| Subdomain | `career-toolkit` |
| Domain | `cjinsightflow.com` |
| Service Type | `HTTP` |
| URL | `localhost:8080` |

> token 管理的 tunnel 沒有本機路由設定檔，這一步只能在儀表板做。

DNS 記錄 Cloudflare 會自動建立（CNAME 指向 tunnel），不用手動加。

### 2. 建立 `.env`

```bash
cp .env.example .env
```

然後把 `DB_PASSWORD` 換成一個真正的密碼。

**這一步不能跳過** —— `application.yml` 裡的 `DB_PASSWORD` 刻意沒有預設值，缺變數時應用會**啟動失敗**而不是用弱密碼跑起來。

> 已經有資料的話，改密碼要同時改 Postgres 那邊，否則應用連不上。
> 全新環境直接設就好。

### 3. 起服務

```bash
docker compose up --build -d
```

### 4. 驗證

```bash
# 本機
curl -s localhost:8080/actuator/health          # {"status":"UP"}

# 外網
curl -sI https://career-toolkit.cjinsightflow.com | head -3
```

登入之後檢查 `Set-Cookie` 有沒有帶 `Secure`：

```bash
curl -si https://career-toolkit.cjinsightflow.com/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"你的信箱","password":"你的密碼"}' | grep -i set-cookie
```

應該看到 `SESSION=...; Path=/; Secure; HttpOnly; SameSite=Lax`。

`Secure` 是自動加上的：`application.yml` 裡刻意**沒有**明寫 `server.servlet.session.cookie.secure`，容器會依 `request.isSecure()` 決定，而 `forward-headers-strategy: framework` 讓它認得 Cloudflare 的 `X-Forwarded-Proto: https`。本機用 HTTP 測試時就自動不帶 —— 兩邊都對，不需要額外的 profile。

### 5. 確認流量真的走本機

```bash
docker compose down
curl -s -o /dev/null -w '%{http_code}\n' https://career-toolkit.cjinsightflow.com   # 502
docker compose up -d
```

502 代表「通道在，但本機沒有 origin」。這是唯一能證明「這個網站真的跑在你的電腦上」的測試。

## 選配：用 Cloudflare Access 保護管理路徑

`/actuator/health` 是刻意公開的（tunnel 要打它，回應只有 `{"status":"UP"}`）。其餘 actuator endpoint 沒有暴露，`/api/admin/**` 需要 `ROLE_ADMIN`。

想再加一層的話，在 Zero Trust → Access → Applications 為 `career-toolkit.cjinsightflow.com/api/admin/*` 建一個 policy，只放行你的 email。這樣未授權的人連 Spring 都碰不到。

## 常見狀況

| 症狀 | 原因 |
|---|---|
| 網域回 **502** | 本機沒有 origin。`docker compose ps` 看容器是不是在跑 |
| 網域回 **1033 / tunnel error** | `cloudflared` 服務沒跑。`services.msc` 裡找 Cloudflare Tunnel |
| 應用**啟動失敗**說找不到 `DB_PASSWORD` | 沒建 `.env`。這是預期行為，不是 bug |
| 登入成功但下一個請求 401 | cookie 沒被存。檢查是不是用 `http://` 連正式網域（Secure cookie 不會經 HTTP 送出） |
| 上傳檔案 **Permission denied** | volume 權限。`docker compose down -v` 砍掉 volume 重建（**會刪掉已上傳的檔案**） |

## 要注意的事

- **電腦關機或睡眠 = 網站掛掉。** 這是 tunnel 架構的本質，不是設定問題。
- **資料庫沒有自動備份。** 目前只在 Docker volume 裡。定期跑：
  ```bash
  docker exec ct-postgres pg_dump -U ct_user career_toolkit > backup.sql
  ```
  而且**要真的試著還原一次** —— 沒有還原演練過的備份不算備份。
- 上傳的履歷檔在 `ct-files` volume 裡，`docker compose down -v` 會一起刪掉。
