<template>
  <div class="fade-in">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
      <h2 style="font-size:22px;font-weight:600;">团队管理</h2>
      <div style="display:flex;gap:8px;">
        <el-button @click="showJoinDialog = true">
          <el-icon><Connection /></el-icon> 加入团队
        </el-button>
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon> 创建团队
        </el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="8" v-for="team in teams" :key="team.id" style="margin-bottom:16px;">
        <el-card shadow="hover" style="border-radius:8px;">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center;">
              <span style="font-weight:600;font-size:15px;">{{ team.name }}</span>
              <el-tag :type="roleType(team.myRole)" size="small">{{ roleLabel(team.myRole) }}</el-tag>
            </div>
          </template>
          <p style="font-size:13px;color:#909399;min-height:40px;margin:0;">
            {{ team.description || '暂无描述' }}
          </p>
          <div style="display:flex;gap:12px;font-size:12px;color:#c0c4cc;margin-top:8px;">
            <span>{{ team.memberCount }} 名成员</span>
            <span>创建于 {{ formatDate(team.createTime) }}</span>
          </div>
          <div style="display:flex;justify-content:space-between;align-items:center;margin-top:10px;">
            <el-button link type="primary" size="small" @click="openTeamDetail(team)">
              {{ isAdmin(team.myRole) ? '管理团队' : '查看详情' }}
            </el-button>
            <el-button v-if="team.myRole === 'LEADER'" link type="danger" size="small" @click="handleDeleteTeam(team)">
              删除团队
            </el-button>
          </div>
        </el-card>
      </el-col>
      <el-col v-if="teams.length === 0" :span="24">
        <el-empty description="暂无团队，创建一个团队或通过邀请码加入吧" />
      </el-col>
    </el-row>

    <!-- 创建团队对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建团队" width="450px">
      <el-form :model="createForm">
        <el-form-item label="团队名称" required>
          <el-input v-model="createForm.name" placeholder="请输入团队名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="团队描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="可选" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 加入团队对话框 -->
    <el-dialog v-model="showJoinDialog" title="加入团队" width="420px">
      <el-form>
        <el-form-item label="邀请码">
          <el-input v-model="joinCode" placeholder="请输入团队邀请码" maxlength="16" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showJoinDialog = false">取消</el-button>
        <el-button type="primary" @click="handleJoin">加入</el-button>
      </template>
    </el-dialog>

    <!-- 团队详情对话框（成员管理 + 团队项目） -->
    <el-dialog v-model="showDetailDialog" :title="selectedTeam?.name" width="860px" top="5vh">
      <el-tabs v-model="detailTab">
        <!-- 成员管理 -->
        <el-tab-pane label="成员管理" name="members">
          <!-- 邀请码区域（仅管理员可见） -->
          <div v-if="selectedTeam && isAdmin(selectedTeam.myRole)" class="invite-section">
            <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
              <span style="font-size:13px;color:#606266;white-space:nowrap;">团队邀请码：</span>
              <el-input v-model="inviteCode" readonly placeholder="尚未生成" style="width:180px;" />
              <el-button size="small" @click="copyInviteCode" :disabled="!inviteCode">复制</el-button>
              <el-button v-if="!inviteCode" size="small" type="primary" @click="handleGenerateCode">生成邀请码</el-button>
              <el-button v-else size="small" type="warning" @click="handleRegenerateCode">重新生成</el-button>
            </div>
          </div>

          <!-- 添加成员（仅管理员可见） -->
          <div v-if="selectedTeam && isAdmin(selectedTeam.myRole)" style="display:flex;gap:8px;margin:16px 0;">
            <el-input v-model="newMemberId" placeholder="输入用户ID添加成员" style="width:200px;" />
            <el-select v-if="selectedTeam.myRole === 'LEADER'" v-model="newMemberRole" style="width:130px;">
              <el-option label="普通成员" value="MEMBER" />
              <el-option label="管理员" value="ADMIN" />
            </el-select>
            <el-button type="primary" @click="handleAddMember">添加成员</el-button>
          </div>

          <el-table :data="members" style="width:100%">
            <el-table-column label="成员" min-width="180">
              <template #default="{ row }">
                <div style="display:flex;align-items:center;gap:8px;">
                  <el-avatar :size="32" :src="row.avatar">{{ (row.username || '?').charAt(0).toUpperCase() }}</el-avatar>
                  <div>
                    <div style="font-size:13px;font-weight:500;">{{ row.username || '用户' + row.userId }}</div>
                    <div style="font-size:11px;color:#c0c4cc;">ID: {{ row.userId }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="email" label="邮箱" min-width="160">
              <template #default="{ row }">{{ row.email || '—' }}</template>
            </el-table-column>
            <el-table-column label="角色" width="130">
              <template #default="{ row }">
                <el-select
                  v-if="canEditRole(row)"
                  v-model="row.role"
                  size="small"
                  style="width:110px;"
                  @change="(val) => handleRoleChange(row, val)"
                >
                  <el-option label="普通成员" value="MEMBER" />
                  <el-option label="管理员" value="ADMIN" />
                </el-select>
                <el-tag v-else :type="roleType(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="加入时间" width="110">
              <template #default="{ row }">{{ formatDate(row.joinTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openMemberDetail(row)">查看项目/审查</el-button>
                <el-button
                  v-if="canRemoveMember(row)"
                  link type="danger" size="small"
                  @click="handleRemoveMember(row.userId)"
                >移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 团队项目 -->
        <el-tab-pane label="团队项目" name="projects">
          <el-table :data="teamProjects" style="width:100%">
            <el-table-column prop="name" label="项目名称" min-width="180">
              <template #default="{ row }">
                <el-link type="primary" @click="goToProject(row.id)">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="language" label="语言" width="90" />
            <el-table-column label="审查状态" width="110">
              <template #default="{ row }">
                <el-tag :type="reviewStatusType(row.reviewStatus)" size="small">{{ reviewStatusLabel(row.reviewStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="creatorName" label="创建者" width="120" />
            <el-table-column label="创建时间" width="110">
              <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="goToReview(row.id)">审查记录</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 成员项目/审查详情对话框 -->
    <el-dialog v-model="showMemberDialog" :title="`${memberDetail?.username || '成员'} 的项目与审查`" width="780px" top="6vh">
      <el-tabs v-model="memberTab">
        <el-tab-pane :label="`创建的项目 (${memberProjects.length})`" name="projects">
          <el-table :data="memberProjects" style="width:100%">
            <el-table-column prop="name" label="项目名称" min-width="200">
              <template #default="{ row }">
                <el-link type="primary" @click="goToProject(row.id)">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="language" label="语言" width="90" />
            <el-table-column label="审查状态" width="110">
              <template #default="{ row }">
                <el-tag :type="reviewStatusType(row.reviewStatus)" size="small">{{ reviewStatusLabel(row.reviewStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="120">
              <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="memberProjects.length === 0" description="该成员暂无项目" :image-size="60" />
        </el-tab-pane>
        <el-tab-pane :label="`提交的审查 (${memberReviews.length})`" name="reviews">
          <el-table :data="memberReviews" style="width:100%">
            <el-table-column label="所属项目" min-width="200">
              <template #default="{ row }">
                <el-link type="primary" @click="goToReview(row.projectId)">{{ row.projectName || '项目#' + row.projectId }}</el-link>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="reviewStatusType(row.status)" size="small">{{ reviewStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="aiModel" label="模型" width="130">
              <template #default="{ row }">{{ row.aiModel || '—' }}</template>
            </el-table-column>
            <el-table-column label="提交时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="memberReviews.length === 0" description="该成员暂无审查记录" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMyTeams, createTeam, deleteTeam,
  getTeamMembers, addTeamMember, removeTeamMember, updateTeamMemberRole,
  getTeamInviteCode, generateTeamInviteCode, regenerateTeamInviteCode, joinTeamByCode,
  getTeamProjects, getMemberProjects, getMemberReviews
} from '@/api'

const router = useRouter()
const authStore = useAuthStore()

// ==================== 状态 ====================
const teams = ref([])
const showCreateDialog = ref(false)
const showJoinDialog = ref(false)
const showDetailDialog = ref(false)
const showMemberDialog = ref(false)
const detailTab = ref('members')
const memberTab = ref('projects')

const createForm = reactive({ name: '', description: '' })
const joinCode = ref('')

const selectedTeam = ref(null)
const members = ref([])
const teamProjects = ref([])
const inviteCode = ref('')
const newMemberId = ref('')
const newMemberRole = ref('MEMBER')

const memberDetail = ref(null)
const memberProjects = ref([])
const memberReviews = ref([])

// ==================== 工具方法 ====================
const roleType = (r) => ({ LEADER: 'danger', ADMIN: 'warning', MEMBER: 'success' }[r] || 'info')
const roleLabel = (r) => ({ LEADER: '负责人', ADMIN: '管理员', MEMBER: '成员' }[r] || r)
const isAdmin = (role) => role === 'LEADER' || role === 'ADMIN'
const isLeader = (role) => role === 'LEADER'

const reviewStatusType = (s) => ({
  COMPLETED: 'success', IN_PROGRESS: 'warning', PROCESSING: 'warning',
  QUEUED: 'warning', PENDING: 'info', FAILED: 'danger'
}[s] || 'info')
const reviewStatusLabel = (s) => ({
  COMPLETED: '已完成', IN_PROGRESS: '审查中', PROCESSING: '审查中',
  QUEUED: '排队中', PENDING: '待审查', FAILED: '失败'
}[s] || s || '—')

const formatDate = (d) => d ? new Date(d).toLocaleDateString('zh-CN') : ''
const formatDateTime = (d) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }) : ''

// 当前登录用户能否编辑某成员的角色（仅 LEADER，且不能改 owner 和自己）
const canEditRole = (row) => {
  if (!selectedTeam.value || selectedTeam.value.myRole !== 'LEADER') return false
  if (row.role === 'LEADER') return false
  if (row.userId === authStore.user?.id) return false
  return true
}

// 当前登录用户能否移除某成员（LEADER/ADMIN，不能移除 owner 和自己）
const canRemoveMember = (row) => {
  if (!selectedTeam.value || !isAdmin(selectedTeam.value.myRole)) return false
  if (row.role === 'LEADER') return false
  if (row.userId === authStore.user?.id) return false
  return true
}

// ==================== 数据加载 ====================
const loadTeams = async () => {
  try {
    const res = await getMyTeams()
    teams.value = res.data || []
  } catch { /* ignore */ }
}

const loadInviteCode = async (teamId) => {
  try {
    const res = await getTeamInviteCode(teamId)
    inviteCode.value = res.data || ''
  } catch { inviteCode.value = '' }
}

const loadMembers = async (teamId) => {
  try {
    const res = await getTeamMembers(teamId)
    members.value = res.data || []
  } catch { members.value = [] }
}

const loadTeamProjects = async (teamId) => {
  try {
    const res = await getTeamProjects(teamId)
    teamProjects.value = res.data || []
  } catch { teamProjects.value = [] }
}

// ==================== 团队详情 ====================
const openTeamDetail = async (team) => {
  selectedTeam.value = team
  detailTab.value = 'members'
  showDetailDialog.value = true
  inviteCode.value = ''
  newMemberId.value = ''
  newMemberRole.value = 'MEMBER'
  await Promise.all([
    loadMembers(team.id),
    loadTeamProjects(team.id),
    isAdmin(team.myRole) ? loadInviteCode(team.id) : Promise.resolve()
  ])
}

// ==================== 创建团队 ====================
const handleCreate = async () => {
  if (!createForm.name.trim()) { ElMessage.warning('请输入团队名称'); return }
  try {
    await createTeam({ name: createForm.name.trim(), description: createForm.description })
    ElMessage.success('团队创建成功')
    showCreateDialog.value = false
    createForm.name = ''
    createForm.description = ''
    loadTeams()
  } catch { /* ignore */ }
}

// ==================== 加入团队 ====================
const handleJoin = async () => {
  if (!joinCode.value.trim()) { ElMessage.warning('请输入邀请码'); return }
  try {
    await joinTeamByCode(joinCode.value.trim())
    ElMessage.success('加入团队成功')
    showJoinDialog.value = false
    joinCode.value = ''
    loadTeams()
  } catch { /* ignore */ }
}

// ==================== 删除团队 ====================
const handleDeleteTeam = async (team) => {
  try {
    await ElMessageBox.confirm(
      `确认删除团队「${team.name}」？\n\n若团队下存在项目将无法删除。`,
      '删除团队',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteTeam(team.id)
    ElMessage.success('团队已删除')
    loadTeams()
  } catch { /* ignore cancel or error */ }
}

// ==================== 邀请码 ====================
const copyInviteCode = async () => {
  if (!inviteCode.value) return
  try {
    await navigator.clipboard.writeText(inviteCode.value)
    ElMessage.success('邀请码已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

const handleGenerateCode = async () => {
  try {
    const res = await generateTeamInviteCode(selectedTeam.value.id)
    inviteCode.value = res.data
    ElMessage.success('邀请码已生成')
  } catch { /* ignore */ }
}

const handleRegenerateCode = async () => {
  try {
    await ElMessageBox.confirm('重新生成后旧邀请码将立即失效，确认继续？', '提示', { type: 'warning' })
    const res = await regenerateTeamInviteCode(selectedTeam.value.id)
    inviteCode.value = res.data
    ElMessage.success('邀请码已重新生成')
  } catch { /* ignore */ }
}

// ==================== 成员管理 ====================
const handleAddMember = async () => {
  if (!newMemberId.value) { ElMessage.warning('请输入用户ID'); return }
  try {
    await addTeamMember(selectedTeam.value.id, Number(newMemberId.value), newMemberRole.value)
    ElMessage.success('添加成功')
    newMemberId.value = ''
    newMemberRole.value = 'MEMBER'
    loadMembers(selectedTeam.value.id)
    loadTeams()
  } catch { /* ignore */ }
}

const handleRemoveMember = async (userId) => {
  try {
    await ElMessageBox.confirm('确认移除该成员？', '提示', { type: 'warning' })
    await removeTeamMember(selectedTeam.value.id, userId)
    ElMessage.success('已移除')
    loadMembers(selectedTeam.value.id)
    loadTeams()
  } catch { /* ignore */ }
}

const handleRoleChange = async (row, newRole) => {
  try {
    await updateTeamMemberRole(selectedTeam.value.id, row.userId, newRole)
    ElMessage.success('角色已更新')
    loadMembers(selectedTeam.value.id)
  } catch {
    loadMembers(selectedTeam.value.id)
  }
}

// ==================== 成员项目/审查详情 ====================
const openMemberDetail = async (row) => {
  memberDetail.value = row
  memberTab.value = 'projects'
  showMemberDialog.value = true
  memberProjects.value = []
  memberReviews.value = []
  try {
    const [projRes, revRes] = await Promise.all([
      getMemberProjects(selectedTeam.value.id, row.userId),
      getMemberReviews(selectedTeam.value.id, row.userId)
    ])
    memberProjects.value = projRes.data || []
    memberReviews.value = revRes.data || []
  } catch { /* ignore */ }
}

// ==================== 页面跳转 ====================
const goToProject = (projectId) => {
  showDetailDialog.value = false
  showMemberDialog.value = false
  router.push(`/projects/${projectId}`)
}

const goToReview = (projectId) => {
  showDetailDialog.value = false
  showMemberDialog.value = false
  router.push(`/projects/${projectId}/review`)
}

onMounted(loadTeams)
</script>

<style scoped>
.invite-section {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 8px;
}
</style>
