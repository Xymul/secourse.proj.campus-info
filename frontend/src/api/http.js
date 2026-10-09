import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 20000
})

// 统一携带登录令牌
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('campus_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 统一处理响应结构 { code, message, data }
http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload.code !== 'undefined' && payload.code !== 0) {
      if (payload.code === 401) {
        localStorage.removeItem('campus_token')
        localStorage.removeItem('campus_user')
      }
      return Promise.reject(new Error(payload.message || '请求失败'))
    }
    return payload ? payload.data : null
  },
  (error) => {
    const payload = error.response && error.response.data
    if (payload && payload.code === 401) {
      localStorage.removeItem('campus_token')
      localStorage.removeItem('campus_user')
    }
    const message = (payload && payload.message) || error.message || '网络异常，请稍后重试'
    return Promise.reject(new Error(message))
  }
)

export default http
