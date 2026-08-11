import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { BLOCK_TYPES, labelOf } from './blockTypes'
import { BlockEditor } from './BlockEditor'
import { countChars } from '../components/CharCounter'

export function BlocksPage() {
  const { user, logout } = useAuth()
  const [blocks, setBlocks] = useState([])
  const [filter, setFilter] = useState('')
  const [editing, setEditing] = useState(undefined) // undefined = 關閉，null = 新增，物件 = 編輯
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setBlocks(await api.listBlocks(filter || undefined))
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [filter])

  useEffect(() => {
    load()
  }, [load])

  async function handleSave(payload) {
    if (editing) {
      await api.updateBlock(editing.id, payload)
    } else {
      await api.createBlock(payload)
    }
    setEditing(undefined)
    await load()
  }

  async function handleDelete(block) {
    if (!confirm(`確定要刪除「${block.title}」嗎？`)) return
    await api.deleteBlock(block.id)
    await load()
  }

  async function copyContent(block) {
    await navigator.clipboard.writeText(block.content)
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">Career Toolkit</div>
        <div className="topbar-right">
          <span className="whoami">{user.email}</span>
          {user.role === 'ADMIN' && <span className="badge">管理員</span>}
          <button className="btn btn-ghost" onClick={logout}>
            登出
          </button>
        </div>
      </header>

      <main className="main">
        <div className="toolbar">
          <div className="filters">
            <button
              className={'chip' + (filter === '' ? ' chip-active' : '')}
              onClick={() => setFilter('')}
            >
              全部
            </button>
            {BLOCK_TYPES.map((t) => (
              <button
                key={t.value}
                className={'chip' + (filter === t.value ? ' chip-active' : '')}
                onClick={() => setFilter(t.value)}
              >
                {t.label}
              </button>
            ))}
          </div>

          <button className="btn btn-primary" onClick={() => setEditing(null)}>
            新增積木
          </button>
        </div>

        {editing !== undefined && (
          <BlockEditor
            block={editing}
            onSave={handleSave}
            onCancel={() => setEditing(undefined)}
          />
        )}

        {error && <div className="alert">{error}</div>}

        {loading ? (
          <p className="muted">載入中…</p>
        ) : blocks.length === 0 ? (
          <div className="card empty">
            <p>還沒有任何積木。</p>
            <p className="muted">
              先把一段你常常要重打的自我介紹或專案描述存進來，下次就不用再找了。
            </p>
          </div>
        ) : (
          <ul className="block-list">
            {blocks.map((block) => (
              <li key={block.id} className="card block">
                <div className="block-head">
                  <span className="tag">{labelOf(block.type)}</span>
                  <h3 className="block-title">{block.title}</h3>
                  <span className="block-count">{countChars(block.content)} 字</span>
                </div>

                <p className="block-content">{block.content}</p>

                <div className="block-actions">
                  <button className="btn btn-ghost" onClick={() => copyContent(block)}>
                    複製
                  </button>
                  <button className="btn btn-ghost" onClick={() => setEditing(block)}>
                    編輯
                  </button>
                  <button className="btn btn-danger" onClick={() => handleDelete(block)}>
                    刪除
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  )
}
