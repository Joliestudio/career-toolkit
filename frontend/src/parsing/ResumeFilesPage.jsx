import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiError } from '../api/client'

const PARSE_STATUS = {
  PENDING: { label: '處理中', tone: 'neutral' },
  PARSED: { label: '已解析', tone: 'good' },
  NO_TEXT_LAYER: { label: '沒有文字層', tone: 'warn' },
  FAILED: { label: '解析失敗', tone: 'bad' },
}

const KIND_LABELS = { SKILL: '技能', CERTIFICATION: '證照' }

export function ResumeFilesPage() {
  const [files, setFiles] = useState([])
  const [openId, setOpenId] = useState(null)
  const [candidates, setCandidates] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const inputRef = useRef(null)

  const load = useCallback(async () => {
    try {
      setFiles(await api.listResumeFiles())
      setError(null)
    } catch (err) {
      setError(err.message)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const loadCandidates = useCallback(async (fileId) => {
    setCandidates(await api.resumeFileCandidates(fileId))
  }, [])

  useEffect(() => {
    if (openId) loadCandidates(openId)
  }, [openId, loadCandidates])

  async function upload(event) {
    const file = event.target.files?.[0]
    if (!file) return

    setBusy(true)
    setError(null)
    try {
      const uploaded = await api.uploadResumeFile(file)
      await load()
      setOpenId(uploaded.id)
    } catch (err) {
      // 型別不符會回 415 並帶出「偵測到的真實類型」，訊息本身就說明了問題
      setError(err instanceof ApiError ? err.message : '上傳失敗')
    } finally {
      setBusy(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  async function decide(candidateId, accept) {
    const updated = accept
      ? await api.acceptCandidate(candidateId)
      : await api.rejectCandidate(candidateId)

    setCandidates((cs) => cs.map((c) => (c.id === candidateId ? updated : c)))
  }

  const openFile = files.find((f) => f.id === openId)
  const pending = candidates.filter((c) => c.status === 'PENDING')

  return (
    <>
      <div className="toolbar">
        <p className="muted toolbar-hint">
          上傳現有的履歷，系統會掃出裡面提到的技能與證照。
          每一項都要你確認過才會變成積木 —— 解析只是加速，不是自動駕駛。
        </p>
        <label className="btn btn-primary upload-btn">
          {busy ? '上傳中…' : '上傳履歷'}
          <input
            ref={inputRef}
            type="file"
            accept=".pdf,.doc,.docx,.txt,.rtf"
            onChange={upload}
            disabled={busy}
            hidden
          />
        </label>
      </div>

      {error && <div className="alert">{error}</div>}

      {files.length === 0 ? (
        <div className="card empty">
          <p>還沒有上傳任何履歷。</p>
          <p className="muted">
            支援 PDF、Word、純文字。掃描的 PDF 沒有文字層，抽不出東西 ——
            系統會直接告訴你，不會假裝解析成功。
          </p>
        </div>
      ) : (
        <ul className="app-list">
          {files.map((f) => {
            const status = PARSE_STATUS[f.parseStatus] ?? { label: f.parseStatus, tone: 'neutral' }
            return (
              <li key={f.id}
                  className={'card app-row' + (f.id === openId ? ' app-row-open' : '')}
                  onClick={() => setOpenId(f.id === openId ? null : f.id)}>
                <span className={'badge badge-' + status.tone}>{status.label}</span>
                <div className="app-main">
                  <strong>{f.originalName}</strong>
                  <span className="muted"> · {(f.sizeBytes / 1024).toFixed(0)} KB</span>
                  {f.parseError && <div className="muted parse-error">{f.parseError}</div>}
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {openFile && openFile.parseStatus === 'PARSED' && (
        <div className="card detail">
          <div className="detail-head">
            <h2 className="detail-title">
              從「{openFile.originalName}」找到的項目
              <span className="badge badge-info">{pending.length} 個待確認</span>
            </h2>
            <button className="btn btn-ghost" onClick={() => setOpenId(null)}>關閉</button>
          </div>

          {candidates.length === 0 ? (
            <p className="muted">沒有比對到字典裡的技能或證照。</p>
          ) : (
            <ul className="candidates">
              {candidates.map((c) => (
                <li key={c.id} className={'candidate candidate-' + c.status.toLowerCase()}>
                  <span className="tag">{KIND_LABELS[c.kind] ?? c.kind}</span>
                  <strong className="candidate-name">{c.normalized}</strong>
                  {c.value !== c.normalized.toLowerCase() &&
                    c.value !== c.normalized && (
                      <span className="muted">（原文寫「{c.value}」）</span>
                    )}
                  <span className="muted candidate-confidence">
                    信心 {(Number(c.confidence) * 100).toFixed(0)}%
                  </span>

                  {c.status === 'PENDING' ? (
                    <>
                      <button className="btn btn-ghost" onClick={() => decide(c.id, false)}>
                        不要
                      </button>
                      <button className="btn btn-primary" onClick={() => decide(c.id, true)}>
                        建成積木
                      </button>
                    </>
                  ) : (
                    <span className={'badge badge-' + (c.status === 'ACCEPTED' ? 'good' : 'neutral')}>
                      {c.status === 'ACCEPTED' ? '已建立' : '已略過'}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          )}

          <p className="muted candidate-note">
            建成積木後內容會是空的 —— 解析只知道「你會 Kubernetes」，
            沒辦法幫你寫出「我用 Kubernetes 做了什麼」。那句話只有你寫得出來，
            而那句話才是履歷上真正有價值的東西。
          </p>
        </div>
      )}
    </>
  )
}
