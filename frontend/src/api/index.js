import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器
http.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器
http.interceptors.response.use(
  res => {
    if (res.data.code !== 200) {
      ElMessage.error(res.data.message || '请求失败')
      return Promise.reject(res.data)
    }
    return res.data
  },
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(err.response?.data?.message || '网络错误')
    }
    return Promise.reject(err)
  }
)

// ================ 认证 ================
export const login = (data) => http.post('/auth/login', data)
export const logout = () => http.post('/auth/logout')
export const getCurrentUser = () => http.get('/auth/me')
export const updateProfile = (data) => http.put('/auth/profile', data)
export const changePassword = (data) => http.put('/auth/password', data)
export const uploadAvatar = (file) => {
  const fd = new FormData(); fd.append('file', file)
  return http.post('/auth/avatar', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export const register = (params) => http.post('/auth/register', null, { params })

// ================ 团队 ================
export const createTeam = (data) => http.post('/teams', data)
export const getMyTeams = () => http.get('/teams/my')
export const getTeamMembers = (teamId) => http.get(`/teams/${teamId}/members`)
export const addTeamMember = (teamId, userId, role) =>
  http.post(`/teams/${teamId}/members`, { userId, role })
export const removeTeamMember = (teamId, userId) =>
  http.delete(`/teams/${teamId}/members/${userId}`)
export const deleteTeam = (teamId) => http.delete(`/teams/${teamId}`)

// ================ 项目 ================
export const createProject = (data) => http.post('/projects', data)
export const getProjectList = (page = 1, size = 10) =>
  http.get('/projects', { params: { page, size } })
export const getProjectDetail = (id) => http.get(`/projects/${id}`)
export const uploadProjectCode = (projectId, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post(`/projects/${projectId}/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export const pullFromGit = (projectId) =>
  http.post(`/projects/${projectId}/git-pull`)
export const deleteProject = (projectId) =>
  http.delete(`/projects/${projectId}`)
export const deleteProjectFile = (projectId, fileId) =>
  http.delete(`/projects/${projectId}/files/${fileId}`)

// ================ 代码文件 ================
export const getProjectFiles = (projectId) =>
  http.get(`/code/projects/${projectId}/files`)
export const getFileContent = (fileId) =>
  http.get(`/code/files/${fileId}`)
export const getProjectChunks = (projectId) =>
  http.get(`/code/projects/${projectId}/chunks`)
export const getCodeHistory = (userId, status) =>
  http.get('/code/history', { params: { userId, status } })
export const deleteCodeHistory = (projectId) =>
  http.delete(`/code/history/${projectId}`)

// ================ 审查 ================
export const startReview = (projectId, data) =>
  http.post(`/review/projects/${projectId}/start`, data || {})
export const analyzeProjectStyle = (projectId) =>
  http.post(`/review/projects/${projectId}/analyze-style`)
export const getProjectStyle = (projectId) =>
  http.get(`/review/projects/${projectId}/style`)
export const updateProjectStyle = (projectId, styleProfile) =>
  http.put(`/review/projects/${projectId}/style`, { styleProfile })
export const getReviewProgress = (projectId) =>
  http.get(`/review/projects/${projectId}/progress`)
export const getProjectIssues = (projectId) =>
  http.get(`/review/projects/${projectId}/issues`)
export const getFileIssues = (projectId, filePath) =>
  http.get(`/review/projects/${projectId}/files/${encodeURIComponent(filePath)}/issues`)
export const getIssueStats = (projectId) =>
  http.get(`/review/projects/${projectId}/stats`)
export const getReviewReport = (projectId) =>
  http.get(`/review/projects/${projectId}/report`)

// ================ 统计 ================
export const getDashboard = () => http.get('/stats/dashboard')
export const getBugRateTrend = () => http.get('/stats/bug-rate-trend')

// ================ API Key 管理 ================
export const getApiKeys = () => http.get('/api-keys')
export const saveApiKey = (data) => http.post('/api-keys', data)
export const activateApiKey = (id) => http.put(`/api-keys/${id}/activate`)
export const validateApiKey = (id) => http.post(`/api-keys/${id}/validate`)
export const deleteApiKey = (id) => http.delete(`/api-keys/${id}`)
export const getModelsForProvider = (provider) => http.get(`/api-keys/models/${provider}`)

export default http
