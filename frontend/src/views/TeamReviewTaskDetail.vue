<template>
  <div class="fade-in" v-loading="loading">
    <div v-if="task">
      <!-- 返回 + 标题栏 -->
      <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:20px;">
        <div>
          <el-button link @click="router.back()" style="padding:0;margin-bottom:8px;">
            <el-icon><ArrowLeft /></el-icon> 返回
          </el-button>
          <h2 style="font-size:22px;margin:0;">
            {{ task.title }}
            <el-tag :type="statusType(task.status)" size="default" style="margin-left:12px;">
              {{ statusLabel(task.status) }}
            </el-tag>
          </h2>
          <p style="color:#909399;margin-top:6px;">{{ task.description || '暂无描述' }}</p>
        </div>
        <div style="display:flex;gap:8px;flex-shrink:0;">
          <el-button v-if="task.canSubmit && task.status !== 'REVIEWING'"
            type="success" :loading="submitting" @click="handleSubmit">
            <el-icon><Promotion /></el-icon> 提交代码并审查
          </el-button>
          <el-button v-if="task.status === 'COMPLETED'" type="warning"
            @click="goReport">
            <el-icon><View /></el-icon> 查看审查报告
          </el-button>
          <el-button v-if="task.canManage && task.status !== 'REVIEWING'"
            type="danger" plain @click="handleDelete">
            <el-icon><Delete /></el-icon> 删除任务
          </el-button>
        </div>
      </div>

      <el-row :gutter="16">
        <!-- 左侧：任务信息 -->
        <el-col :span="16">
          <el-card shadow="never" style="border-radius:8px;">
            <template #header><span style="font-weight:600;">任务信息</span></template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="所属团队">{{ task.teamName }}</el-descriptions-item>
              <el-descriptions-item label="关联项目">
                <el-link type="primary" @click="goProject">{{ task.projectName }}</el-link>
              </el-descriptions-item>
              <el-descriptions-item label="审查分支">
                <el-tag size="small">{{ task.reviewBranch }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="截止时间">
                <span :class="{ 'overdue': isOverdue }">{{ formatDateTime(task.deadline) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="发布人">{{ task.creatorName }}</el-descriptions-item>
              <el-descriptions-item label="发布时间">{{ formatDateTime(task.createTime) }}</el-descriptions-item>
              <el-descriptions-item label="最后提交人">
                {{ task.lastSubmitterName || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="最后提交时间">
                {{ formatDateTime(task.lastSubmitTime) }}
              </el-descriptions-item>
              <el-descriptions-item label="Commit Hash" :span="2">
                <span v-if="task.lastCommitHash" style="font-family:monospace;">
                  {{ task.lastCommitHash }}
                </span>
                <span v-else style="color:#c0c4cc;">尚未提交</span>
              </el-descriptions-item>
            </el-descriptions>

            <!-- 失败原因 -->
            <el-alert v-if="task.status === 'FAILED' && task.errorMsg" type="error"
              :closable="false" show-icon style="margin-top:16px;"
              :title="task.errorMsg" />

            <!-- 提交后处理进度（拉取/扫描/AI审查） -->
            <div v-if="task.status === 'REVIEWING'" style="margin-top:20px;">
              <el-steps :active="stageActive" align-center finish-status="success"
                process-status="process" style="margin-bottom:18px;">
                <el-step title="拉取代码" />
                <el-step title="扫描代码" />
                <el-step title="AI 审查" />
                <el-step title="完成" />
              </el-steps>

              <div v-if="task.stage === 'PULLING'">
                <el-text type="warning">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  正在从远端分支「{{ task.reviewBranch }}」拉取最新代码，仓库较大或网络较慢时请耐心等待，无需重复提交…
                </el-text>
              </div>
              <div v-else-if="task.stage === 'SCANNING'">
                <el-text type="warning">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  代码拉取成功（commit {{ shortHash(task.lastCommitHash) }}），正在扫描代码文件…
                </el-text>
              </div>
              <div v-else>
                <div style="display:flex;justify-content:space-between;margin-bottom:8px;">
                  <span>AI 审查进行中…</span>
                  <span>{{ progress.completed || 0 }} / {{ progress.total || 0 }}</span>
                </div>
                <el-progress :percentage="progress.percentage || 0" :stroke-width="14"
                  :status="progress.percentage === 100 ? 'success' : ''" striped striped-flow />
                <p style="font-size:12px;color:#909399;margin-top:8px;">
                  系统正在对最新提交的代码进行切片审查，完成后任务将自动标记为「已完成」。
                </p>
              </div>
            </div>

            <!-- 提交说明 -->
            <el-alert v-if="task.status === 'PENDING'" type="info" :closable="false"
              show-icon style="margin-top:16px;"
              title="等待提交"
              description="被指派成员请在本地 IDE 完成开发，commit 并 push 到审查分支后，点击右上角「提交代码并审查」。系统将拉取最新代码并自动触发 AI 审查。" />
          </el-card>
        </el-col>

        <!-- 右侧：指派成员 -->
        <el-col :span="8">
          <el-card shadow="never" style="border-radius:8px;">
            <template #header>
              <span style="font-weight:600;">指派成员 ({{ task.assignees?.length || 0 }})</span>
            </template>
            <div v-for="a in task.assignees" :key="a.userId"
              style="display:flex;align-items:center;gap:10px;padding:8px 0;
                     border-bottom:1px solid #f5f5f5;">
              <el-avatar :size="34" :src="a.avatar">
                {{ (a.username || '?').charAt(0).toUpperCase() }}
              </el-avatar>
              <div>
                <div style="font-size:14px;font-weight:500;">{{ a.username || '用户' + a.userId }}</div>
                <div style="font-size:11px;color:#c0c4cc;">ID: {{ a.userId }}</div>
              </div>
              <el-tag v-if="task.lastSubmitterId === a.userId" type="success" size="small"
                style="margin-left:auto;">最近提交</el-tag>
            </div>
          </el-card>

          <el-card shadow="never" style="border-radius:8px;margin-top:16px;">
            <template #header><span style="font-weight:600;">使用流程</span></template>
            <ol style="margin:0;padding-left:20px;font-size:13px;color:#606266;line-height:2;">
              <li>成员在本地 IDE 编写代码</li>
              <li>commit 并 push 到审查分支</li>
              <li>点击「提交代码并审查」</li>
              <li>系统拉取最新代码并触发 AI 审查</li>
              <li>审查完成后查看报告</li>
            </ol>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTeamTaskDetail, submitTeamTask, deleteTeamTask, getReviewProgress
} from '@/api'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => route.params.id)

const task = ref(null)
const loading = ref(false)
const submitting = ref(false)
const progress = ref({})
let progressTimer = null

const statusType = (s) => ({
  PENDING: 'info', REVIEWING: 'warning', COMPLETED: 'success', FAILED: 'danger'
}[s] || 'info')
const statusLabel = (s) => ({
  PENDING: '待提交', REVIEWING: '审查中', COMPLETED: '已完成', FAILED: '失败'
}[s] || s || '—')
const formatDateTime = (d) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }) : '—'
const shortHash = (h) => h ? h.substring(0, 7) : ''

// 步骤条当前激活步骤
const stageActive = computed(() => {
  const st = task.value?.stage
  if (st === 'PULLING') return 0
  if (st === 'SCANNING') return 1
  if (st === 'AI_REVIEWING') return 2
  return 0
})

const isOverdue = computed(() => {
  if (!task.value?.deadline || task.value.status === 'COMPLETED') return false
  return new Date(task.value.deadline).getTime() < Date.now()
})

const loadTask = async () => {
  loading.value = true
  try {
    const res = await getTeamTaskDetail(taskId.value)
    task.value = res.data
    if (task.value.status === 'REVIEWING') {
      startTaskPolling()
    }
  } catch { /* ignore */ }
  loading.value = false
}

// ==================== 提交代码并审查 ====================
const handleSubmit = async () => {
  try {
    await ElMessageBox.confirm(
      `系统将从分支「${task.value.reviewBranch}」拉取最新代码，记录最新 commit，重新扫描并触发 AI 审查。请确保代码已 push 到远端。`,
      '提交代码并审查',
      { confirmButtonText: '确认提交', cancelButtonText: '取消', type: 'info' }
    )
  } catch { return }

  submitting.value = true
  try {
    // 后端同步受理后立即返回，拉取/扫描/审查在后台进行
    const res = await submitTeamTask(taskId.value)
    ElMessage.success('提交已受理，正在后台拉取代码')
    task.value = res.data
    progress.value = {}
    startTaskPolling()
  } catch { /* ignore */ }
  submitting.value = false
}

// ==================== 任务状态轮询（以任务 stage 为准） ====================
const startTaskPolling = () => {
  clearInterval(progressTimer)
  progressTimer = setInterval(async () => {
    if (!task.value) return
    try {
      const res = await getTeamTaskDetail(taskId.value)
      const prev = task.value
      task.value = res.data
      const t = task.value

      if (t.status === 'COMPLETED') {
        clearInterval(progressTimer)
        progress.value = { percentage: 100 }
        ElMessage.success('审查完成')
        return
      }
      if (t.status === 'FAILED') {
        clearInterval(progressTimer)
        ElMessage.error(t.errorMsg || '审查失败，请查看详情')
        return
      }
      if (t.status !== 'REVIEWING') {
        clearInterval(progressTimer)
        return
      }

      // AI 审查阶段额外拉取项目切片进度
      if (t.stage === 'AI_REVIEWING') {
        try {
          const p = await getReviewProgress(t.projectId)
          progress.value = p.data || {}
        } catch { /* ignore */ }
      } else {
        progress.value = {}
      }
    } catch { /* 轮询偶发失败忽略，下个周期重试 */ }
  }, 2000)
}

// ==================== 删除 ====================
const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `确认删除任务「${task.value.title}」？删除后不可恢复。`,
      '删除确认',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteTeamTask(taskId.value)
    ElMessage.success('任务已删除')
    router.push('/team-tasks')
  } catch { /* ignore */ }
}

const goProject = () => router.push(`/projects/${task.value.projectId}`)
const goReport = () => router.push(`/projects/${task.value.projectId}/review`)

onMounted(loadTask)
onUnmounted(() => clearInterval(progressTimer))
</script>

<style scoped>
.overdue { color: #F56C6C; font-weight: 600; }
</style>
