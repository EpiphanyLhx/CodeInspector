<template>
  <div class="fade-in">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
      <h2 style="font-size:22px;font-weight:600;">审查任务</h2>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon> 发布任务
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="loadTasks">
      <el-tab-pane label="指派给我的" name="assigned" />
      <el-tab-pane label="我发布的" name="created" />
      <el-tab-pane label="全部" name="all" />
    </el-tabs>

    <el-table :data="tasks" v-loading="loading" style="width:100%" stripe>
      <el-table-column label="任务标题" min-width="200">
        <template #default="{ row }">
          <el-link type="primary" @click="goDetail(row.id)">{{ row.title }}</el-link>
          <div v-if="row.description" style="font-size:12px;color:#909399;margin-top:2px;
               white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:320px;">
            {{ row.description }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="teamName" label="团队" width="130" />
      <el-table-column label="项目" min-width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="goProject(row.projectId)">{{ row.projectName }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="分支" width="110">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.reviewBranch }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最后提交" width="120">
        <template #default="{ row }">
          <span v-if="row.lastSubmitterName">{{ row.lastSubmitterName }}</span>
          <span v-else style="color:#c0c4cc;">—</span>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.lastSubmitTime) }}</template>
      </el-table-column>
      <el-table-column label="Commit" width="100">
        <template #default="{ row }">
          <span v-if="row.lastCommitHash" style="font-family:monospace;font-size:12px;">
            {{ shortHash(row.lastCommitHash) }}
          </span>
          <span v-else style="color:#c0c4cc;">—</span>
        </template>
      </el-table-column>
      <el-table-column label="截止时间" width="120">
        <template #default="{ row }">
          <span :class="{ 'overdue': isOverdue(row) }">{{ formatDate(row.deadline) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="goDetail(row.id)">查看</el-button>
          <el-button v-if="row.canSubmit && row.status !== 'REVIEWING'"
            link type="success" size="small" @click="goDetail(row.id)">提交</el-button>
          <el-button v-if="row.canManage" link type="danger" size="small"
            @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无审查任务" />
      </template>
    </el-table>

    <!-- 发布任务对话框 -->
    <el-dialog v-model="showCreateDialog" title="发布审查任务" width="600px" @closed="resetCreateForm">
      <el-form :model="createForm" label-width="90px" v-loading="creating">
        <el-form-item label="所属团队" required>
          <el-select v-model="createForm.teamId" placeholder="选择团队" style="width:100%"
            @change="onTeamChange">
            <el-option v-for="t in myTeams" :key="t.id" :label="t.name" :value="t.id">
              <span>{{ t.name }}</span>
              <span style="float:right;color:#909399;font-size:12px;">{{ roleLabel(t.myRole) }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="Git 项目" required>
          <el-select v-model="createForm.projectId" placeholder="选择团队下的 Git 项目"
            style="width:100%" :disabled="!createForm.teamId" @change="onProjectChange">
            <el-option v-for="p in teamGitProjects" :key="p.id" :label="p.name" :value="p.id">
              <span>{{ p.name }}</span>
              <span style="float:right;color:#909399;font-size:12px;">{{ p.language }}</span>
            </el-option>
          </el-select>
          <div v-if="createForm.teamId && teamGitProjects.length === 0"
            style="font-size:12px;color:#E6A23C;margin-top:4px;">
            该团队下暂无 Git 类型项目，请先在「项目管理」中创建
          </div>
        </el-form-item>
        <el-form-item label="任务标题" required>
          <el-input v-model="createForm.title" placeholder="例如：用户模块 v1.2 代码审查" maxlength="256" />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3"
            placeholder="说明本次审查的范围、要求等（可选）" maxlength="2048" />
        </el-form-item>
        <el-form-item label="审查分支" required>
          <el-input v-model="createForm.reviewBranch" placeholder="例如：main / develop / feature/login" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="createForm.deadline" type="datetime"
            placeholder="选择截止时间（可选）" style="width:100%"
            value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="指派成员" required>
          <el-select v-model="createForm.assigneeIds" multiple filterable
            placeholder="选择被指派成员" style="width:100%" :disabled="!createForm.teamId">
            <el-option v-for="m in teamMembers" :key="m.userId"
              :label="m.username + ' (' + roleLabel(m.role) + ')'" :value="m.userId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTeamTasks, createTeamTask, deleteTeamTask,
  getMyTeams, getTeamProjects, getTeamMembers
} from '@/api'

const router = useRouter()

const activeTab = ref('assigned')
const tasks = ref([])
const loading = ref(false)

const statusType = (s) => ({
  PENDING: 'info', REVIEWING: 'warning', COMPLETED: 'success', FAILED: 'danger'
}[s] || 'info')
const statusLabel = (s) => ({
  PENDING: '待提交', REVIEWING: '审查中', COMPLETED: '已完成', FAILED: '失败'
}[s] || s || '—')
const roleLabel = (r) => ({ LEADER: '负责人', ADMIN: '管理员', MEMBER: '成员' }[r] || r)

const formatDate = (d) => d ? new Date(d).toLocaleDateString('zh-CN') : '—'
const formatDateTime = (d) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }) : '—'
const shortHash = (h) => h ? h.substring(0, 7) : '—'
const isOverdue = (row) => {
  if (!row.deadline || row.status === 'COMPLETED') return false
  return new Date(row.deadline).getTime() < Date.now()
}

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await getTeamTasks(activeTab.value)
    tasks.value = res.data || []
  } catch { /* ignore */ }
  loading.value = false
}

