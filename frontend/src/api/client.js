/**
 * 後端 API 的唯一入口。
 *
 * 兩件事集中在這裡處理，不要散落到各個元件：
 *   1. CSRF token（從 cookie 讀，放進 header）
 *   2. 錯誤格式（後端統一回 RFC 7807 的 ProblemDetail）
 */

function readCookie(name) {
  const hit = document.cookie
    .split('; ')
    .find((row) => row.startsWith(name + '='))

  // cookie 值是 URL-encoded 的，要解回來才會跟伺服器手上的 token 一致
  return hit ? decodeURIComponent(hit.slice(name.length + 1)) : null
}

/** 後端回的錯誤。把 ProblemDetail 的欄位攤平成好用的形狀。 */
export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || `請求失敗（${status}）`)
    this.status = status
    this.problem = problem
    /** 欄位級的驗證錯誤，例如 { title: "不能為空白" } */
    this.fieldErrors = problem?.errors || {}
    /** 500 時後端給的追查編號，回報問題時用得到 */
    this.errorId = problem?.errorId
  }
}

async function request(path, { method = 'GET', body } = {}) {
  const headers = {}

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  // GET / HEAD 不需要 CSRF token（它們不該改變狀態）
  if (method !== 'GET' && method !== 'HEAD') {
    const token = readCookie('XSRF-TOKEN')
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const response = await fetch('/api' + path, {
    method,
    headers,
    // 同源部署，但明確寫出來比較不會踩到瀏覽器預設值變動
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 204) return null

  const text = await response.text()
  const payload = text ? JSON.parse(text) : null

  if (!response.ok) {
    throw new ApiError(response.status, payload)
  }

  return payload
}

export const api = {
  // ---- 認證 ----
  me: () => request('/auth/me'),
  login: (email, password) => request('/auth/login', { method: 'POST', body: { email, password } }),
  register: (email, password, displayName) =>
    request('/auth/register', { method: 'POST', body: { email, password, displayName } }),
  logout: () => request('/auth/logout', { method: 'POST' }),

  // ---- 積木 ----
  listBlocks: (type) => request('/blocks' + (type ? `?type=${type}` : '')),
  getBlock: (id) => request(`/blocks/${id}`),
  createBlock: (block) => request('/blocks', { method: 'POST', body: block }),
  updateBlock: (id, patch) => request(`/blocks/${id}`, { method: 'PATCH', body: patch }),
  deleteBlock: (id) => request(`/blocks/${id}`, { method: 'DELETE' }),
}
