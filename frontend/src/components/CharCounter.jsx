import { useState } from 'react'

/**
 * 依 code point 計算字數。
 *
 * [...text] 會依 code point 迭代，surrogate pair（emoji）算成一個字。
 * text.length 回傳的是 UTF-16 code unit 數量，emoji 會被算成 2。
 *
 * 這個實作必須跟後端 Block.countChars() 的 codePointCount 完全一致——
 * 不然會出現「前端顯示 299/300、送出去卻被後端擋掉」這種最惱人的錯誤。
 */
export function countChars(text) {
  return text ? [...text].length : 0
}

/** 常見的表單字數上限。台灣的求職表單幾乎都落在這幾個數字。 */
const PRESETS = [100, 300, 500, 1000]

export function CharCounter({ value, limit, onLimitChange }) {
  const count = countChars(value)
  const over = count - limit
  const isOver = over > 0
  const isClose = !isOver && count >= limit * 0.9

  const [copied, setCopied] = useState(false)

  async function copyToClipboard() {
    await navigator.clipboard.writeText(value ?? '')
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div className="counter">
      <div className="counter-limits">
        <span className="counter-label">上限</span>
        {PRESETS.map((preset) => (
          <button
            key={preset}
            type="button"
            className={'chip' + (limit === preset ? ' chip-active' : '')}
            onClick={() => onLimitChange(preset)}
          >
            {preset}
          </button>
        ))}
        <input
          className="counter-custom"
          type="number"
          min="1"
          value={limit}
          onChange={(e) => onLimitChange(Number(e.target.value) || 1)}
          aria-label="自訂字數上限"
        />
      </div>

      <div className="counter-readout">
        <span
          className={
            'counter-count' +
            (isOver ? ' counter-over' : isClose ? ' counter-close' : '')
          }
        >
          {count} / {limit}
        </span>

        {isOver && <span className="counter-warning">超出 {over} 字</span>}

        <button
          type="button"
          className="btn btn-ghost"
          onClick={copyToClipboard}
          disabled={!value}
        >
          {copied ? '已複製' : '複製'}
        </button>
      </div>

      <div className="counter-bar">
        <div
          className={'counter-fill' + (isOver ? ' counter-fill-over' : '')}
          style={{ width: Math.min(100, (count / limit) * 100) + '%' }}
        />
      </div>
    </div>
  )
}
