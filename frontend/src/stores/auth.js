import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, logout as apiLogout, getCurrentUser } from '@/api'
import router from '@/router'

// 解码 JWT payload（不上传到服务器，仅解析 Base64）
function parseJwtPayload(token) {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    return JSON.parse(atob(parts[1]))
  } catch {
    return null
  }
}

// 检查 JWT 是否已过期（基于客户端本地时间，允许 30s 误差）
function isJwtExpired(token) {
  if (!token) return true
  const payload = parseJwtPayload(token)
  if (!payload || !payload.exp) return true
  return (payload.exp * 1000) < (Date.now() - 30000)
}

// 初始化时清理过期 token
function loadToken() {
  const stored = localStorage.getItem('token')
  if (stored && isJwtExpired(stored)) {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    return ''
  }
  return stored || ''
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(loadToken())
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  const isLoggedIn = computed(() => {
    if (!token.value) return false
    if (isJwtExpired(token.value)) {
      // 过期则主动清理
      token.value = ''
      user.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      return false
    }
    return true
  })
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
