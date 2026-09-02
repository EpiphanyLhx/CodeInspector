<template>
  <div class="fade-in">
    <h2 style="font-size:22px;font-weight:600;margin-bottom:20px;">代码仓</h2>

    <div v-loading="historyLoading">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;flex-wrap:wrap;gap:8px;">
        <span style="font-size:14px;color:var(--text-secondary);">
          上传记录（{{ history.length }}条）
          <span v-if="statusFilter" style="color:#409EFF;"> · 已筛选</span>
        </span>
        <div style="display:flex;gap:8px;align-items:center;">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable
            @change="loadHistory" style="width:140px;" size="small">
            <el-option label="全部状态" value="" />
            <el-option label="待审查" value="PENDING" />
            <el-option label="审查中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-button size="small" @click="loadHistory" :loading="historyLoading" icon="Refresh">刷新</el-button>
        </div>
      </div>
      <el-table :data="history" stripe max-height="420">
        <el-table-column prop="projectName" label="项目名称" min-width="140" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{row}">
            <el-tag :type="statusTagType(row.reviewStatus)" size="small" effect="dark">
              {{ statusLabel(row.reviewStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="70" align="center">
          <template #default="{row}">{{ row.sourceType === 'GIT' ? 'Git' : '上传' }}</template>
        </el-table-column>
        <el-table-column prop="language" label="语言" width="70" />
        <el-table-column label="文件/行数" width="110">
          <template #default="{row}">{{ row.files || 0 }}文件 / {{ row.lines || 0 }}行</template>
        </el-table-column>
        <el-table-column label="发现问题" width="130">
          <template #default="{row}">
            <template v-if="row.reviewStatus === 'COMPLETED'">
              <span v-if="row.critical" style="color:#F56C6C;font-weight:600;">●{{ row.critical }}</span>
              <span v-if="row.major" style="color:#E6A23C;margin-left:4px;">●{{ row.major }}</span>
              <span style="color:var(--text-placeholder);margin-left:4px;">{{ row.issues }}个</span>
            </template>
            <span v-else style="color:var(--text-placeholder);">-</span>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="160">
          <template #default="{row}">{{ formatDate(row.createDate) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small"
              @click="$router.push('/projects/' + row.projectId)">详情</el-button>
            <el-button link type="primary" size="small"
              v-if="row.reviewStatus === 'COMPLETED'"
              @click="$router.push('/projects/' + row.projectId + '/review')">结果</el-button>
            <el-button link type="danger" size="small"
              @click="handleDeleteHistory(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="history.length === 0" description="暂无上传记录" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCodeHistory, deleteCodeHistory } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const user = ref(authStore.user || {})

const history = ref([])
const historyLoading = ref(false)
const statusFilter = ref('')

const statusLabel = (s) => ({ PENDING: '待审查', IN_PROGRESS: '审查中', COMPLETED: '已完成', FAILED: '失败' }[s] || s)
const statusTagType = (s) => ({ PENDING: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success', FAILED: 'danger' }[s] || 'info')

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await getCodeHistory(user.value.id, statusFilter.value || undefined)
    history.value = res.data || []
  } catch {}
  historyLoading.value = false
}

const handleDeleteHistory = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除「${row.projectName}」的审查记录？`, '删除确认',
      { type: 'warning' })
    await deleteCodeHistory(row.projectId)
    ElMessage.success('已删除')
    loadHistory()
  } catch {}
}

const formatDate = (d) => d ? new Date(d).toLocaleString('zh-CN') : ''

onMounted(() => {
  loadHistory()
})
</script>
