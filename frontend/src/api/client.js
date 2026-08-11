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

/**
 * multipart 上傳。
 *
 * 跟 request() 分開的唯一理由：**不能自己設 Content-Type**。
 * FormData 需要瀏覽器自動加上帶 boundary 的 multipart/form-data，
 * 手動設成 'multipart/form-data' 會少掉 boundary，伺服器直接解析失敗。
 */
async function uploadRequest(path, formData) {
  const headers = {}
  const token = readCookie('XSRF-TOKEN')
  if (token) headers['X-XSRF-TOKEN'] = token

  const response = await fetch('/api' + path, {
    method: 'POST',
    headers,
    credentials: 'same-origin',
    body: formData,
  })

  const text = await response.text()
  const payload = text ? JSON.parse(text) : null

  if (!response.ok) throw new ApiError(response.status, payload)
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

  // ---- 公司與產業 ----
  listIndustries: () => request('/industries'),
  listCompanies: (search) =>
    request('/companies' + (search ? `?search=${encodeURIComponent(search)}` : '')),
  createCompany: (company) => request('/companies', { method: 'POST', body: company }),

  // ---- 投遞 ----
  listApplications: (status) => request('/applications' + (status ? `?status=${status}` : '')),
  getApplication: (id) => request(`/applications/${id}`),
  createApplication: (application) => request('/applications', { method: 'POST', body: application }),
  updateApplication: (id, patch) => request(`/applications/${id}`, { method: 'PATCH', body: patch }),
  deleteApplication: (id) => request(`/applications/${id}`, { method: 'DELETE' }),
  changeStatus: (id, status, note) =>
    request(`/applications/${id}/status`, { method: 'POST', body: { status, note } }),
  statusHistory: (id) => request(`/applications/${id}/history`),

  // ---- 面試 ----
  upcomingInterviews: (days = 14) => request(`/interviews/upcoming?days=${days}`),
  listInterviews: (applicationId) => request(`/applications/${applicationId}/interviews`),
  createInterview: (applicationId, interview) =>
    request(`/applications/${applicationId}/interviews`, { method: 'POST', body: interview }),
  updateInterview: (id, patch) => request(`/interviews/${id}`, { method: 'PATCH', body: patch }),

  // ---- 面試檢討 ----
  getReview: (interviewId) => request(`/interviews/${interviewId}/review`),
  saveReview: (interviewId, review) =>
    request(`/interviews/${interviewId}/review`, { method: 'PUT', body: review }),
  reviewsForApplication: (applicationId) => request(`/applications/${applicationId}/reviews`),

  // ---- 履歷版本 ----
  listResumes: () => request('/resumes'),
  getResume: (id) => request(`/resumes/${id}`),
  createResume: (resume) => request('/resumes', { method: 'POST', body: resume }),
  updateResume: (id, patch) => request(`/resumes/${id}`, { method: 'PATCH', body: patch }),
  deleteResume: (id) => request(`/resumes/${id}`, { method: 'DELETE' }),
  lockResume: (id) => request(`/resumes/${id}/lock`, { method: 'POST' }),
  cloneResume: (id, label) => request(`/resumes/${id}/clone`, { method: 'POST', body: { label } }),

  resumeBlocks: (id) => request(`/resumes/${id}/blocks`),
  addResumeBlock: (id, blockId, section) =>
    request(`/resumes/${id}/blocks`, { method: 'POST', body: { blockId, section } }),
  removeResumeBlock: (id, blockId) =>
    request(`/resumes/${id}/blocks/${blockId}`, { method: 'DELETE' }),
  reorderResumeBlocks: (id, blockIds) =>
    request(`/resumes/${id}/order`, { method: 'PUT', body: { blockIds } }),
  setResumeBlockOverride: (id, blockId, content) =>
    request(`/resumes/${id}/blocks/${blockId}/override`, { method: 'PUT', body: { content } }),

  /** 匯出走瀏覽器下載，不經過 fetch —— 讓瀏覽器自己處理 Content-Disposition */
  resumeMarkdownUrl: (id) => `/api/resumes/${id}/export.md`,

  // ---- 履歷上傳與解析 ----
  listResumeFiles: () => request('/resume-files'),
  resumeFileCandidates: (id) => request(`/resume-files/${id}/candidates`),
  acceptCandidate: (candidateId) =>
    request(`/resume-files/candidates/${candidateId}/accept`, { method: 'POST' }),
  rejectCandidate: (candidateId) =>
    request(`/resume-files/candidates/${candidateId}/reject`, { method: 'POST' }),

  /** 上傳走 multipart，不能用 JSON 那條路徑（不要自己設 Content-Type，讓瀏覽器帶 boundary）。 */
  uploadResumeFile: (file) => {
    const form = new FormData()
    form.append('file', file)
    return uploadRequest('/resume-files', form)
  },

  // ---- 作品集 ----
  listProjects: () => request('/projects'),
  createProject: (project) => request('/projects', { method: 'POST', body: project }),
  updateProject: (id, patch) => request(`/projects/${id}`, { method: 'PATCH', body: patch }),
  deleteProject: (id) => request(`/projects/${id}`, { method: 'DELETE' }),
  /** 公開頁不需要登入 —— 這是整個系統唯一這樣的端點 */
  publicPortfolioUrl: (userId) => `/api/public/portfolio/${userId}`,

  // ---- 題型與題組 ----
  listQuestionTypes: () => request('/question-types'),
  questionsAsked: () => request('/question-types/asked'),
  listPresets: () => request('/presets'),
  createPreset: (preset) => request('/presets', { method: 'POST', body: preset }),
  updatePreset: (id, patch) => request(`/presets/${id}`, { method: 'PATCH', body: patch }),
  deletePreset: (id) => request(`/presets/${id}`, { method: 'DELETE' }),
  addPresetBlock: (id, blockId) =>
    request(`/presets/${id}/blocks`, { method: 'POST', body: { blockId } }),
  removePresetBlock: (id, blockId) =>
    request(`/presets/${id}/blocks/${blockId}`, { method: 'DELETE' }),
  reorderPresetBlocks: (id, blockIds) =>
    request(`/presets/${id}/order`, { method: 'PUT', body: { blockIds } }),
  assembledAnswer: (id) => request(`/presets/${id}/assembled`),

  // ---- 積木選擇（貼 JD → 挑積木）----
  selectBlocksForJd: (jobDescription) =>
    request('/selection', { method: 'POST', body: { jobDescription } }),

  // ---- Offer ----
  listOffers: () => request('/offers'),
  getOffer: (applicationId) => request(`/applications/${applicationId}/offer`),
  saveOffer: (applicationId, offer) =>
    request(`/applications/${applicationId}/offer`, { method: 'PUT', body: offer }),
  decideOffer: (offerId, decision, declineReason) =>
    request(`/offers/${offerId}/decision`, { method: 'POST', body: { decision, declineReason } }),
}
