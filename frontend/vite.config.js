import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],

  server: {
    port: 5173,
    proxy: {
      // 開發時前端跑在 5173、後端在 8080。透過 proxy 讓瀏覽器看到的是同一個來源，
      // 這樣 session cookie 與 CSRF cookie 都不用處理跨來源的問題。
      // changeOrigin 保持 false：Host header 要維持原樣，cookie 的 domain 才對得上。
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },

  build: {
    // build 進 Spring Boot 的 static/，正式環境就變成同源部署——完全不需要 CORS。
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
