<template>
  <div class="review-center fade-in">
    <!-- 级联选择器区域 -->
    <div class="selector-bar">
      <div class="selector-group">
        <span class="selector-label">选择项目</span>
        <el-select v-model="selectedProjectId" placeholder="请选择项目" filterable
          @change="onProjectChange" :loading="projectsLoading" style="width:280px;">
          <el-option v-for="p in projects" :key="p.id"
            :label="p.name + (p.reviewStatus === 'COMPLETED' ? ' ✓' : '')"
            :value="p.id">
            <span>{{ p.name }}</span>
            <el-tag v-if="p.reviewStatus === 'COMPLETED'" type="success" size="small" style="margin-left:8px;">已审查</el-tag>
            <el-tag v-else-if="p.reviewStatus === 'IN_PROGRESS'" type="warning" size="small" style="margin-left:8px;">审查中</el-tag>
            <el-tag v-else type="info" size="small" style="margin-left:8px;">未审查</el-tag>
          </el-option>
        </el-select>
      </div>
      <el-icon :size="20" color="#909399"><ArrowRight /></el-icon>
      <div class="selector-group">
        <span class="selector-label">选择文件</span>
        <el-select v-model="selectedFileId" placeholder="请先选择项目" filterable
          :disabled="!selectedProjectId" :loading="filesLoading"
          @change="onFileChange" style="width:360px;">
          <el-option v-for="f in files" :key="f.id"
            :label="f.filePath" :value="f.id" />
        </el-select>
      </div>
      <div class="selector-info" v-if="selectedProjectId && selectedFileId">
        <el-tag v-if="currentIssueCount > 0" type="warning" effect="dark">
          {{ currentIssueCount }} 个问题
        </el-tag>
        <el-tag v-else type="success" effect="dark">无问题</el-tag>
        <span style="font-size:12px;color:#909399;margin-left:8px;">
          {{ currentLanguage }} · {{ currentLineCount }} 行
        </span>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="review-main" v-if="selectedProjectId && selectedFileId">
      <div class="review-editor-panel" v-loading="codeLoading">
        <CodeEditor v-if="currentFileContent" ref="editorRef"
          :content="currentFileContent"
          :language="currentLanguage"
          :issues="currentIssues" />
        <div v-else class="empty-placeholder">
          <el-icon :size="40"><Document /></el-icon>
          <p>选择文件后在此查看审查结果</p>
        </div>
      </div>
      <div class="review-issues-panel">
        <div class="issues-panel-header">
          <span>审查问题列表</span>
          <el-radio-group v-model="severityFilter" size="small">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="CRITICAL">
              <span class="dot dot-critical"></span>严重
            </el-radio-button>
            <el-radio-button value="MAJOR">
              <span class="dot dot-major"></span>重要
            </el-radio-button>
            <el-radio-button value="MINOR">
              <span class="dot dot-minor"></span>次要
            </el-radio-button>
          </el-radio-group>
        </div>
        <div class="issues-panel-body" v-loading="issuesLoading">
          <div v-if="filteredIssues.length === 0" class="no-issues">
            <el-empty :image-size="48" :description="currentIssues.length === 0 ? '该文件暂无审查问题' : '无匹配问题'" />
          </div>
          <div v-for="issue in filteredIssues" :key="issue.id"
            class="issue-row"
            :class="'severity-' + issue.severity.toLowerCase()"
            @click="scrollToLine(issue)">
            <div class="issue-row-top">
              <span class="sev-tag" :class="issue.severity.toLowerCase()">
                {{ severityMap[issue.severity] }}
              </span>
              <span class="cat-tag">{{ categoryMap[issue.category] || issue.category }}</span>
              <span class="line-info">L{{ issue.lineStart }}{{ issue.lineEnd !== issue.lineStart ? '-' + issue.lineEnd : '' }}</span>
            </div>
            <div class="issue-row-title">{{ issue.title }}</div>
            <div class="issue-row-desc" v-if="issue.description">{{ issue.description }}</div>
            <div class="issue-row-suggestion" v-if="issue.suggestion">
              <span class="sug-label">修复建议：</span>{{ issue.suggestion }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div class="empty-main" v-else>
      <el-result icon="info" title="请选择项目和文件" sub-title="在上方选择器中选择一个已完成审查的项目和文件，即可查看代码审查结果">
        <template #extra>
          <el-button type="primary" @click="loadProjects">刷新项目列表</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getProjectList, getProjectFiles, getFileContent, getFileIssues } from '@/api'
import CodeEditor from '@/components/CodeEditor.vue'

const projects = ref([])
const files = ref([])
const projectsLoading = ref(false)
const filesLoading = ref(false)
const codeLoading = ref(false)
const issuesLoading = ref(false)

const selectedProjectId = ref(null)
const selectedFileId = ref(null)
const currentFileContent = ref('')
const currentLanguage = ref('java')
const currentLineCount = ref(0)
const currentIssues = ref([])
const currentIssueCount = computed(() => currentIssues.value.length)
const severityFilter = ref('')
const editorRef = ref(null)

