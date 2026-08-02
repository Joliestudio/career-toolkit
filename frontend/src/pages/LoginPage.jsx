import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/client'

export function LoginPage() {
  const { login, register } = useAuth()
  const [mode, setMode] = useState('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setBusy(true)

    try {
      if (mode === 'login') {
        await login(email, password)
      } else {
        await register(email, password, displayName || null)
      }
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
        // 後端 ProblemDetail 的 errors 是欄位級的，直接渲染在對應欄位下面
        setFieldErrors(err.fieldErrors)
      } else {
        setError('連不上伺服器')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-shell">
      <form className="card auth-card" onSubmit={handleSubmit}>
        <h1 className="auth-title">Career Toolkit</h1>
        <p className="auth-subtitle">把能力拆成積木，不再重複填同一份表單</p>

        <div className="tabs">
          <button
            type="button"
            className={'tab' + (mode === 'login' ? ' tab-active' : '')}
            onClick={() => setMode('login')}
          >
            登入
          </button>
          <button
            type="button"
            className={'tab' + (mode === 'register' ? ' tab-active' : '')}
            onClick={() => setMode('register')}
          >
            註冊
          </button>
        </div>

        <label className="field">
          <span>Email</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="username"
            required
          />
          {fieldErrors.email && <em className="field-error">{fieldErrors.email}</em>}
        </label>

        <label className="field">
          <span>密碼</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            required
          />
          {fieldErrors.password && <em className="field-error">{fieldErrors.password}</em>}
        </label>

        {mode === 'register' && (
          <label className="field">
            <span>顯示名稱<span className="optional">（可略）</span></span>
            <input
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          </label>
        )}

        {error && <div className="alert">{error}</div>}

        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? '處理中…' : mode === 'login' ? '登入' : '建立帳號'}
        </button>
      </form>
    </div>
  )
}
