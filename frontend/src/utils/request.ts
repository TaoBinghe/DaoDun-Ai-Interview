import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: 'http://localhost:8081',
  timeout: 5000
})

/** 刷新 Token 的 Promise，保证并发 401 只触发一次刷新 */
let refreshPromise: Promise<string> | null = null

function doRefresh(): Promise<string> {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) {
    toLogin()
    return Promise.reject(new Error('请重新登录'))
  }
  return axios
    .post<{ code: number; msg?: string; data?: { accessToken: string } }>(
      'http://localhost:8081/api/auth/refresh',
      { refreshToken }
    )
    .then((res) => {
      const { code, data } = res.data
      if (code === 200 && data?.accessToken) {
        localStorage.setItem('accessToken', data.accessToken)
        if (data.refreshToken) {
          localStorage.setItem('refreshToken', data.refreshToken)
        }
        return data.accessToken
      }
      toLogin()
      return Promise.reject(new Error(res.data?.msg || '刷新失败'))
    })
    .catch((err) => {
      toLogin()
      return Promise.reject(err)
    })
}

function toLogin() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  router.push('/login').catch(() => {})
  ElMessage.error('登录已过期，请重新登录')
}

// Request interceptor
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.msg || 'Error')
      return Promise.reject(new Error(res.msg || 'Error'))
    }
    return res
  },
  async (error) => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (originalRequest.url?.includes('/api/auth/refresh')) {
        toLogin()
        return Promise.reject(error)
      }
      originalRequest._retry = true
      if (!refreshPromise) {
        refreshPromise = doRefresh().finally(() => {
          refreshPromise = null
        })
      }
      try {
        const newToken = await refreshPromise
        originalRequest.headers['Authorization'] = `Bearer ${newToken}`
        return request(originalRequest)
      } catch {
        return Promise.reject(error)
      }
    }

    const msg =
      error.response?.data?.msg || error.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
