/** 對應後端的 BlockType enum。改動時兩邊要一起改。 */
export const BLOCK_TYPES = [
  { value: 'EXPERIENCE', label: '工作經歷' },
  { value: 'SKILL', label: '技能' },
  { value: 'PROJECT', label: '專案' },
  { value: 'EDUCATION', label: '學歷' },
  { value: 'CERTIFICATION', label: '證照' },
  { value: 'ANSWER_SNIPPET', label: '答題素材' },
]

export function labelOf(type) {
  return BLOCK_TYPES.find((t) => t.value === type)?.label ?? type
}
