import { useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'

const EMPTY = {
  processNotes: '',
  jobReality: '',
  gaps: '',
  questionsAsked: '',
  redFlags: '',
  rating: '',
  interestLevel: '',
}

/**
 * 面試檢討。
 *
 * 欄位刻意分開而不是一個大 textarea：需求講的是三件不同的事
 * （今天的流程 / 這份工作實際在做什麼 / 我要調整的部分），
 * 全部塞在一起之後，收到 offer 要做決策時就沒辦法只看某一面。
 */
export function ReviewForm({ interviewId }) {
  const [form, setForm] = useState(EMPTY)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api
      .getReview(interviewId)
      .then((r) =>
        setForm({
          processNotes: r.processNotes ?? '',
          jobReality: r.jobReality ?? '',
          gaps: r.gaps ?? '',
          questionsAsked: r.questionsAsked ?? '',
          redFlags: r.redFlags ?? '',
          rating: r.rating ?? '',
          interestLevel: r.interestLevel ?? '',
        })
      )
      // 還沒寫過檢討就是 404，那是正常狀態不是錯誤
      .catch((err) => {
        if (!(err instanceof ApiError && err.status === 404)) setError(err.message)
      })
  }, [interviewId])

  function set(field, value) {
    setForm((f) => ({ ...f, [field]: value }))
    setSaved(false)
  }

  async function save() {
    setBusy(true)
    setError(null)
    try {
      await api.saveReview(interviewId, {
        ...form,
        rating: form.rating === '' ? null : Number(form.rating),
        interestLevel: form.interestLevel === '' ? null : Number(form.interestLevel),
      })
      setSaved(true)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="review">
      <label className="field">
        <span>今天的面試流程</span>
        <textarea rows={3} value={form.processNotes}
                  onChange={(e) => set('processNotes', e.target.value)} />
      </label>

      <label className="field">
        <span>這份工作實際在做什麼</span>
        <textarea rows={3} value={form.jobReality}
                  onChange={(e) => set('jobReality', e.target.value)} />
      </label>

      <label className="field">
        <span>我需要調整的部分</span>
        <textarea rows={3} value={form.gaps}
                  onChange={(e) => set('gaps', e.target.value)} />
      </label>

      <label className="field">
        <span>
          被問到的題目
          <span className="optional">（之後可以變成題型對應的素材）</span>
        </span>
        <textarea rows={3} value={form.questionsAsked}
                  onChange={(e) => set('questionsAsked', e.target.value)} />
      </label>

      <label className="field">
        <span>警訊<span className="optional">（可略）</span></span>
        <textarea rows={2} value={form.redFlags}
                  onChange={(e) => set('redFlags', e.target.value)} />
      </label>

      {/*
        兩個分數一定要分開。
        面得很順但公司文化有疑慮（高 rating、低 interest）是很常見的組合，
        而那正是收到 offer 時最需要看到的資訊。合成一個分數就用不上了。
      */}
      <div className="grid-2">
        <label className="field">
          <span>面得如何 <strong>{form.rating || '—'}</strong> / 5</span>
          <input type="range" min="1" max="5" value={form.rating || 3}
                 onChange={(e) => set('rating', e.target.value)} />
        </label>

        <label className="field">
          <span>我多想去 <strong>{form.interestLevel || '—'}</strong> / 5</span>
          <input type="range" min="1" max="5" value={form.interestLevel || 3}
                 onChange={(e) => set('interestLevel', e.target.value)} />
        </label>
      </div>

      {error && <div className="alert">{error}</div>}

      <div className="editor-actions">
        {saved && <span className="muted">已儲存</span>}
        <button className="btn btn-primary" onClick={save} disabled={busy}>
          {busy ? '儲存中…' : '儲存檢討'}
        </button>
      </div>
    </div>
  )
}
