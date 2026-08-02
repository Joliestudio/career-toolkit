import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { DeadlineBadge, formatMoney } from '../offers/OfferPanel'
import { INTERVIEW_STAGES, INTERVIEW_FORMATS } from '../applications/applicationStatus'

function formatWhen(iso) {
  if (!iso) return '時間未定'
  return new Date(iso).toLocaleString('zh-TW', {
    month: 'numeric', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit',
  })
}

function daysAway(iso) {
  if (!iso) return null
  const diff = new Date(iso) - Date.now()
  return Math.ceil(diff / 86400_000)
}

/**
 * 首頁：兩件有時間壓力的事——未來的面試、還沒回覆的 Offer。
 *
 * 其他都可以慢慢看，只有這兩件錯過就沒了。
 */
export function DashboardPage({ onOpenApplications }) {
  const [interviews, setInterviews] = useState([])
  const [offers, setOffers] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([api.upcomingInterviews(14), api.listOffers()])
      .then(([i, o]) => {
        setInterviews(i)
        setOffers(o)
      })
      .finally(() => setLoading(false))
  }, [])

  const pending = offers.filter((o) => o.decision === 'PENDING')

  if (loading) return <p className="muted">載入中…</p>

  return (
    <>
      <section className="dash-section">
        <h2 className="section-title">未來 14 天的面試</h2>

        {interviews.length === 0 ? (
          <p className="muted">目前沒有排定的面試。</p>
        ) : (
          <ul className="dash-list">
            {interviews.map((i) => {
              const away = daysAway(i.scheduledAt)
              return (
                <li key={i.id} className="card dash-item">
                  <span className={'badge ' + (away <= 1 ? 'badge-bad' : away <= 3 ? 'badge-warn' : 'badge-info')}>
                    {away <= 0 ? '今天' : `${away} 天後`}
                  </span>
                  <div className="dash-main">
                    <strong>第 {i.round} 關</strong>
                    {i.stage && <span className="tag">{INTERVIEW_STAGES[i.stage]}</span>}
                    {i.format && <span className="muted"> · {INTERVIEW_FORMATS[i.format]}</span>}
                    <div className="muted">{formatWhen(i.scheduledAt)}</div>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <section className="dash-section">
        <h2 className="section-title">等待回覆的 Offer</h2>

        {pending.length === 0 ? (
          <p className="muted">目前沒有待決定的 Offer。</p>
        ) : (
          <ul className="dash-list">
            {pending.map((o) => (
              <li key={o.id} className="card dash-item">
                <DeadlineBadge days={o.daysUntilDeadline} />
                <div className="dash-main">
                  <strong>年總額 {formatMoney(o.annualisedTotal)}</strong>
                  <span className="muted">
                    {' '}（{o.salaryPeriod === 'MONTHLY' ? '月薪' : '年薪'} {formatMoney(o.baseSalary)}
                    {o.guaranteedMonths ? ` × ${o.guaranteedMonths} 個月` : ''}）
                  </span>
                  <div className="muted">回覆死線 {o.replyDeadline ?? '未設定'}</div>
                </div>
                <button className="btn btn-ghost" onClick={onOpenApplications}>
                  去決定
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}
