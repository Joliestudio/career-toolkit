import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'
import { ResumeEditor } from './ResumeEditor'

export function ResumesPage() {
  const [resumes, setResumes] = useState([])
  const [openId, setOpenId] = useState(null)
  const [creating, setCreating] = useState(false)
  const [label, setLabel] = useState('')
  const [targetRole, setTargetRole] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setResumes(await api.listResumes())
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function create(event) {
    event.preventDefault()
    try {
      await api.createResume({ label, targetRole: targetRole || null })
      setLabel('')
      setTargetRole('')
      setCreating(false)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '建立失敗')
    }
  }

  async function clone(resume) {
    const newLabel = prompt('新版本的名稱', resume.label + '（複製）')
    if (!newLabel) return
    await api.cloneResume(resume.id, newLabel)
    await load()
  }

  async function remove(resume) {
    if (!confirm(`確定要刪除「${resume.label}」嗎？`)) return
    await api.deleteResume(resume.id)
    if (openId === resume.id) setOpenId(null)
    await load()
  }

  const open = resumes.find((r) => r.id === openId)

  return (
    <>
      <div className="toolbar">
        <p className="muted toolbar-hint">
          針對不同職缺組不同版本，投出去之前鎖定 —— 之後就查得到「當初寄的到底是哪一版」。
        </p>
        <button className="btn btn-primary" onClick={() => setCreating(true)}>
          新增履歷版本
        </button>
      </div>

      {creating && (
        <form className="card editor" onSubmit={create}>
          <h2 className="editor-title">新增履歷版本</h2>
          <div className="grid-2">
            <label className="field">
              <span>版本名稱</span>
              <input value={label} maxLength={120} required
                     onChange={(e) => setLabel(e.target.value)}
                     placeholder="投台積電用" />
            </label>
            <label className="field">
              <span>目標職位<span className="optional">（可略）</span></span>
              <input value={targetRole} maxLength={120}
                     onChange={(e) => setTargetRole(e.target.value)}
                     placeholder="後端工程師" />
            </label>
          </div>
          <div className="editor-actions">
            <button type="button" className="btn btn-ghost" onClick={() => setCreating(false)}>
              取消
            </button>
            <button className="btn btn-primary">建立</button>
          </div>
        </form>
      )}

      {error && <div className="alert">{error}</div>}

      {open && (
        <ResumeEditor
          resume={open}
          onChanged={load}
          onClose={() => setOpenId(null)}
        />
      )}

      {loading ? (
        <p className="muted">載入中…</p>
      ) : resumes.length === 0 ? (
        <div className="card empty">
          <p>還沒有履歷版本。</p>
          <p className="muted">
            從積木庫挑幾段組成一份，針對不同職缺可以各組一版。
          </p>
        </div>
      ) : (
        <ul className="app-list">
          {resumes.map((r) => (
            <li key={r.id}
                className={'card app-row' + (r.id === openId ? ' app-row-open' : '')}>
              <span className={'badge ' + (r.locked ? 'badge-neutral' : 'badge-info')}>
                {r.locked ? '已鎖定' : '編輯中'}
              </span>
              <div className="app-main" onClick={() => setOpenId(r.id === openId ? null : r.id)}>
                <strong>{r.label}</strong>
                {r.targetRole && <span className="muted"> · {r.targetRole}</span>}
                {r.parentId && <span className="tag">複製自其他版本</span>}
              </div>
              <button className="btn btn-ghost" onClick={() => clone(r)}>複製</button>
              <button className="btn btn-danger" onClick={() => remove(r)}>刪除</button>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
