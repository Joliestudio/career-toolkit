import { useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'
import { SOURCES } from './applicationStatus'

/**
 * 新增投遞。
 *
 * 公司欄位是「輸入名稱，不存在就建一筆」——後端的 findOrCreate 同名會回傳既有那筆。
 * 這樣使用者不會在「我要記一筆投遞」的當下被「公司已存在」的錯誤卡住。
 */
export function ApplicationForm({ onCreated, onCancel }) {
  const [companies, setCompanies] = useState([])
  const [industries, setIndustries] = useState([])

  const [companyName, setCompanyName] = useState('')
  const [industryId, setIndustryId] = useState('')
  const [positionTitle, setPositionTitle] = useState('')
  const [jobUrl, setJobUrl] = useState('')
  const [source, setSource] = useState('')
  const [status, setStatus] = useState('APPLIED')
  const [notes, setNotes] = useState('')

  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    Promise.all([api.listCompanies(), api.listIndustries()])
      .then(([c, i]) => {
        setCompanies(c)
        setIndustries(i)
      })
      .catch((err) => setError(err.message))
  }, [])

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setBusy(true)

    try {
      const company = await api.createCompany({
        name: companyName.trim(),
        industryId: industryId ? Number(industryId) : null,
      })

      await api.createApplication({
        companyId: company.id,
        positionTitle,
        jobUrl: jobUrl || null,
        source: source || null,
        status,
        notes: notes || null,
      })

      onCreated()
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
        setFieldErrors(err.fieldErrors)
      } else {
        setError('儲存失敗')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card editor" onSubmit={handleSubmit}>
      <h2 className="editor-title">新增投遞</h2>

      <div className="grid-2">
        <label className="field">
          <span>公司</span>
          <input
            list="company-options"
            value={companyName}
            onChange={(e) => setCompanyName(e.target.value)}
            placeholder="台積電"
            required
          />
          {/* 已建過的公司直接選，沒有的就打新的——後端同名會回傳既有那筆 */}
          <datalist id="company-options">
            {companies.map((c) => (
              <option key={c.id} value={c.name} />
            ))}
          </datalist>
        </label>

        <label className="field">
          <span>產業</span>
          <select value={industryId} onChange={(e) => setIndustryId(e.target.value)}>
            <option value="">未分類</option>
            {industries.map((i) => (
              <option key={i.id} value={i.id}>
                {i.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      <label className="field">
        <span>職稱</span>
        <input
          value={positionTitle}
          maxLength={200}
          onChange={(e) => setPositionTitle(e.target.value)}
          placeholder="後端工程師"
          required
        />
        {fieldErrors.positionTitle && (
          <em className="field-error">{fieldErrors.positionTitle}</em>
        )}
      </label>

      <div className="grid-2">
        <label className="field">
          <span>來源</span>
          <select value={source} onChange={(e) => setSource(e.target.value)}>
            <option value="">未指定</option>
            {SOURCES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>

        <label className="field">
          <span>狀態</span>
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="APPLIED">已投遞</option>
            {/* 想先記下還沒投的職缺就選草稿 */}
            <option value="DRAFT">草稿（還沒投）</option>
          </select>
        </label>
      </div>

      <label className="field">
        <span>職缺連結<span className="optional">（可略）</span></span>
        <input
          type="url"
          value={jobUrl}
          maxLength={500}
          onChange={(e) => setJobUrl(e.target.value)}
          placeholder="https://…"
        />
      </label>

      <label className="field">
        <span>備註<span className="optional">（可略）</span></span>
        <textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
      </label>

      {error && <div className="alert">{error}</div>}

      <div className="editor-actions">
        <button type="button" className="btn btn-ghost" onClick={onCancel}>
          取消
        </button>
        <button className="btn btn-primary" disabled={busy}>
          {busy ? '儲存中…' : '建立'}
        </button>
      </div>
    </form>
  )
}
