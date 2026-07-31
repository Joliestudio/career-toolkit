# Career Toolkit

## 專案目標
這個專案旨在打造一個屬於我自己的職涯與技術工具箱。透過實作各種後端與基礎設施技術，來解決實際開發中會遇到的問題。這不僅是技術力的展示，也是未來工作上可以隨時重用的參考架構。

## 工作流程與分支規則
- **Main 分支**：`main` 永遠保持可運行（Runnable）的狀態，且絕對乾淨。
- **功能分支**：每個 Lesson 或新功能都會開一個獨立的分支進行開發（例如：`p0/l4-postgres-docker`）。
- **PR 與 Code Review**：開發完成後，必須發布 Pull Request (PR)。在 Merge 回 `main` 之前，開發者必須先 review 自己的 diff，確保沒有殘留的除錯程式碼（如 print），並且絕對不可將密碼、API key 等機密資訊推上版控。

## 技術堆疊 (Tech Stack)
- **後端框架**：Java 21, Spring Boot 3.x
- **資料庫**：PostgreSQL 17
- **資料庫版控**：Flyway
- **本地開發環境**：Docker Compose

## 🚀 本機開發環境設定 (Getting Started)

要將這個專案在本地端跑起來，請確認你的電腦已安裝 [Docker](https://www.docker.com/) 與 Java 21 開發環境。

### 1. 啟動資料庫
專案使用 Docker Compose 管理本地資料庫。請在專案根目錄執行：
```bash
docker compose up -d db
```

### 2. 啟動應用程式
專案內附 Maven Wrapper，不需要另外安裝 Maven。在根目錄執行：
```bash
# Mac / Linux
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```
啟動時，Flyway 會自動連接資料庫，並將資料表結構（Schema）更新至最新版本。

### 預設測試帳號
Flyway 啟動時會自動寫入一筆測試用的開發者帳號，方便本地測試：

Email: dev@local
ID (UUID): 00000000-0000-0000-0000-000000000001

## 架構與設計決策 (Architecture & Design Decisions)

- **Package 結構**：採用依功能劃分（Feature-based / Domain-driven），而非技術分層（MVC）。將同一領域（如 Block）的 Entity、Repository、Service 放在同一個 package 中，以提高內聚力並便於權限控管。
- **資料庫同步策略**：`spring.jpa.hibernate.ddl-auto` 嚴格設定為 `validate`。資料庫結構完全交由 Flyway 控管，Hibernate 只負責在啟動時檢查 Java Entity 是否與資料庫真實結構 100% 吻合，防堵結構不一致引發的線上災難。
- **Entity 封裝設計**：
  - 拒絕無腦使用 Lombok `@Data`。
  - ID 採用手動產生的 UUID，確保物件建立當下即可使用。
  - Setter 僅開放給業務邏輯上真正允許變動的欄位，並透過領域方法（Domain Method，如 `updateContent`）連動更新相關屬性（如字數統計），維持物件內部狀態的一致性。