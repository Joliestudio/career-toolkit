import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'
import { statusLabel, STATUS_TONE, INTERVIEW_STAGES, INTERVIEW_FORMATS } from './applicationStatus'
import { ReviewForm } from '../interviews/ReviewForm'
import { OfferPanel } from '../offers/OfferPanel'

function formatDateTime(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('zh-TW', {
    month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', weekday: 'short',
  })
}

export function ApplicationDetail({ application, onChanged, onClose }) {
  const [history, setHistory] = useState([])
  const [interviews, setInterviews] = useState([])
  const [statusNote, setStatusNote] = useState('')
  const [openReview, setOpenReview] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    try {
      const [h, i] = await Promise.all([
        api.statusHistory(application.id),
        api.listInterviews(application.id),
      ])
      setHistory(h)
      setInterviews(i)
      setError(null)
    } catch (err) {
      setError(err.message)
    }
  }, [application.id])

  useEffect(() => {
    load()
  }, [load])

  async function handleStatusChange(target) {
    setError(null)
    try {
      await api.changeStatus(application.id, target, statusNote || null)
      setStatusNote('')
      await onChanged()
      await load()
    } catch (err) {
      // 後端的 ProblemDetail 會附上 allowed，訊息本身就說得出可以改成什麼
      setError(err instanceof ApiError ? err.message : '狀態變更失敗')
    }
  }

  async function addInterview() {
    const round = interviews.length + 1
    await api.createInterview(application.id, {
      round,
      scheduledAt: new Date(Date.now() + 3 * 86400_000).toISOString(),
      stage: round === 1 ? 'HR_SCREEN' : 'TECH',
      format: 'VIDEO',
    })
    await load()
  }

  async function updateInterview(interview, patch) {
    await api.updateInterview(interview.id, { round: interview.round, ...patch })
    await load()
  }

  return (
    <div className="card detail">
      <div className="detail-head">
        <div>
          <h2 className="detail-title">{application.positionTitle}</h2>
          <p className="muted detail-sub">
            {application.companyName}
            {application.industry && <span className="tag">{application.industry}</span>}
          </p>
        </div>
        <button className="btn btn-ghost" onClick={onClose}>
          關閉
        </button>
      </div>

      {error && <div className="alert">{error}</div>}

      {/* ---------- 狀態 ---------- */}
      <section className="detail-section">
        <h3 className="section-title">
          目前狀態
          <span className={'badge badge-' + STATUS_TONE[application.status]}>
            {statusLabel(application.status)}
          </span>
        </h3>

        {application.allowedNextStates?.length > 0 ? (
          <>
            <input
              className="status-note"
              placeholder="為什麼改成這個狀態？（會存進歷程）"
              value={statusNote}
              onChange={(e) => setStatusNote(e.target.value)}
            />
            <div className="chips">
              {/* 選項直接用後端回的 allowedNextStates —— 不在前端重寫一次狀態機 */}
              {application.allowedNextStates.map((next) => (
                <button key={next} className="chip" onClick={() => handleStatusChange(next)}>
                  → {statusLabel(next)}
                </button>
              ))}
            </div>
          </>
        ) : (
          <p className="muted">這是終態，不能再改成其他狀態。</p>
        )}
      </section>

      {/* ---------- 歷程 ---------- */}
      <section className="detail-section">
        <h3 className="section-title">狀態歷程</h3>
        <ol className="timeline">
          {history.map((h) => (
            <li key={h.id}>
              <span className="timeline-when">{formatDateTime(h.changedAt)}</span>
              <span className="timeline-what">
                {h.fromStatus ? `${statusLabel(h.fromStatus)} → ` : ''}
                {statusLabel(h.toStatus)}
              </span>
              {h.note && <span className="timeline-note">{h.note}</span>}
            </li>
          ))}
        </ol>
      </section>

      {/* ---------- 面試 ---------- */}
      <section className="detail-section">
        <h3 className="section-title">
          面試
          <button className="btn btn-ghost" onClick={addInterview}>
            + 新增一場
          </button>
        </h3>

        {interviews.length === 0 ? (
          <p className="muted">還沒有排定的面試。</p>
        ) : (
          <ul className="interview-list">
            {interviews.map((i) => (
              <li key={i.id} className="interview">
                <div className="interview-row">
                  <span className="round">第 {i.round} 關</span>

                  <select
                    value={i.stage ?? ''}
                    onChange={(e) => updateInterview(i, { stage: e.target.value || null })}
                  >
                    <option value="">未指定</option>
                    {Object.entries(INTERVIEW_STAGES).map(([k, v]) => (
                      <option key={k} value={k}>{v}</option>
                    ))}
                  </select>

                  <select
                    value={i.format ?? ''}
                    onChange={(e) => updateInterview(i, { format: e.target.value || null })}
                  >
                    <option value="">未指定</option>
                    {Object.entries(INTERVIEW_FORMATS).map(([k, v]) => (
                      <option key={k} value={k}>{v}</option>
                    ))}
                  </select>

                  <input
                    type="datetime-local"
                    value={i.scheduledAt ? i.scheduledAt.slice(0, 16) : ''}
                    onChange={(e) =>
                      updateInterview(i, {
                        scheduledAt: e.target.value
                          ? new Date(e.target.value).toISOString()
                          : null,
                      })
                    }
                  />

                  <select
                    value={i.status}
                    onChange={(e) => updateInterview(i, { status: e.target.value })}
                  >
                    <option value="SCHEDULED">已排定</option>
                    <option value="COMPLETED">已完成</option>
                    <option value="CANCELLED">已取消</option>
                    <option value="RESCHEDULED">改期</option>
                  </select>

                  <button
                    className="btn btn-ghost"
                    onClick={() => setOpenReview(openReview === i.id ? null : i.id)}
                  >
                    {openReview === i.id ? '收合檢討' : '面試檢討'}
                  </button>
                </div>

                {openReview === i.id && <ReviewForm interviewId={i.id} />}
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* ---------- Offer ---------- */}
      <OfferPanel
        applicationId={application.id}
        onChanged={async () => {
          await onChanged()
          await load()
        }}
      />
    </div>
  )
}
