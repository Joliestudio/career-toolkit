/**
 * 狀態的中文標籤與顏色。
 *
 * 這裡刻意只放「顯示用」的資訊，不放任何轉換規則——
 * 哪些狀態可以轉到哪些狀態，一律用後端回應裡的 allowedNextStates。
 * 在前端再實作一次狀態機的話，兩份實作遲早會不一致，
 * 而且不一致的表現會是「使用者選得到但送出去被拒」，非常難查。
 */
export const STATUS_LABELS = {
  DRAFT: '草稿',
  APPLIED: '已投遞',
  SCREENING: '初篩中',
  INTERVIEWING: '面試中',
  OFFER: '收到 Offer',
  ACCEPTED: '已接受',
  DECLINED: '已婉拒',
  REJECTED: '未錄取',
  WITHDRAWN: '主動撤回',
  GHOSTED: '已讀不回',
}

/** 列表上方的頁籤順序。刻意把「還活著的」排在前面。 */
export const STATUS_TABS = [
  'DRAFT',
  'APPLIED',
  'SCREENING',
  'INTERVIEWING',
  'OFFER',
  'ACCEPTED',
  'DECLINED',
  'REJECTED',
  'GHOSTED',
  'WITHDRAWN',
]

export const STATUS_TONE = {
  DRAFT: 'neutral',
  APPLIED: 'info',
  SCREENING: 'info',
  INTERVIEWING: 'active',
  OFFER: 'good',
  ACCEPTED: 'good',
  DECLINED: 'neutral',
  REJECTED: 'bad',
  WITHDRAWN: 'neutral',
  GHOSTED: 'warn',
}

export function statusLabel(status) {
  return STATUS_LABELS[status] ?? status
}

export const SOURCES = ['104', 'LINKEDIN', 'REFERRAL', 'DIRECT', 'CAKERESUME', 'OTHER']

export const INTERVIEW_STAGES = {
  HR_SCREEN: 'HR 初篩',
  TECH: '技術面',
  MANAGER: '主管面',
  FINAL: '終面',
  OTHER: '其他',
}

export const INTERVIEW_FORMATS = {
  ONSITE: '現場',
  VIDEO: '視訊',
  PHONE: '電話',
}
