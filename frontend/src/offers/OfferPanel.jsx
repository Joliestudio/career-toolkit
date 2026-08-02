import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'

export function formatMoney(value) {
  if (value == null) return '—'
  return new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 0 }).format(value)
}

/** 死線倒數。負數代表已經過期。 */
export function DeadlineBadge({ days }) {
  if (days == null) return null

  if (days < 0) return <span className="badge badge-bad">已過期 {-days} 天</span>
  if (days === 0) return <span className="badge badge-bad">今天到期</span>
  if (days <= 3) return <span className="badge badge-bad">剩 {days} 天</span>
  if (days <= 7) return <span className="badge badge-warn">剩 {days} 天</span>
  return <span className="badge badge-info">剩 {days} 天</span>
}

const EMPTY = {
  baseSalary: '',
  salaryPeriod: 'MONTHLY',
  guaranteedMonths: '',
  bonusNote: '',
  offeredAt: '',
  replyDeadline: '',
}

export function OfferPanel({ applicationId, onChanged }) {
  const [offer, setOffer] = useState(null)
  const [form, setForm] = useState(EMPTY)
  const [editing, setEditing] = useState(false)
  const [declineReason, setDeclineReason] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      const o = await api.getOffer(applicationId)
      setOffer(o)
      setForm({
        baseSalary: o.baseSalary ?? '',
        salaryPeriod: o.salaryPeriod ?? 'MONTHLY',
        guaranteedMonths: o.guaranteedMonths ?? '',
        bonusNote: o.bonusNote ?? '',
        offeredAt: o.offeredAt ?? '',
        replyDeadline: o.replyDeadline ?? '',
      })
    } catch (err) {
      // 還沒有 offer 就是 404，那是正常狀態
      if (!(err instanceof ApiError && err.status === 404)) setError(err.message)
      setOffer(null)
    }
  }, [applicationId])

  useEffect(() => {
    load()
  }, [load])

  async function save() {
    setBusy(true)
    setError(null)
    try {
      await api.saveOffer(applicationId, {
        baseSalary: form.baseSalary === '' ? null : Number(form.baseSalary),
        salaryPeriod: form.salaryPeriod,
        guaranteedMonths: form.guaranteedMonths === '' ? null : Number(form.guaranteedMonths),
        bonusNote: form.bonusNote || null,
        offeredAt: form.offeredAt || null,
        replyDeadline: form.replyDeadline || null,
      })
      setEditing(false)
      await load()
      await onChanged()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function decide(decision) {
    setBusy(true)
    setError(null)
    try {
      await api.decideOffer(offer.id, decision, decision === 'DECLINED' ? declineReason : null)
      await load()
      await onChanged()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="detail-section">
      <h3 className="section-title">
        Offer
        {offer && offer.decision === 'PENDING' && <DeadlineBadge days={offer.daysUntilDeadline} />}
        {!editing && (
          <button className="btn btn-ghost" onClick={() => setEditing(true)}>
            {offer ? '編輯' : '+ 記錄 Offer'}
          </button>
        )}
      </h3>

      {error && <div className="alert">{error}</div>}

      {editing ? (
        <>
          <div className="grid-2">
            <label className="field">
              <span>薪資</span>
              <input type="number" value={form.baseSalary}
                     onChange={(e) => setForm({ ...form, baseSalary: e.target.value })} />
            </label>
            <label className="field">
              <span>計算方式</span>
              <select value={form.salaryPeriod}
                      onChange={(e) => setForm({ ...form, salaryPeriod: e.target.value })}>
                <option value="MONTHLY">月薪</option>
                <option value="ANNUAL">年薪</option>
              </select>
            </label>
          </div>

          <label className="field">
            <span>
              保證年薪幾個月
              <span className="optional">
                （沒有這欄就沒辦法比較：月薪 50k×14 = 70 萬 &gt; 月薪 55k×12 = 66 萬）
              </span>
            </span>
            <input type="number" step="0.5" value={form.guaranteedMonths}
                   onChange={(e) => setForm({ ...form, guaranteedMonths: e.target.value })} />
          </label>

          <div className="grid-2">
            <label className="field">
              <span>收到日期</span>
              <input type="date" value={form.offeredAt}
                     onChange={(e) => setForm({ ...form, offeredAt: e.target.value })} />
            </label>
            <label className="field">
              <span>回覆死線</span>
              <input type="date" value={form.replyDeadline}
                     onChange={(e) => setForm({ ...form, replyDeadline: e.target.value })} />
            </label>
          </div>

          <label className="field">
            <span>獎金／其他<span className="optional">（可略）</span></span>
            <textarea rows={2} value={form.bonusNote}
                      onChange={(e) => setForm({ ...form, bonusNote: e.target.value })} />
          </label>

          <div className="editor-actions">
            <button className="btn btn-ghost" onClick={() => setEditing(false)}>取消</button>
            <button className="btn btn-primary" onClick={save} disabled={busy}>儲存</button>
          </div>
        </>
      ) : offer ? (
        <div className="offer-summary">
          <dl className="offer-facts">
            <div>
              <dt>{offer.salaryPeriod === 'MONTHLY' ? '月薪' : '年薪'}</dt>
              <dd>{formatMoney(offer.baseSalary)}</dd>
            </div>
            {offer.guaranteedMonths && (
              <div>
                <dt>保證</dt>
                <dd>{offer.guaranteedMonths} 個月</dd>
              </div>
            )}
            <div>
              <dt>年總額</dt>
              <dd className="strong">{formatMoney(offer.annualisedTotal)}</dd>
            </div>
            <div>
              <dt>回覆死線</dt>
              <dd>{offer.replyDeadline ?? '—'}</dd>
            </div>
          </dl>

          {offer.bonusNote && <p className="muted">{offer.bonusNote}</p>}

          {offer.decision === 'PENDING' ? (
            <>
              <input
                className="status-note"
                placeholder="婉拒的話，理由是什麼？（三個月後回頭看只剩模糊印象）"
                value={declineReason}
                onChange={(e) => setDeclineReason(e.target.value)}
              />
              <div className="editor-actions">
                <button className="btn btn-ghost" onClick={() => decide('DECLINED')} disabled={busy}>
                  婉拒
                </button>
                <button className="btn btn-primary" onClick={() => decide('ACCEPTED')} disabled={busy}>
                  接受
                </button>
              </div>
            </>
          ) : (
            <p className="muted">
              已{offer.decision === 'ACCEPTED' ? '接受' : '婉拒'}
              {offer.decidedAt && `（${offer.decidedAt}）`}
              {offer.declineReason && ` — ${offer.declineReason}`}
            </p>
          )}
        </div>
      ) : (
        <p className="muted">還沒有收到 Offer。</p>
      )}
    </section>
  )
}
