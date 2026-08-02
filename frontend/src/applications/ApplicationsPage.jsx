import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import { statusLabel, STATUS_TABS, STATUS_TONE } from './applicationStatus'
import { ApplicationForm } from './ApplicationForm'
import { ApplicationDetail } from './ApplicationDetail'

export function ApplicationsPage() {
  const [applications, setApplications] = useState([])
  const [filter, setFilter] = useState('')
  const [creating, setCreating] = useState(false)
  const [openId, setOpenId] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setApplications(await api.listApplications(filter || undefined))
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

  const open = applications.find((a) => a.id === openId)

  return (
    <>
      <div className="toolbar">
        <div className="filters">
          <button className={'chip' + (filter === '' ? ' chip-active' : '')}
                  onClick={() => setFilter('')}>
            全部
          </button>
          {STATUS_TABS.map((s) => (
            <button key={s} className={'chip' + (filter === s ? ' chip-active' : '')}
                    onClick={() => setFilter(s)}>
              {statusLabel(s)}
            </button>
          ))}
        </div>

        <button className="btn btn-primary" onClick={() => setCreating(true)}>
          新增投遞
        </button>
      </div>

      {creating && (
        <ApplicationForm
          onCreated={async () => {
            setCreating(false)
            await load()
          }}
          onCancel={() => setCreating(false)}
        />
      )}

      {error && <div className="alert">{error}</div>}

      {open && (
        <ApplicationDetail
          application={open}
          onChanged={load}
          onClose={() => setOpenId(null)}
        />
      )}

      {loading ? (
        <p className="muted">載入中…</p>
      ) : applications.length === 0 ? (
        <div className="card empty">
          <p>還沒有投遞紀錄。</p>
          <p className="muted">
            記下第一筆之後，你就能回答「我投了幾家、卡在哪一關、哪些還在等回覆」。
          </p>
        </div>
      ) : (
        <ul className="app-list">
          {applications.map((a) => (
            <li key={a.id}
                className={'card app-row' + (a.id === openId ? ' app-row-open' : '')}
                onClick={() => setOpenId(a.id === openId ? null : a.id)}>
              <span className={'badge badge-' + STATUS_TONE[a.status]}>
                {statusLabel(a.status)}
              </span>
              <div className="app-main">
                <strong>{a.positionTitle}</strong>
                <span className="muted"> · {a.companyName}</span>
                {a.industry && <span className="tag">{a.industry}</span>}
              </div>
              <span className="muted app-date">{a.appliedAt ?? '未投遞'}</span>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
