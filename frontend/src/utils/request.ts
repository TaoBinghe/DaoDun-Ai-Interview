import axios from 'axios'
import { ElMessage } from 'element-plus'
import { forceRelogin } from './authSession'

const request = axios.create({
  baseURL: 'http://localhost:8081',
  timeout: 5000
})

/** 刷新 Token 的 Promise，保证并发 401 只触发一次刷新 */
let refreshPromise: Promise<string> | null = null

function doRefresh(): Promise<string> {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) {
    return Promise.reject(new Error('请重新登录'))
  }
  return axios
    .post<{ code: number; msg?: string; data?: { accessToken: string; refreshToken?: string } }>(
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
      return Promise.reject(new Error(res.data?.msg || '刷新失败'))
    })
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
    // 兼容 HTTP 200 但业务码表示未登录（防御性）
    if (res?.code === 401) {
      forceRelogin(res.msg || '未登录或 Token 已过期，请重新登录')
      return Promise.reject(new Error(res.msg || '未登录'))
    }
    if (res.code !== 200) {
      ElMessage.error(res.msg || 'Error')
      return Promise.reject(new Error(res.msg || 'Error'))
    }
    return res
  },
  async (error) => {
    const originalRequest = error.config

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      if (originalRequest.url?.includes('/api/auth/refresh')) {
        forceRelogin(
          error.response?.data?.msg || '未登录或 Token 已过期，请重新登录'
        )
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
        forceRelogin('登录已过期，请重新登录')
        return Promise.reject(error)
      }
    }

    // 刷新已试过仍 401，或其它请求直接 401（带 _retry）
    if (error.response?.status === 401) {
      forceRelogin(
        error.response?.data?.msg || '未登录或 Token 已过期，请重新登录'
      )
      return Promise.reject(error)
    }

    const msg =
      error.response?.data?.msg || error.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
export { forceRelogin }
