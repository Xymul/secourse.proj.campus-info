import http from './http'

/** 基础数据 */
export const metaApi = {
  schools: () => http.get('/schools'),
  categories: (includeDisabled = false) => http.get('/categories', { params: { includeDisabled } }),
  locations: (params) => http.get('/locations', { params }),
  searchEngine: () => http.get('/meta/search-engine'),
  hotKeywords: (schoolId, limit = 8) => http.get('/meta/hot-keywords', { params: { schoolId, limit } })
}

/** 账号 */
export const authApi = {
  login: (data) => http.post('/auth/login', data),
  register: (data) => http.post('/auth/register', data),
  logout: () => http.post('/auth/logout'),
  me: () => http.get('/auth/me')
}

/** 信息节点 */
export const postApi = {
  list: (params) => http.get('/posts', { params }),
  detail: (postId) => http.get(`/posts/${postId}`),
  publish: (data) => http.post('/posts', data),
  update: (postId, data) => http.put(`/posts/${postId}`, data),
  remove: (postId, reason) => http.delete(`/posts/${postId}`, { params: { reason } }),
  resolve: (postId) => http.post(`/posts/${postId}/resolve`),
  mine: () => http.get('/posts/mine'),
  hot: (params) => http.get('/posts/hot', { params }),
  map: (params) => http.get('/posts/map', { params }),
  nearby: (params) => http.get('/posts/nearby', { params }),
  cluster: (locationId) => http.get('/posts/cluster', { params: { locationId } })
}

/** 搜索 */
export const searchApi = {
  keyword: (params) => http.get('/search/keyword', { params }),
  ai: (params) => http.get('/search/ai', { params })
}

/** AI 辅助发布 */
export const aiApi = {
  suggest: (data) => http.post('/ai/suggest', data)
}

/** 互动 */
export const interactionApi = {
  comments: (postId) => http.get(`/posts/${postId}/comments`),
  addComment: (postId, content) => http.post(`/posts/${postId}/comments`, { content }),
  like: (postId) => http.post(`/posts/${postId}/like`),
  unlike: (postId) => http.delete(`/posts/${postId}/like`),
  verification: (postId) => http.get(`/posts/${postId}/verifications`),
  verify: (postId, data) => http.post(`/posts/${postId}/verify`, data),
  report: (postId, data) => http.post(`/posts/${postId}/reports`, data),
  claim: (postId, data) => http.post(`/posts/${postId}/claims`, data),
  claims: (postId) => http.get(`/posts/${postId}/claims`),
  handleClaim: (claimId, data) => http.post(`/claims/${claimId}/handle`, data),
  myInteractions: () => http.get('/me/interactions')
}

/** 管理后台 */
export const adminApi = {
  dashboard: (schoolId) => http.get('/admin/dashboard', { params: { schoolId } }),
  analytics: (schoolId) => http.get('/admin/analytics', { params: { schoolId } }),
  posts: (params) => http.get('/admin/posts', { params }),
  postStats: (schoolId) => http.get('/admin/posts/stats', { params: { schoolId } }),
  audit: (postId, data) => http.post(`/admin/posts/${postId}/audit`, data),
  flagged: (limit = 20) => http.get('/admin/posts/flagged', { params: { limit } }),
  users: (params) => http.get('/admin/users', { params }),
  updateUserStatus: (userId, data) => http.post(`/admin/users/${userId}/status`, data),
  reports: (params) => http.get('/admin/reports', { params }),
  handleReport: (reportId, data) => http.post(`/admin/reports/${reportId}/handle`, data),
  categories: () => http.get('/admin/categories'),
  saveCategory: (data, categoryId) => (categoryId
    ? http.put(`/admin/categories/${categoryId}`, data)
    : http.post('/admin/categories', data)),
  locations: (params) => http.get('/admin/locations', { params }),
  saveLocation: (data, locationId) => (locationId
    ? http.put(`/admin/locations/${locationId}`, data)
    : http.post('/admin/locations', data)),
  verifyLocation: (locationId, approved, remark) => http.post(`/admin/locations/${locationId}/verify`,
    null, { params: { approved, remark } }),
  deleteLocation: (locationId) => http.delete(`/admin/locations/${locationId}`),
  tasks: (params) => http.get('/admin/tasks', { params }),
  triggerExpire: () => http.post('/admin/tasks/expire'),
  logs: (params) => http.get('/admin/logs', { params })
}
