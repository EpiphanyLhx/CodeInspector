import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, logout as apiLogout, getCurrentUser } from '@/api'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const userRole = computed(() => user.value?.role || 'VIEWER')

  async function login(credentials) {
    const res = await apiLogin(credentials)
    token.value = res.data.token
    user.value = {
      id: res.data.userId,
      username: res.data.username,
      email: res.data.email,
      avatar: res.data.avatar,
      role: res.data.role
    }
    localStorage.setItem('token', token.value)
    localStorage.setItem('user', JSON.stringify(user.value))
    return res
  }

  async function fetchUser() {
    try {
      const res = await getCurrentUser()
      user.value = res.data
      localStorage.setItem('user', JSON.stringify(user.value))
    } catch {
      // ignore
    }
  }

  async function logout() {
    try {
      await apiLogout()
    } finally {
      token.value = ''
      user.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      router.push('/login')
    }
  }

  return { token, user, isLoggedIn, userRole, login, logout, fetchUser }
})
