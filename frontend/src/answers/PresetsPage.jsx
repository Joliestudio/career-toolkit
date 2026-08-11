import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'
import { labelOf } from '../blocks/blockTypes'
import { countChars } from '../components/CharCounter'

/**
 * 題型對應：常見問題各自預設要組合哪幾塊積木。
 *
 * 使用者的原話：「題型對應：常見問題（自我介紹／為什麼選我們／你的優勢）
 * 各自預設要組合哪幾塊積木。」
 */
export function PresetsPage() {
  const [questionTypes, setQuestionTypes] = useState([])
  const [presets, setPresets] = useState([])
  const [asked, setAsked] = useState([])
  const [library, setLibrary] = useState([])
  const [openId, setOpenId] = useState(null)
  const [assembled, setAssembled] = useState(null)
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState({ questionTypeId: '', label: '', targetCharLimit: 300 })
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    try {
      const [types, ps, blocks, qs] = await Promise.all([
        api.listQuestionTypes(), api.listPresets(), api.listBlocks(), api.questionsAsked(),
      ])
      setQuestionTypes(types)
      setPresets(ps)
      setLibrary(blocks)
      setAsked(qs)
      setError(null)
    } catch (err) {
      setError(err.message)
    }
  }, [])

  useEffect(() => { load() }, [load])

  useEffect(() => {
    if (openId) api.assembledAnswer(openId).then(setAssembled).catch((e) => setError(e.message))
    else setAssembled(null)
  }, [openId])

  async function refreshAssembled(id) {
    setAssembled(await api.assembledAnswer(id))
  }

  async function create(event) {
    event.preventDefault()
    try {
      const created = await api.createPreset({
        questionTypeId: Number(form.questionTypeId),
        label: form.label,
        targetCharLimit: form.targetCharLimit ? Number(form.targetCharLimit) : null,
      })
      setCreating(false)
      setForm({ questionTypeId: '', label: '', targetCharLimit: 300 })
      await load()
      setOpenId(created.id)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '建立失敗')
    }
  }

  async function copyAnswer() {
    await navigator.clipboard.writeText(assembled.text)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  const typeName = (id) => questionTypes.find((t) => t.id === id)?.name ?? '未知題型'
  const typeHint = (id) => questionTypes.find((t) => t.id === id)?.hint
  const open = presets.find((p) => p.id === openId)

  const used = new Set(assembled?.parts?.map((p) => p.blockId) ?? [])
  const available = library.filter((b) => !used.has(b.id))

  const over = assembled?.targetCharLimit
    ? assembled.charCount - assembled.targetCharLimit
    : 0

  return (
    <>
      <div className="toolbar">
        <p className="muted toolbar-hint">
          常見問題各自預設要組合哪幾塊積木。組完直接複製貼進對方的表單，
          字數當場對照上限。
        </p>
        <button className="btn btn-primary" onClick={() => setCreating(true)}>新增題組</button>
      </div>

      {error && <div className="alert">{error}</div>}

      {/* 面試檢討裡真正被問過的題目 */}
      {asked.length > 0 && (
        <div className="card asked">
          <h3 className="section-title">你被問過的題目</h3>
          <p className="muted">
            這些是從面試檢討裡撈出來的。真正被問過的題目，才是最該準備的題目。
          </p>
          <ul className="asked-list">
            {asked.slice(0, 12).map((q, i) => <li key={i}>{q}</li>)}
          </ul>
        </div>
      )}

      {creating && (
        <form className="card editor" onSubmit={create}>
          <h2 className="editor-title">新增題組</h2>
          <div className="grid-2">
            <label className="field">
              <span>題型</span>
              <select value={form.questionTypeId} required
                      onChange={(e) => setForm({ ...form, questionTypeId: e.target.value })}>
                <option value="">請選擇</option>
                {questionTypes.map((t) => (
                  <option key={t.id} value={t.id}>{t.name}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>字數上限</span>
              <input type="number" min="1" value={form.targetCharLimit}
                     onChange={(e) => setForm({ ...form, targetCharLimit: e.target.value })} />
            </label>
          </div>
          <label className="field">
            <span>這個版本叫什麼</span>
            <input value={form.label} maxLength={120} required
                   onChange={(e) => setForm({ ...form, label: e.target.value })}
                   placeholder="自我介紹 300 字版" />
          </label>
          {form.questionTypeId && (
            <p className="muted lock-note">{typeHint(Number(form.questionTypeId))}</p>
          )}
          <div className="editor-actions">
            <button type="button" className="btn btn-ghost" onClick={() => setCreating(false)}>取消</button>
            <button className="btn btn-primary">建立</button>
          </div>
        </form>
      )}

      {open && assembled && (
        <div className="card detail">
          <div className="detail-head">
            <div>
              <h2 className="detail-title">{open.label}</h2>
              <p className="muted detail-sub">{typeName(open.questionTypeId)}</p>
            </div>
            <button className="btn btn-ghost" onClick={() => setOpenId(null)}>關閉</button>
          </div>

          {typeHint(open.questionTypeId) && (
            <p className="muted lock-note">{typeHint(open.questionTypeId)}</p>
          )}

          {/* 組好的答案 + 字數 */}
          <section className="detail-section">
            <h3 className="section-title">
              組好的答案
              <span className={'counter-count' + (over > 0 ? ' counter-over' : '')}>
                {assembled.charCount}
                {assembled.targetCharLimit ? ` / ${assembled.targetCharLimit}` : ''}
              </span>
              {over > 0 && <span className="counter-warning">超出 {over} 字</span>}
              <button className="btn btn-ghost" onClick={copyAnswer} disabled={!assembled.text}>
                {copied ? '已複製' : '複製'}
              </button>
            </h3>

            {assembled.parts.length === 0 ? (
              <p className="muted">還沒有加入積木。從下面挑。</p>
            ) : (
              <>
                <pre className="assembled">{assembled.text}</pre>
                <ul className="candidates">
                  {assembled.parts.map((p) => (
                    <li key={p.blockId} className="candidate">
                      <strong className="candidate-name">{p.title}</strong>
                      <span className="muted">{countChars(p.content)} 字</span>
                      <button className="btn btn-ghost"
                              onClick={async () => {
                                await api.removePresetBlock(open.id, p.blockId)
                                await refreshAssembled(open.id)
                              }}>
                        移除
                      </button>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </section>

          <section className="detail-section">
            <h3 className="section-title">積木庫</h3>
            {available.length === 0 ? (
              <p className="muted">所有積木都已加入。</p>
            ) : (
              <ul className="library">
                {available.map((b) => (
                  <li key={b.id} className="library-item">
                    <span className="tag">{labelOf(b.type)}</span>
                    <span className="library-title">{b.title}</span>
                    <span className="muted">{countChars(b.content)} 字</span>
                    <button className="btn btn-ghost"
                            onClick={async () => {
                              await api.addPresetBlock(open.id, b.id)
                              await refreshAssembled(open.id)
                            }}>
                      + 加入
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}

      {presets.length === 0 ? (
        <div className="card empty">
          <p>還沒有任何題組。</p>
          <p className="muted">
            先做一個「自我介紹 300 字版」，之後每次要填表就直接組出來複製。
          </p>
        </div>
      ) : (
        <ul className="app-list">
          {presets.map((p) => (
            <li key={p.id}
                className={'card app-row' + (p.id === openId ? ' app-row-open' : '')}>
              <span className="badge badge-info">{typeName(p.questionTypeId)}</span>
              <div className="app-main" onClick={() => setOpenId(p.id === openId ? null : p.id)}>
                <strong>{p.label}</strong>
                {p.targetCharLimit && <span className="muted"> · 上限 {p.targetCharLimit} 字</span>}
              </div>
              <button className="btn btn-danger"
                      onClick={async () => {
                        if (!confirm(`確定要刪除「${p.label}」嗎？`)) return
                        await api.deletePreset(p.id)
                        if (openId === p.id) setOpenId(null)
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
