import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiError } from '../api/client'
import { labelOf } from '../blocks/blockTypes'
import { countChars } from '../components/CharCounter'

/**
 * 履歷組裝。
 *
 * 拖曳排序用原生的 HTML5 draggable，不引入套件——
 * 這個需求只有「同一個清單內重排」，用套件反而是為了一件小事扛一整包相依。
 */
export function ResumeEditor({ resume, onChanged, onClose }) {
  const [entries, setEntries] = useState([])
  const [library, setLibrary] = useState([])
  const [editingOverride, setEditingOverride] = useState(null)
  const [overrideText, setOverrideText] = useState('')
  const [error, setError] = useState(null)
  const dragFrom = useRef(null)

  const locked = resume.locked

  const load = useCallback(async () => {
    try {
      const [e, l] = await Promise.all([api.resumeBlocks(resume.id), api.listBlocks()])
      setEntries(e)
      setLibrary(l)
      setError(null)
    } catch (err) {
      setError(err.message)
    }
  }, [resume.id])

  useEffect(() => {
    load()
  }, [load])

  async function guard(fn) {
    setError(null)
    try {
      await fn()
    } catch (err) {
      // 鎖定後所有修改都會回 409，訊息本身就說明了該怎麼辦（複製一份）
      setError(err instanceof ApiError ? err.message : '操作失敗')
    }
  }

  const used = new Set(entries.map((e) => e.blockId))
  const available = library.filter((b) => !used.has(b.id))

  // ---------- 拖曳 ----------

  function onDrop(targetIndex) {
    if (locked || dragFrom.current === null || dragFrom.current === targetIndex) return

    const next = [...entries]
    const [moved] = next.splice(dragFrom.current, 1)
    next.splice(targetIndex, 0, moved)
    dragFrom.current = null

    setEntries(next) // 先動畫面，不要等後端來回
    guard(async () => {
      setEntries(await api.reorderResumeBlocks(resume.id, next.map((e) => e.blockId)))
      await onChanged()
    })
  }

  return (
    <div className="card detail">
      <div className="detail-head">
        <div>
          <h2 className="detail-title">
            {resume.label}
            {locked && <span className="badge badge-neutral">已鎖定</span>}
          </h2>
          {resume.targetRole && <p className="muted detail-sub">目標職位：{resume.targetRole}</p>}
        </div>
        <button className="btn btn-ghost" onClick={onClose}>關閉</button>
      </div>

      {error && <div className="alert">{error}</div>}

      {locked && (
        <p className="muted lock-note">
          這份履歷已鎖定，內容凍結在鎖定當下 —— 之後修改積木不會影響它。
          要調整的話請複製一份新的。
        </p>
      )}

      {/* ---------- 已選的積木 ---------- */}
      <section className="detail-section">
        <h3 className="section-title">
          履歷內容（{entries.length} 段）
          <a className="btn btn-ghost" href={api.resumeMarkdownUrl(resume.id)} download>
            下載 Markdown
          </a>
          <button className="btn btn-ghost" onClick={() => window.print()}>
            列印／存成 PDF
          </button>
        </h3>

        {entries.length === 0 ? (
          <p className="muted">還沒有加入任何積木。從下面的積木庫挑。</p>
        ) : (
          <ol className="resume-entries">
            {entries.map((entry, index) => (
              <li
                key={entry.blockId}
                className={'resume-entry' + (locked ? '' : ' resume-entry-draggable')}
                draggable={!locked}
                onDragStart={() => (dragFrom.current = index)}
                onDragOver={(e) => e.preventDefault()}
                onDrop={() => onDrop(index)}
              >
                <div className="resume-entry-head">
                  {!locked && <span className="drag-handle" title="拖曳排序">⠿</span>}
                  <span className="tag">{labelOf(entry.type)}</span>
                  <strong>{entry.title}</strong>
                  {entry.overridden && <span className="badge badge-info">已客製化</span>}
                  <span className="muted resume-count">{countChars(entry.content)} 字</span>

                  {!locked && (
                    <>
                      <button
                        className="btn btn-ghost"
                        onClick={() => {
                          setEditingOverride(
                            editingOverride === entry.blockId ? null : entry.blockId
                          )
                          setOverrideText(entry.content)
                        }}
                      >
                        改措辭
                      </button>
                      <button
                        className="btn btn-danger"
                        onClick={() =>
                          guard(async () => {
                            setEntries(await api.removeResumeBlock(resume.id, entry.blockId))
                            await onChanged()
                          })
                        }
                      >
                        移除
                      </button>
                    </>
                  )}
                </div>

                <p className="resume-entry-content">{entry.content}</p>

                {editingOverride === entry.blockId && (
                  <div className="override">
                    <p className="muted">
                      這裡改的只影響這一份履歷，原始積木不動。
                      清空後儲存就會回到原文。
                    </p>
                    <textarea
                      rows={4}
                      value={overrideText}
                      onChange={(e) => setOverrideText(e.target.value)}
                    />
                    <div className="editor-actions">
                      <button className="btn btn-ghost" onClick={() => setEditingOverride(null)}>
                        取消
                      </button>
                      <button
                        className="btn btn-primary"
                        onClick={() =>
                          guard(async () => {
                            setEntries(
                              await api.setResumeBlockOverride(
                                resume.id, entry.blockId, overrideText)
                            )
                            setEditingOverride(null)
                            await onChanged()
                          })
                        }
                      >
                        儲存措辭
                      </button>
                    </div>
                  </div>
                )}
              </li>
            ))}
          </ol>
        )}
      </section>

      {/* ---------- 積木庫 ---------- */}
      {!locked && (
        <section className="detail-section">
          <h3 className="section-title">積木庫（{available.length} 個可加入）</h3>

          {available.length === 0 ? (
            <p className="muted">所有積木都已經加進這份履歷了。</p>
          ) : (
            <ul className="library">
              {available.map((block) => (
                <li key={block.id} className="library-item">
                  <span className="tag">{labelOf(block.type)}</span>
                  <span className="library-title">{block.title}</span>
                  <span className="muted">{countChars(block.content)} 字</span>
                  <button
                    className="btn btn-ghost"
                    onClick={() =>
                      guard(async () => {
                        setEntries(await api.addResumeBlock(resume.id, block.id))
                        await onChanged()
                      })
                    }
                  >
                    + 加入
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {/* ---------- 鎖定 ---------- */}
      {!locked && (
        <section className="detail-section">
          <h3 className="section-title">鎖定這個版本</h3>
          <p className="muted">
            鎖定會把每段內容凍結在此刻。之後修改積木不會再影響這份履歷 ——
            這樣「我當初寄出去的到底是什麼」才有辦法回答。鎖定後不能再改，但可以複製一份繼續調整。
          </p>
          <div className="editor-actions">
            <button
              className="btn btn-primary"
              disabled={entries.length === 0}
              onClick={() =>
                guard(async () => {
                  await api.lockResume(resume.id)
                  await onChanged()
                })
              }
            >
              鎖定並凍結內容
            </button>
          </div>
        </section>
      )}
    </div>
  )
}