const severityMap = { CRITICAL: '严重', MAJOR: '重要', MINOR: '次要', INFO: '提示' }
const categoryMap = { SECURITY: '安全', BUG: 'Bug', CODE_STYLE: '代码风格', PERFORMANCE: '性能', BEST_PRACTICE: '最佳实践' }

const filteredIssues = computed(() => {
  if (!severityFilter.value) return currentIssues.value
  return currentIssues.value.filter(i => i.severity === severityFilter.value)
})

const loadProjects = async () => {
  projectsLoading.value = true
  try {
    const res = await getProjectList(1, 100)
    projects.value = (res.data?.records || []).filter(p => p.reviewStatus === 'COMPLETED')
  } catch { /* ignore */ }
  projectsLoading.value = false
}

const onProjectChange = async (projectId) => {
  if (!projectId) return
  selectedFileId.value = null
  currentFileContent.value = ''
  currentIssues.value = []
  filesLoading.value = true
  try {
    const res = await getProjectFiles(projectId)
    files.value = res.data || []
  } catch { /* ignore */ }
  filesLoading.value = false
}

const onFileChange = async (fileId) => {
  if (!fileId) return
  codeLoading.value = true
  issuesLoading.value = true
  try {
    const fileRes = await getFileContent(fileId)
    currentFileContent.value = fileRes.data?.content || ''
    currentLanguage.value = fileRes.data?.language || 'java'
    currentLineCount.value = (currentFileContent.value.match(/\n/g) || []).length + 1

    if (fileRes.data?.filePath) {
      const issueRes = await getFileIssues(selectedProjectId.value, fileRes.data.filePath)
      currentIssues.value = issueRes.data || []
    }
  } catch { /* ignore */ }
  codeLoading.value = false
  issuesLoading.value = false
}

const scrollToLine = (issue) => {
  if (editorRef.value) editorRef.value.scrollToLine(issue.lineStart)
}

onMounted(() => loadProjects())
</script>

<style scoped>
.review-center { height: 100%; display: flex; flex-direction: column; overflow: hidden; }

.selector-bar {
  display: flex; align-items: center; gap: 12px; padding: 16px 20px;
  background: #fff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  margin-bottom: 12px; flex-shrink: 0; flex-wrap: wrap;
}
.selector-group { display: flex; flex-direction: column; gap: 4px; }
.selector-label { font-size: 12px; color: #909399; font-weight: 500; }
.selector-info { display: flex; align-items: center; margin-left: auto; }

.review-main { flex: 1; display: flex; gap: 12px; overflow: hidden; min-height: 0; }

.review-editor-panel {
  flex: 1; border: 1px solid #e4e7ed; border-radius: 6px;
  overflow: hidden; background: #fff; min-width: 0;
}
.empty-placeholder {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; height: 100%; color: #909399;
}

.review-issues-panel {
  width: 380px; flex-shrink: 0; display: flex; flex-direction: column;
  background: #fff; border: 1px solid #e4e7ed; border-radius: 6px;
  overflow: hidden;
}
.issues-panel-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px; border-bottom: 1px solid #f0f0f0;
  font-size: 14px; font-weight: 600; flex-shrink: 0;
}
.issues-panel-body { flex: 1; overflow-y: auto; padding: 8px; }
.no-issues { display: flex; align-items: center; justify-content: center; height: 100%; }

.issue-row {
  padding: 10px 12px; margin-bottom: 6px; border-radius: 6px;
  border-left: 4px solid #e4e7ed; cursor: pointer; transition: all 0.15s;
}
.issue-row:hover { background: #f8f9fa; }
.issue-row.severity-critical { border-left-color: #F56C6C; }
.issue-row.severity-major { border-left-color: #E6A23C; }
.issue-row.severity-minor { border-left-color: #409EFF; }
.issue-row.severity-info { border-left-color: #909399; }

.issue-row-top { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; flex-wrap: wrap; }
.sev-tag { padding: 1px 6px; border-radius: 3px; font-size: 11px; color: #fff; font-weight: 600; }
.sev-tag.critical { background: #F56C6C; }
.sev-tag.major { background: #E6A23C; }
.sev-tag.minor { background: #409EFF; }
.sev-tag.info { background: #909399; }
.cat-tag { padding: 1px 5px; border-radius: 3px; font-size: 11px; background: #f0f2f5; color: #606266; }
.line-info { font-size: 12px; color: #909399; margin-left: auto; }

.issue-row-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 2px; }
.issue-row-desc { font-size: 12px; color: #606266; line-height: 1.4; margin-bottom: 4px; }
.issue-row-suggestion { font-size: 12px; color: #67C23A; }
.sug-label { font-weight: 600; }

.dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; }
.dot-critical { background: #F56C6C; }
.dot-major { background: #E6A23C; }
.dot-minor { background: #409EFF; }

.empty-main { flex: 1; display: flex; align-items: center; justify-content: center; }
</style>
