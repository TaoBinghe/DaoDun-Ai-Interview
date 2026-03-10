import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import request from '../utils/request'

export interface UserInfo {
  userId: number
  username: string
  email: string
}

export const useUserStore = defineStore('user', () => {
  const user = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!user.value)

  function setUser(info: UserInfo | null) {
    user.value = info
  }

  function clearUser() {
    user.value = null
  }

  /** 从服务端拉取当前用户信息（需已登录） */
  async function fetchUser() {
    try {
      const res = await request.get<{ data: UserInfo }>('/api/user/me')
      const info = res?.data
      if (info) {
        user.value = info
        return info
      }
    } catch {
      clearUser()
    }
    return null
  }

  return { user, isLoggedIn, setUser, clearUser, fetchUser }
})
