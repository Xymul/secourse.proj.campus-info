/** 时间与字典格式化工具 */

export const formatTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export const formatDate = (value) => (value ? formatTime(value).slice(0, 10) : '-')

export const relativeTime = (value) => {
  if (!value) return '-'
  const diff = Date.now() - new Date(value).getTime()
  if (Number.isNaN(diff)) return value
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return formatDate(value)
}

export const distanceText = (km) => {
  if (km === null || km === undefined) return ''
  return km < 1 ? `${Math.round(km * 1000)} 米` : `${km.toFixed(1)} 公里`
}

export const POST_STATUS = {
  PENDING: { label: '待审核', type: 'warning' },
  PUBLISHED: { label: '已发布', type: 'success' },
  REJECTED: { label: '未通过', type: 'danger' },
  REMOVED: { label: '已下架', type: 'info' },
  EXPIRED: { label: '已过期', type: 'info' },
  RESOLVED: { label: '已解决', type: 'primary' }
}

export const statusLabel = (status) => (POST_STATUS[status] ? POST_STATUS[status].label : status || '-')

export const statusType = (status) => (POST_STATUS[status] ? POST_STATUS[status].type : 'info')

export const REPORT_STATUS = {
  PENDING: { label: '待处理', type: 'warning' },
  HANDLED: { label: '已处理', type: 'success' },
  REJECTED: { label: '已驳回', type: 'info' }
}

export const CLAIM_STATUS = {
  PENDING: { label: '待确认', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'info' },
  CANCELED: { label: '已取消', type: 'info' }
}

export const LOCATION_STATUS = {
  VERIFIED: { label: '已核验', type: 'success' },
  PENDING: { label: '待核验', type: 'warning' },
  REJECTED: { label: '已驳回', type: 'danger' }
}

export const VERIFY_RESULT = {
  TRUE: { label: '证实', type: 'success' },
  FALSE: { label: '证伪', type: 'danger' },
  UNKNOWN: { label: '存疑', type: 'info' }
}

export const truncate = (text, max = 80) => {
  if (!text) return ''
  return text.length > max ? `${text.slice(0, max)}…` : text
}

export const defaultCategoryColor = '#8fa3b0'