const goDetail = (id) => router.push(`/team-tasks/${id}`)
const goProject = (projectId) => router.push(`/projects/${projectId}`)

// ==================== 发布任务 ====================
const showCreateDialog = ref(false)
const creating = ref(false)
const myTeams = ref([])
const teamGitProjects = ref([])
const teamMembers = ref([])

const createForm = reactive({
  teamId: null,
  projectId: null,
  title: '',
  description: '',
  reviewBranch: 'main',
  deadline: null,
  assigneeIds: []
})

const openCreateDialog = async () => {
  showCreateDialog.value = true
  try {
    const res = await getMyTeams()
    myTeams.value = res.data || []
    if (myTeams.value.length === 0) {
      ElMessage.info('请先创建或加入一个团队')
    }
  } catch { /* ignore */ }
}

const onTeamChange = async (teamId) => {
  createForm.projectId = null
  createForm.assigneeIds = []
  teamGitProjects.value = []
  teamMembers.value = []
  if (!teamId) return
  try {
    const [projRes, memRes] = await Promise.all([
      getTeamProjects(teamId),
      getTeamMembers(teamId)
    ])
    teamGitProjects.value = (projRes.data || []).filter(p => p.sourceType === 'GIT')
    teamMembers.value = memRes.data || []
  } catch { /* ignore */ }
}

const onProjectChange = (projectId) => {
  const p = teamGitProjects.value.find(x => x.id === projectId)
  if (p && p.reviewBranch) {
    createForm.reviewBranch = p.reviewBranch
  }
}

const handleCreate = async () => {
  if (!createForm.teamId) { ElMessage.warning('请选择团队'); return }
  if (!createForm.projectId) { ElMessage.warning('请选择 Git 项目'); return }
  if (!createForm.title.trim()) { ElMessage.warning('请填写任务标题'); return }
  if (!createForm.reviewBranch.trim()) { ElMessage.warning('请填写审查分支'); return }
  if (!createForm.assigneeIds.length) { ElMessage.warning('请至少指派一名成员'); return }

  creating.value = true
  try {
    await createTeamTask({
      teamId: createForm.teamId,
      projectId: createForm.projectId,
      title: createForm.title.trim(),
      description: createForm.description,
      reviewBranch: createForm.reviewBranch.trim(),
      deadline: createForm.deadline,
      assigneeIds: createForm.assigneeIds
    })
    ElMessage.success('任务发布成功')
    showCreateDialog.value = false
    activeTab.value = 'created'
    loadTasks()
  } catch { /* ignore */ }
  creating.value = false
}

const resetCreateForm = () => {
  createForm.teamId = null
  createForm.projectId = null
  createForm.title = ''
  createForm.description = ''
  createForm.reviewBranch = 'main'
  createForm.deadline = null
  createForm.assigneeIds = []
  teamGitProjects.value = []
  teamMembers.value = []
}

// ==================== 删除 ====================
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除任务「${row.title}」？删除后不可恢复。`,
      '删除确认',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteTeamTask(row.id)
    ElMessage.success('任务已删除')
    loadTasks()
  } catch { /* ignore */ }
}

onMounted(loadTasks)
</script>

<style scoped>
.overdue { color: #F56C6C; font-weight: 600; }
</style>
