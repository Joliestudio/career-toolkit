import { useEffect, useState } from 'react'
import { CharCounter } from '../components/CharCounter'
import { BLOCK_TYPES } from './blockTypes'
import { ApiError } from '../api/client'

export function BlockEditor({ block, onSave, onCancel }) {
  const isEditing = Boolean(block)

  const [type, setType] = useState(block?.type ?? 'SKILL')
  const [title, setTitle] = useState(block?.title ?? '')
  const [content, setContent] = useState(block?.content ?? '')
  const [limit, setLimit] = useState(300)
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setType(block?.type ?? 'SKILL')
    setTitle(block?.title ?? '')
    setContent(block?.content ?? '')
    setError(null)
    setFieldErrors({})
  }, [block])

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setBusy(true)

    try {
      // 編輯時送 PATCH，只帶要改的欄位——後端的部分更新語意是
      // 「沒給的欄位不要動」，不是「清空」。
      await onSave(isEditing ? { title, content } : { type, title, content })
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
        setFieldErrors(err.fieldErrors)
      } else {
        setError('儲存失敗')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card editor" onSubmit={handleSubmit}>
      <h2 className="editor-title">{isEditing ? '編輯積木' : '新增積木'}</h2>

      {!isEditing && (
        <label className="field">
          <span>類型</span>
          <select value={type} onChange={(e) => setType(e.target.value)}>
            {BLOCK_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </label>
      )}

      <label className="field">
        <span>標題</span>
        <input
          type="text"
          value={title}
          maxLength={200}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="例如：Spring Boot 後端開發"
          required
        />
        {fieldErrors.title && <em className="field-error">{fieldErrors.title}</em>}
      </label>

      <label className="field">
        <span>內容</span>
        <textarea
          value={content}
          rows={8}
          onChange={(e) => setContent(e.target.value)}
          placeholder="寫一段可以直接貼進求職表單的文字"
          required
        />
        {fieldErrors.content && <em className="field-error">{fieldErrors.content}</em>}
      </label>

      <CharCounter value={content} limit={limit} onLimitChange={setLimit} />

      {error && <div className="alert">{error}</div>}

      <div className="editor-actions">
        <button type="button" className="btn btn-ghost" onClick={onCancel}>
          取消
        </button>
        <button className="btn btn-primary" disabled={busy}>
          {busy ? '儲存中…' : '儲存'}
        </button>
      </div>
    </form>
  )
}
