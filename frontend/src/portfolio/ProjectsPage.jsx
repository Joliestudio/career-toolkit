import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

const EMPTY = {
  name: '', summary: '', repoUrl: '', demoUrl: '', techStack: '', role: '', isPublic: false,
}

export function ProjectsPage() {
  const { user } = useAuth()
  const [projects, setProjects] = useState([])
  const [editing, setEditing] = useState(null)   // null = 關閉，物件 = 編輯中
  const [error, setError] = useState(null)
  const [copied, setCopied] = useState(false)

  const load = useCallback(async () => {
    try {
      setProjects(await api.listProjects())
      setError(null)
    } catch (err) {
      setError(err.message)
    }
  }, [])

  useEffect(() => { load() }, [load])

  async function save(event) {
    event.preventDefault()
    try {
      if (editing.id) await api.updateProject(editing.id, editing)
      else await api.createProject(editing)
      setEditing(null)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '儲存失敗')
    }
  }

  async function togglePublic(project) {
    await api.updateProject(project.id, { isPublic: !project.isPublic })
    await load()
  }

  const publicUrl = `${window.location.origin}${api.publicPortfolioUrl(user.id)}`
  const publicCount = projects.filter((p) => p.isPublic).length

  async function copyPublicUrl() {
    await navigator.clipboard.writeText(publicUrl)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <>
      <div className="toolbar">
        <p className="muted toolbar-hint">
          把 side project 整理起來。標記為公開的專案，任何人不用登入就看得到 ——
          所以預設是私人的，公開必須是你明確的動作。
        </p>
        <button className="btn btn-primary" onClick={() => setEditing({ ...EMPTY })}>
          新增專案
        </button>
      </div>

      {publicCount > 0 && (
        <div className="card asked">
          <h3 className="section-title">
            公開作品集連結（{publicCount} 個專案）
            <button className="btn btn-ghost" onClick={copyPublicUrl}>
              {copied ? '已複製' : '複製連結'}
            </button>
          </h3>
          <code className="public-url">{publicUrl}</code>
        </div>
      )}

      {error && <div className="alert">{error}</div>}

      {editing && (
        <form className="card editor" onSubmit={save}>
          <h2 className="editor-title">{editing.id ? '編輯專案' : '新增專案'}</h2>

          <label className="field">
            <span>專案名稱</span>
            <input value={editing.name} maxLength={200} required
                   onChange={(e) => setEditing({ ...editing, name: e.target.value })} />
          </label>

          <label className="field">
            <span>簡介</span>
            <textarea rows={3} value={editing.summary ?? ''}
                      onChange={(e) => setEditing({ ...editing, summary: e.target.value })} />
          </label>

          <div className="grid-2">
            <label className="field">
              <span>原始碼連結</span>
              <input type="url" value={editing.repoUrl ?? ''}
                     onChange={(e) => setEditing({ ...editing, repoUrl: e.target.value })}
                     placeholder="https://github.com/..." />
            </label>
            <label className="field">
              <span>Demo 連結</span>
              <input type="url" value={editing.demoUrl ?? ''}
                     onChange={(e) => setEditing({ ...editing, demoUrl: e.target.value })} />
            </label>
          </div>

          <div className="grid-2">
            <label className="field">
              <span>技術棧</span>
              <input value={editing.techStack ?? ''}
                     onChange={(e) => setEditing({ ...editing, techStack: e.target.value })}
                     placeholder="Spring Boot, React, PostgreSQL" />
            </label>
            <label className="field">
              <span>你的角色</span>
              <input value={editing.role ?? ''} maxLength={200}
                     onChange={(e) => setEditing({ ...editing, role: e.target.value })}
                     placeholder="全端開發" />
            </label>
          </div>

          <label className="field checkbox-field">
            <input type="checkbox" checked={editing.isPublic ?? false}
                   onChange={(e) => setEditing({ ...editing, isPublic: e.target.checked })} />
            <span>公開這個專案（任何人不用登入都看得到）</span>
          </label>

          <div className="editor-actions">
            <button type="button" className="btn btn-ghost" onClick={() => setEditing(null)}>取消</button>
            <button className="btn btn-primary">儲存</button>
          </div>
        </form>
      )}

      {projects.length === 0 ? (
        <div className="card empty">
          <p>還沒有任何專案。</p>
          <p className="muted">把做過的 side project 整理進來，之後投遞時直接給連結。</p>
        </div>
      ) : (
        <ul className="app-list">
          {projects.map((p) => (
            <li key={p.id} className="card app-row">
              <span className={'badge ' + (p.isPublic ? 'badge-good' : 'badge-neutral')}>
                {p.isPublic ? '公開' : '私人'}
              </span>
              <div className="app-main">
                <strong>{p.name}</strong>
                {p.techStack && <span className="muted"> · {p.techStack}</span>}
                {p.summary && <div className="muted parse-error">{p.summary}</div>}
              </div>
              <button className="btn btn-ghost" onClick={() => togglePublic(p)}>
                {p.isPublic ? '設為私人' : '公開'}
              </button>
              <button className="btn btn-ghost" onClick={() => setEditing(p)}>編輯</button>
              <button className="btn btn-danger"
                      onClick={async () => {
                        if (!confirm(`確定要刪除「${p.name}」嗎？`)) return
                        await api.deleteProject(p.id)
                        await load()
                      }}>
                刪除
              </button>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
