<template>
  <div class="fade-in" v-if="project">
    <!-- 项目头部 -->
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
      <div>
        <h2 style="font-size:22px;">{{ project.name }}</h2>
        <p style="color:#909399;margin-top:4px;">{{ project.description || '暂无描述' }}</p>
      </div>
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;">
        <el-button v-if="project.sourceType === 'UPLOAD'" type="primary"
          @click="triggerUpload">
          <el-icon><Upload /></el-icon> 上传代码
        </el-button>
        <el-button v-if="project.sourceType === 'GIT'" type="primary"
          @click="handleGitPull" :loading="pulling">
          <el-icon><Download /></el-icon> 拉取代码
        </el-button>
        <el-divider direction="vertical" />
        <el-tooltip placement="top"
          content="开启后，AI 会先分析你项目的代码风格（缩进、命名、注释、日志方式等），让修复建议和修复后代码遵循你的风格，且不把你既有的风格习惯报为问题">
          <span style="display:inline-flex;align-items:center;gap:6px;cursor:pointer;">
            <span style="font-size:14px;color:#606266;">代码风格审查</span>
            <el-switch v-model="styleEnabled" style="--el-switch-on-color:#67C23A;" />
          </span>
        </el-tooltip>
        <el-button link type="primary" size="small" @click="openStyleDialog">
          风格画像
        </el-button>
        <el-button type="success" :disabled="!canStartReview"
          @click="handleStartReview" :loading="starting">
          <el-icon><VideoPlay /></el-icon> 开始审查
        </el-button>
        <el-button @click="router.push('/projects/' + project.id + '/review')"
          v-if="project.reviewStatus === 'COMPLETED'" type="warning">
          <el-icon><View /></el-icon> 查看审查结果
        </el-button>
      </div>
      <input ref="fileInput" type="file" accept=".zip,.java,.py,.js,.ts,.go"
        style="display:none" @change="handleFileUpload" />
    </div>

    <!-- 项目信息 -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="info in infoCards" :key="info.label">
        <div class="stat-card">
          <div class="stat-value" :style="{ fontSize: '24px', color: info.color }">
            {{ info.value }}
          </div>
          <div class="stat-label">{{ info.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 文件列表 -->
    <div class="chart-container" style="margin-top:20px;">
      <h3>
        代码文件列表
        <span v-if="project.reviewStatus === 'COMPLETED'" style="font-size:12px;color:#909399;margin-left:8px;">
          点击文件名查看AI审查报告
        </span>
      </h3>
      <el-table :data="files" style="width:100%" max-height="400" stripe row-key="id">
        <el-table-column prop="fileName" label="文件名" min-width="180">
          <template #default="{ row }">
            <el-link :type="(project.reviewStatus === 'COMPLETED' || project.reviewStatus === 'FAILED') ? 'primary' : 'info'"
              :underline="(project.reviewStatus === 'COMPLETED' || project.reviewStatus === 'FAILED')"
              @click="viewFile(row)">{{ row.fileName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="filePath" label="路径" min-width="220" />
        <el-table-column prop="lineCount" label="行数" width="80" align="center" />
        <el-table-column label="审查结果" width="130" align="center">
          <template #default="{ row }">
            <template v-if="project.reviewStatus === 'COMPLETED' || project.reviewStatus === 'FAILED'">
              <span v-if="fileIssueCounts[row.id] > 0" style="color:#F56C6C;font-weight:600;">
                ⚠ {{ fileIssueCounts[row.id] }} 个问题
              </span>
              <span v-else style="color:#67C23A;font-weight:600;">✓ 通过</span>
            </template>
            <span v-else-if="project.reviewStatus === 'IN_PROGRESS'" style="color:#E6A23C;">审查中</span>
            <span v-else style="color:#909399;">待审查</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button link type="danger" size="small" icon="Delete"
              @click="handleDeleteFile(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="files.length === 0" description="暂无代码文件，请上传代码" />
    </div>

    <!-- 审查进度（审查中时显示） -->
    <div v-if="project.reviewStatus === 'IN_PROGRESS'" class="review-progress"
      style="margin-top:20px;border-radius:8px;">
      <div style="display:flex;justify-content:space-between;margin-bottom:8px;">
        <span>AI审查进行中...</span>
        <span>{{ progress.completed || 0 }} / {{ progress.total || 0 }}</span>
      </div>
      <el-progress :percentage="progress.percentage || 0" :stroke-width="12"
        :status="progress.percentage === 100 ? 'success' : ''" striped striped-flow />
    </div>
  </div>

  <!-- AI审查报告对话框 -->
  <el-dialog v-model="showCodeDialog" :title="viewingFile?.fileName" width="90%"
    top="1vh" destroy-on-close @opened="onDialogOpened">
    <!-- 审查状态栏 -->
    <div class="code-dialog-bar">
      <template v-if="!issuesLoaded && issuesLoading">
        <span style="color:#909399;">⏳ AI审查数据加载中...</span>
      </template>
      <template v-else-if="currentFileIssues.length > 0">
        <span style="color:#F56C6C;">
          ⚠ AI发现 {{ currentFileIssues.length }} 个问题
        </span>
      </template>
      <template v-else-if="issuesLoaded">
        <span style="color:#67C23A;">✓ AI审查通过，未发现问题</span>
      </template>
      <template v-else>
        <span style="color:#E6A23C;">⏳ 审查尚未完成，请等待审查结束后查看</span>
      </template>
      <span style="font-size:12px;color:#909399;margin-left:12px;">
        {{ viewingFile?.filePath }}
      </span>
    </div>
    <div class="code-dialog-body">
      <!-- 左侧：代码编辑器（行内标注） -->
      <div class="dialog-editor" v-loading="codeLoading">
        <CodeEditor v-if="viewingFile" ref="editorRef"
          :content="viewingFile.content || ''"
          :language="viewingFile.language || 'java'"
          :issues="currentFileIssues" />
      </div>
      <!-- 右侧：AI分析面板 -->
      <div class="dialog-analysis" v-loading="issuesLoading">
        <div class="analysis-header">
          AI审查分析报告
          <span v-if="currentFileIssues.length > 0" class="analysis-badge">{{ currentFileIssues.length }}</span>
        </div>
        <div class="analysis-list">
          <div v-if="issuesLoaded && currentFileIssues.length === 0" class="analysis-clean">
            <el-icon :size="36" color="#67C23A"><CircleCheck /></el-icon>
            <p>AI未在此文件中发现问题</p>
          </div>
          <div v-else-if="!issuesLoaded && !issuesLoading" class="analysis-clean">
            <el-icon :size="36" color="#E6A23C"><Clock /></el-icon>
            <p>审查尚未完成，请等待审查结束后查看</p>
          </div>
          <div v-for="issue in currentFileIssues" :key="issue.id"
            class="analysis-item"
            :class="'sev-' + issue.severity.toLowerCase()"
            @click="scrollToIssueLine(issue)">
            <!-- 位置 + 严重度 -->
            <div class="ai-line">
              <span class="ai-severity" :class="issue.severity.toLowerCase()">
                {{ severityMap[issue.severity] }}
              </span>
              <span class="ai-category">{{ categoryMap[issue.category] }}</span>
              <span class="ai-location">L{{ issue.lineStart }}{{ issue.lineEnd !== issue.lineStart ? '-' + issue.lineEnd : '' }}</span>
            </div>
            <!-- 错误标题 -->
            <div class="ai-title">{{ issue.title }}</div>
            <!-- 错误原因 -->
            <div class="ai-section" v-if="issue.description">
              <div class="ai-section-title">🔍 错误原因</div>
              <div class="ai-section-content">{{ issue.description }}</div>
            </div>
            <!-- 潜在风险 -->
            <div class="ai-section" v-if="issue.codeSnippet">
              <div class="ai-section-title">📍 问题代码</div>
              <pre class="ai-code">{{ issue.codeSnippet }}</pre>
            </div>
            <!-- 修复建议 -->
            <div class="ai-section" v-if="issue.suggestion">
              <div class="ai-section-title">💡 修复建议</div>
              <div class="ai-section-content suggest">{{ issue.suggestion }}</div>
            </div>
            <!-- 修复后代码 -->
            <div class="ai-section" v-if="issue.fixedCode">
              <div class="ai-section-title">✅ 推荐修复</div>
              <pre class="ai-code fixed">{{ issue.fixedCode }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>

  <!-- 代码风格画像对话框 -->
  <el-dialog v-model="showStyleDialog" title="代码风格画像" width="640px">
    <div v-loading="styleAnalyzing" style="min-height:120px;">
      <el-alert v-if="!styleProfile && !styleAnalyzing" type="info" :closable="false"
        show-icon style="margin-bottom:12px;"
        title="尚未生成风格画像"
        description="点击「开始分析」，系统将扫描项目代码，自动提取缩进、大括号、命名、注释、日志方式、框架使用等风格特征。开启「按我的代码风格审查」后，审查建议将遵循这些风格。" />
      <pre v-if="styleProfile" class="style-profile-text">{{ styleProfile }}</pre>
    </div>
    <template #footer>
      <el-button @click="showStyleDialog = false">关闭</el-button>
      <el-button type="primary" @click="analyzeStyle" :loading="styleAnalyzing"
        :disabled="files.length === 0">
        {{ styleProfile ? '重新分析' : '开始分析' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProjectDetail, getProjectFiles, getFileContent, getFileIssues,
  getProjectIssues,
  uploadProjectCode,
  pullFromGit, startReview, getReviewProgress, deleteProjectFile,
  analyzeProjectStyle, getProjectStyle } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import CodeEditor from '@/components/CodeEditor.vue'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => route.params.id)
const project = ref(null)
const files = ref([])
const pulling = ref(false)
const starting = ref(false)
const progress = ref({})
let progressTimer = null

// 代码风格偏好
const styleEnabled = ref(false)
const styleProfile = ref('')
const showStyleDialog = ref(false)
const styleAnalyzing = ref(false)

const infoCards = computed(() => [
  { label: '代码文件', value: project.value?.totalFiles || 0, color: '#409EFF' },
  { label: '总行数', value: project.value?.totalLines || 0, color: '#67C23A' },
  { label: '代码来源', value: project.value?.sourceType === 'GIT' ? 'Git仓库' : '上传', color: '#E6A23C' },
  { label: '审查状态', value: statusMap[project.value?.reviewStatus] || '未知', color: '#909399' }
])

const statusMap = { PENDING: '待审查', IN_PROGRESS: '审查中', COMPLETED: '已完成', FAILED: '失败' }

const canStartReview = computed(() => {
  return project.value && files.value.length > 0 &&
    ['PENDING', 'FAILED'].includes(project.value.reviewStatus)
})

const loadProject = async () => {
  try {
    const res = await getProjectDetail(projectId.value)
    project.value = res.data
  } catch { /* ignore */ }
}

const loadFiles = async () => {
  try {
    const res = await getProjectFiles(projectId.value)
    files.value = res.data || []
  } catch { /* ignore */ }
}

// 上传代码
const fileInput = ref(null)
const triggerUpload = () => fileInput.value?.click()

const handleFileUpload = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    await uploadProjectCode(projectId.value, file)
    ElMessage.success('代码上传成功')
    loadProject()
    loadFiles()
  } catch { /* ignore */ }
  fileInput.value.value = ''
}

// Git拉取
const handleGitPull = async () => {
  pulling.value = true
  try {
    await pullFromGit(projectId.value)
    ElMessage.success('代码拉取成功')
    loadProject()
    loadFiles()
  } catch { /* ignore */ }
  pulling.value = false
}

// 开始审查
const handleStartReview = async () => {
  starting.value = true
  try {
    await startReview(projectId.value, { styleEnabled: styleEnabled.value })
    ElMessage.success(styleEnabled.value ? '审查任务已启动（按你的代码风格审查）' : '审查任务已启动')
    project.value.reviewStatus = 'IN_PROGRESS'
    startProgressPolling()
  } catch { /* ignore */ }
  starting.value = false
}

// 代码风格画像
const openStyleDialog = async () => {
  showStyleDialog.value = true
  if (!styleProfile.value) {
    try {
      const res = await getProjectStyle(projectId.value)
      styleProfile.value = res.data?.styleProfile || ''
    } catch { /* ignore */ }
  }
}

const analyzeStyle = async () => {
  styleAnalyzing.value = true
  try {
    const res = await analyzeProjectStyle(projectId.value)
    styleProfile.value = res.data?.styleProfile || ''
    ElMessage.success('代码风格画像已生成')
  } catch { /* ignore */ }
  styleAnalyzing.value = false
}

const startProgressPolling = () => {
  clearInterval(progressTimer)
  progressTimer = setInterval(async () => {
    try {
      const res = await getReviewProgress(projectId.value)
      progress.value = res.data || {}
      if (progress.value.percentage >= 100) {
        clearInterval(progressTimer)
        const finalStatus = await waitForCompletion()
        if (finalStatus === 'COMPLETED') {
          ElMessage.success('审查完成')
        } else if (finalStatus === 'FAILED') {
          ElMessage.warning('审查完成但报告生成失败，可查看问题详情')
        } else {
          ElMessage.warning('审查完成但状态同步超时，请刷新页面')
        }
      }
    } catch { /* ignore */ }
  }, 3000)
}

// 等待后端报告生成完成并同步状态（重试最多10次，每次间隔1秒）
const waitForCompletion = async () => {
  for (let i = 0; i < 10; i++) {
    await loadProject()
    await loadFiles()
    const status = project.value?.reviewStatus
    if (status === 'COMPLETED') {
      await loadFileIssueCounts()
      return 'COMPLETED'
    }
    if (status === 'FAILED') {
      // 报告生成失败，但问题数据可能已保存
      await loadFileIssueCounts()
      return 'FAILED'
    }
    // 后端可能还在生成报告，等待1秒后重试
    await new Promise(r => setTimeout(r, 1000))
  }
  // 超时后最后尝试一次
  await loadProject()
  await loadFiles()
  await loadFileIssueCounts()
  return project.value?.reviewStatus || 'UNKNOWN'
}

// 文件级问题计数
const fileIssueCounts = ref({})
const severityMap = { CRITICAL: '严重', MAJOR: '重要', MINOR: '次要', INFO: '提示' }
const categoryMap = { SECURITY: '安全问题', BUG: '代码Bug', CODE_STYLE: '代码风格', PERFORMANCE: '性能问题', BEST_PRACTICE: '最佳实践' }

const loadFileIssueCounts = async () => {
  if (project.value?.reviewStatus !== 'COMPLETED' && project.value?.reviewStatus !== 'FAILED') return
  try {
    const res = await getProjectIssues(projectId.value)
    const issues = res.data || []
    const counts = {}
    issues.forEach(i => {
      files.value.forEach(f => {
        if (f.filePath === i.filePath) counts[f.id] = (counts[f.id] || 0) + 1
      })
    })
    fileIssueCounts.value = counts
  } catch { /* ignore */ }
}

// 查看文件 - AI审查报告
const showCodeDialog = ref(false)
const viewingFile = ref(null)
const currentFileIssues = ref([])
const codeLoading = ref(false)
const issuesLoading = ref(false)
const editorRef = ref(null)

const issuesLoaded = ref(false)
const viewFile = async (file) => {
  codeLoading.value = true
  issuesLoading.value = true
  issuesLoaded.value = false
  currentFileIssues.value = []
  showCodeDialog.value = true

  // 先加载文件内容
  let fp = file.filePath
  try {
    const res = await getFileContent(file.id)
    viewingFile.value = res.data
    fp = res.data.filePath || file.filePath
  } catch { /* ignore */ }
  codeLoading.value = false

  // 加载文件的AI审查问题
  // 只要进度达100%或状态为COMPLETED，就尝试加载问题
  const shouldLoadIssues = project.value?.reviewStatus === 'COMPLETED'
    || project.value?.reviewStatus === 'FAILED'
      || progress.value?.percentage >= 100
  if (shouldLoadIssues) {
    try {
      console.log('Loading issues for file:', fp)
      const issueRes = await getFileIssues(projectId.value, fp)
      currentFileIssues.value = issueRes.data || []
      issuesLoaded.value = true
    } catch (e) {
      console.error('Failed to load file issues:', e)
      issuesLoaded.value = false
    }
  } else {
    // 审查未完成，标记为未加载（而不是失败）
    issuesLoaded.value = false
  }
  issuesLoading.value = false
}

const scrollToIssueLine = (issue) => {
  if (editorRef.value) editorRef.value.scrollToLine(issue.lineStart)
}

const onDialogOpened = () => {
  // dialog打开后，Monaco可能需要resize
  setTimeout(() => {
    if (editorRef.value) {
      const ed = editorRef.value.getEditor()
      if (ed) ed.layout()
    }
  }, 200)
}

const handleDeleteFile = async (file) => {
  try {
    await ElMessageBox.confirm(
      `确认删除文件「${file.fileName}」？`,
      '删除确认',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteProjectFile(projectId.value, file.id)
    ElMessage.success('文件已删除')
    loadFiles()
    loadProject()
  } catch { /* ignore */ }
}

onMounted(async () => {
  await loadProject()
  styleEnabled.value = project.value?.styleEnabled === 1
  await loadFiles()
  await loadFileIssueCounts()

  // 如果状态为审查中，自动启动进度轮询（处理页面刷新后状态恢复）
  if (project.value?.reviewStatus === 'IN_PROGRESS' && files.value.length > 0) {
    startProgressPolling()
  }
})

onUnmounted(() => {
  clearInterval(progressTimer)
})
</script>

<style scoped>
.code-dialog-bar {
  padding: 8px 16px; background: #f8f9fa; border-radius: 4px;
  margin-bottom: 12px; font-size: 14px; font-weight: 500;
}
.code-dialog-body { display: flex; height: 75vh; gap: 12px; }
.dialog-editor { flex: 1; border: 1px solid #e4e7ed; border-radius: 4px; overflow: hidden; min-width: 0; }

.dialog-analysis { width: 380px; flex-shrink: 0; border: 1px solid #e4e7ed; border-radius: 4px; display: flex; flex-direction: column; overflow: hidden; background: #fff; }
.analysis-header {
  padding: 12px 16px; font-size: 15px; font-weight: 600;
  border-bottom: 1px solid #f0f0f0; flex-shrink: 0;
  display: flex; align-items: center; gap: 8px;
}
.analysis-badge {
  background: #F56C6C; color: #fff; font-size: 12px; padding: 2px 8px; border-radius: 10px;
}
.analysis-list { flex: 1; overflow-y: auto; padding: 10px; }
.analysis-clean {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; height: 100%; color: #67C23A; gap: 8px;
}
.analysis-item {
  padding: 12px; margin-bottom: 10px; border-radius: 8px;
  border-left: 4px solid #e4e7ed; cursor: pointer; transition: all 0.15s;
}
.analysis-item:hover { background: #fafafa; }
.analysis-item.sev-critical { border-left-color: #F56C6C; background: #fef0f0; }
.analysis-item.sev-major { border-left-color: #E6A23C; background: #fdf6ec; }
.analysis-item.sev-minor { border-left-color: #409EFF; background: #ecf5ff; }
.analysis-item.sev-info { border-left-color: #909399; background: #f4f4f5; }

.ai-line { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.ai-severity { padding: 2px 8px; border-radius: 4px; font-size: 11px; color: #fff; font-weight: 600; }
.ai-severity.critical { background: #F56C6C; }
.ai-severity.major { background: #E6A23C; }
.ai-severity.minor { background: #409EFF; }
.ai-severity.info { background: #909399; }
.ai-category { padding: 2px 6px; border-radius: 3px; font-size: 11px; background: #e8e8e8; color: #606266; }
.ai-location { font-size: 11px; color: #909399; margin-left: auto; }

.ai-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }

.ai-section { margin-top: 8px; }
.ai-section-title { font-size: 12px; font-weight: 600; color: #909399; margin-bottom: 4px; }
.ai-section-content { font-size: 12px; color: #606266; line-height: 1.6; }
.ai-section-content.suggest { color: #67C23A; }
.ai-code { background: #282c34; color: #abb2bf; padding: 8px 12px; border-radius: 4px; font-size: 12px; line-height: 1.5; overflow-x: auto; max-height: 100px; margin: 4px 0; }
.ai-code.fixed { background: #f0f9eb; color: #67C23A; }

.style-profile-text {
  background: #f8f9fa; border: 1px solid #e4e7ed; border-radius: 6px;
  padding: 14px 16px; font-size: 13px; line-height: 1.8; color: #303133;
  white-space: pre-wrap; word-break: break-word; margin: 0;
  font-family: "SF Mono", "Menlo", "Consolas", monospace;
}
</style>
