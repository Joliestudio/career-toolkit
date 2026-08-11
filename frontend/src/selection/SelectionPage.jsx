import { useState } from 'react'
import { api, ApiError } from '../api/client'
import { labelOf } from '../blocks/blockTypes'
import { countChars } from '../components/CharCounter'

/**
 * 貼一份 JD，挑出最相關的積木。
 *
 * 產品立場：選擇器負責「挑選與排序」你的積木，**不負責「寫」積木**。
 * 積木維持你自己的語氣，幻覺就沒有攻擊面，失敗模式是「排序不好」
 * 而不是「捏造經歷」——後者在求職文件上是會被開除的謊言。
 */
export function SelectionPage() {
  const [jd, setJd] = useState('')
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [copied, setCopied] = useState(null)

  async function run(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      setResult(await api.selectBlocksForJd(jd))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '選擇失敗')
      setResult(null)
    } finally {
      setBusy(false)
    }
  }

  async function copy(item) {
    await navigator.clipboard.writeText(item.content)
    setCopied(item.blockId)
    setTimeout(() => setCopied(null), 1500)
  }

  async function copyAll() {
    await navigator.clipboard.writeText(result.items.map((i) => i.content).join('\n\n'))
    setCopied('all')
    setTimeout(() => setCopied(null), 1500)
  }

  return (
    <>
      <form className="card editor" onSubmit={run}>
        <h2 className="editor-title">貼上職缺描述</h2>
        <p className="muted lock-note">
          系統會從你的積木庫挑出最相關的幾塊，並告訴你「為什麼選它」。
          它只做挑選與排序 —— 不會改你的文字，也不會幫你生出你沒做過的經歷。
        </p>

        <label className="field">
          <span>職缺描述（JD）</span>
          <textarea
            rows={10}
            value={jd}
            maxLength={20000}
            onChange={(e) => setJd(e.target.value)}
            placeholder="把 104 或公司官網上的職缺內容整段貼進來"
            required
          />
        </label>

        <div className="editor-actions">
          <span className="muted">{countChars(jd)} 字</span>
          <button className="btn btn-primary" disabled={busy || !jd.trim()}>
            {busy ? '分析中…' : '挑積木'}
          </button>
        </div>
      </form>

      {error && <div className="alert">{error}</div>}

      {result && (
        <div className="card detail">
          <div className="detail-head">
            <div>
              <h2 className="detail-title">
                選出 {result.items.length} 塊
                <span className="badge badge-neutral">{result.selector}</span>
                {result.cached && <span className="badge badge-info">快取</span>}
              </h2>
              <p className="muted detail-sub">
                從 {result.candidateCount} 塊積木中挑選 · {result.latencyMs} ms
              </p>
            </div>
            {result.items.length > 0 && (
              <button className="btn btn-ghost" onClick={copyAll}>
                {copied === 'all' ? '已複製' : '全部複製'}
              </button>
            )}
          </div>

          {result.items.length === 0 ? (
            <p className="muted">
              {result.candidateCount === 0
                ? '你的積木庫還是空的。先去「積木」分頁建幾塊，或從「匯入」上傳履歷。'
                : '沒有積木跟這份 JD 對得上。可以考慮補幾塊相關的經歷。'}
            </p>
          ) : (
            <ul className="resume-entries">
              {result.items.map((item, index) => (
                <li key={item.blockId} className="resume-entry">
                  <div className="resume-entry-head">
                    <span className="round">#{index + 1}</span>
                    <span className="tag">{labelOf(item.type)}</span>
                    <strong>{item.title}</strong>
                    <span className="muted resume-count">{countChars(item.content)} 字</span>
                    <button className="btn btn-ghost" onClick={() => copy(item)}>
                      {copied === item.blockId ? '已複製' : '複製'}
                    </button>
                  </div>

                  {/* 理由是必要的不是裝飾：只給排名的話，使用者沒辦法判斷
                      「這個推薦到底有沒有道理」，只能盲目相信或盲目忽略 */}
                  <div className="reasons">
                    命中：
                    {item.reasons.map((r) => (
                      <span key={r} className="tag">{r}</span>
                    ))}
                  </div>

                  <p className="resume-entry-content">{item.content}</p>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </>
  )
}
