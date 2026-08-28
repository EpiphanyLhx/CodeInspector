<template>
  <div class="review-page fade-in">
    <!-- 顶部统计概览 -->
    <div class="review-summary-bar" v-loading="loading">
      <div class="summary-item">
        <div class="summary-value" :style="{color:'#303133'}">{{ project?.name || '-' }}</div>
        <div class="summary-label">项目名称</div>
      </div>
      <div class="summary-item">
        <div class="summary-value" style="color:#303133">{{ allIssues.length }}</div>
        <div class="summary-label">问题总数</div>
      </div>
      <div class="summary-item">
        <div class="summary-value" style="color:#F56C6C">{{ criticalCount }}</div>
        <div class="summary-label">严重</div>
      </div>
      <div class="summary-item">
        <div class="summary-value" style="color:#E6A23C">{{ majorCount }}</div>
        <div class="summary-label">重要</div>
      </div>
      <div class="summary-item">
        <div class="summary-value" style="color:#409EFF">{{ minorCount }}</div>
        <div class="summary-label">次要</div>
      </div>
      <div class="summary-item">
        <div class="summary-value" style="color:#67C23A">{{ report?.bugRate ?? '-' }}</div>
        <div class="summary-label">Bug率(‰)</div>
      </div>
    </div>

    <!-- Tab切换 -->
    <el-tabs v-model="activeTab" class="review-tabs">
      <!-- Tab 1: AI审查结果（问题列表+代码查看） -->
      <el-tab-pane label="AI审查结果" name="issues">
        <div class="review-split-layout">
          <!-- 左侧：文件选择 + Monaco Editor -->
          <div class="review-left">
            <div class="file-selector-bar">
              <el-select v-model="selectedFileId" placeholder="选择文件查看" filterable
                @change="onFileChange" style="flex:1;">
                <el-option v-for="file in files" :key="file.id"
                  :label="file.filePath" :value="file.id" />
              </el-select>
              <el-tag v-if="currentFileIssues.length > 0" type="warning" effect="plain">
                当前文件 {{ currentFileIssues.length }} 个问题
              </el-tag>
            </div>
            <div class="code-editor-area" v-loading="codeLoading">
              <CodeEditor v-if="currentFileContent" ref="editorRef"
                :content="currentFileContent"
                :language="currentLanguage"
                :issues="currentFileIssues" />
              <div v-else class="empty-editor">
                <el-icon :size="48"><Document /></el-icon>
                <p>请选择一个文件查看审查结果</p>
              </div>
            </div>
          </div>

          <!-- 右侧：问题列表面板 -->
          <div class="review-right">
            <!-- 筛选器 -->
            <div class="issue-filters">
              <el-radio-group v-model="filterMode" size="small" @change="onFilterChange">
                <el-radio-button value="current">当前文件</el-radio-button>
                <el-radio-button value="all">全部问题</el-radio-button>
              </el-radio-group>
              <div class="severity-filters">
                <el-checkbox-button v-for="s in severities" :key="s"
                  :model-value="activeSeverities.includes(s)"
                  @change="(v) => toggleSeverity(s, v)"
                  size="small">
                  <span :class="'dot-' + s.toLowerCase()" style="display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:4px;"></span>
                  {{ severityLabels[s] }}
                </el-checkbox-button>
              </div>
            </div>

            <!-- 问题列表 -->
            <div class="issue-scroll-list" v-loading="issuesLoading">
              <div v-if="displayIssues.length === 0" class="empty-state">
                <el-empty :image-size="60" description="暂无匹配问题" />
              </div>
              <div v-for="issue in displayIssues" :key="issue.id"
                class="issue-card fade-in"
                :class="'border-' + issue.severity.toLowerCase()"
                @click="scrollToLine(issue)">
                <!-- 问题头部 -->
                <div class="issue-card-header">
                  <span class="severity-badge" :class="issue.severity.toLowerCase()">
                    {{ severityLabels[issue.severity] }}
                  </span>
                  <span class="category-badge">{{ categoryLabels[issue.category] }}</span>
                  <span class="issue-location">
                    <el-icon :size="12"><Location /></el-icon>
                    L{{ issue.lineStart }}{{ issue.lineEnd !== issue.lineStart ? '-' + issue.lineEnd : '' }}
                  </span>
                </div>
                <!-- 标题 -->
                <div class="issue-card-title">{{ issue.title }}</div>
                <!-- 文件路径 -->
                <div class="issue-card-path">{{ issue.filePath }}</div>
                <!-- 描述 -->
                <div class="issue-card-desc" v-if="issue.description">{{ issue.description }}</div>
                <!-- 问题代码 -->
                <div class="issue-card-code" v-if="issue.codeSnippet">
                  <pre>{{ issue.codeSnippet }}</pre>
                </div>
                <!-- 修复建议 -->
                <div class="issue-card-suggestion" v-if="issue.suggestion">
                  <div class="label">💡 修复建议</div>
                  <div class="content">{{ issue.suggestion }}</div>
                </div>
                <!-- 修复后代码 -->
                <div class="issue-card-fixed" v-if="issue.fixedCode">
                  <div class="label">✅ 修复后代码</div>
                  <pre>{{ issue.fixedCode }}</pre>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: 审查报告 -->
      <el-tab-pane label="审查报告" name="report" v-if="report">
        <div class="report-panel" v-loading="reportLoading">
          <!-- AI审查摘要 -->
          <div class="report-section">
            <h3>📋 审查摘要</h3>
            <p class="report-summary-text">{{ report.summary || '暂无摘要' }}</p>
          </div>

          <!-- 问题统计表 -->
          <div class="report-section">
            <h3>📊 问题统计</h3>
            <el-row :gutter="16">
              <el-col :span="6" v-for="stat in reportStats" :key="stat.label">
                <div class="report-stat-card">
                  <div class="stat-num" :style="{color:stat.color}">{{ stat.value }}</div>
                  <div class="stat-text">{{ stat.label }}</div>
                </div>
              </el-col>
            </el-row>
          </div>

          <!-- 图表 -->
          <el-row :gutter="16" style="margin-top:12px;">
            <el-col :span="12">
              <div class="report-chart">
                <h4>严重程度分布</h4>
                <div ref="severityPieRef" style="height:260px;"></div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="report-chart">
                <h4>分类分布</h4>
                <div ref="categoryBarRef" style="height:260px;"></div>
              </div>
            </el-col>
          </el-row>

        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { getProjectDetail, getProjectFiles, getFileContent,
  getProjectIssues, getFileIssues, getReviewReport, getReviewProgress } from '@/api'
import CodeEditor from '@/components/CodeEditor.vue'

const route = useRoute()
const projectId = computed(() => route.params.id)

// 状态
const loading = ref(true)
const codeLoading = ref(false)
const issuesLoading = ref(false)
const reportLoading = ref(false)

const activeTab = ref('issues')
const project = ref({})
const files = ref([])
const report = ref(null)
const allIssues = ref([])
const currentFileIssues = ref([])
const selectedFileId = ref(null)
const currentFileContent = ref('')
const currentLanguage = ref('java')
const editorRef = ref(null)
const filterMode = ref('current')
const activeSeverities = ref(['CRITICAL', 'MAJOR', 'MINOR', 'INFO'])

const severities = ['CRITICAL', 'MAJOR', 'MINOR', 'INFO']
const severityLabels = { CRITICAL: '严重', MAJOR: '重要', MINOR: '次要', INFO: '提示' }
const categoryLabels = { SECURITY: '安全', BUG: 'Bug', CODE_STYLE: '代码风格', PERFORMANCE: '性能', BEST_PRACTICE: '最佳实践' }

const criticalCount = computed(() => allIssues.value.filter(i => i.severity === 'CRITICAL').length)
const majorCount = computed(() => allIssues.value.filter(i => i.severity === 'MAJOR').length)
const minorCount = computed(() => allIssues.value.filter(i => i.severity === 'MINOR').length)

const displayIssues = computed(() => {
  let list = filterMode.value === 'current' ? currentFileIssues.value : allIssues.value
  return list.filter(i => activeSeverities.value.includes(i.severity))
})

const reportStats = computed(() => {
  const r = report.value
  if (!r) return []
  return [
    { label: '安全', value: r.securityCount || 0, color: '#F56C6C' },
    { label: 'Bug', value: r.bugCount || 0, color: '#E6A23C' },
    { label: '代码风格', value: r.styleCount || 0, color: '#409EFF' },
    { label: '性能', value: r.performanceCount || 0, color: '#67C23A' },
    { label: '最佳实践', value: r.bestPracticeCount || 0, color: '#9254de' },
    { label: '已审查文件', value: r.reviewedFiles || 0, color: '#909399' },
    { label: '已审查行数', value: r.reviewedLines || 0, color: '#909399' }
  ]
})

// 加载数据
const loadAll = async () => {
  loading.value = true
  try {
    const [projRes, fileRes, issueRes, reportRes] = await Promise.all([
      getProjectDetail(projectId.value),
      getProjectFiles(projectId.value),
      getProjectIssues(projectId.value),
      getReviewReport(projectId.value)
    ])
    project.value = projRes.data
    files.value = fileRes.data || []
    allIssues.value = issueRes.data || []
    report.value = reportRes.data
  } catch { /* ignore */ }
  loading.value = false
}

const onFileChange = async (fileId) => {
  if (!fileId) { currentFileContent.value = ''; currentFileIssues.value = []; return }
  codeLoading.value = true
  try {
    // 先获取文件内容（需要filePath才能查询问题）
    const fileRes = await getFileContent(fileId)
    currentFileContent.value = fileRes.data?.content || ''
    currentLanguage.value = fileRes.data?.language || 'java'

    // 获取文件问题
    if (fileRes.data?.filePath) {
      const issueRes = await getFileIssues(projectId.value, fileRes.data.filePath)
      currentFileIssues.value = issueRes.data || []
    }
  } catch { /* ignore */ }
  codeLoading.value = false
}

const onFilterChange = () => { /* computed recalculates */ }
const toggleSeverity = (s, checked) => {
  if (checked) activeSeverities.value.push(s)
  else activeSeverities.value = activeSeverities.value.filter(v => v !== s)
}

const scrollToLine = (issue) => {
  if (editorRef.value) editorRef.value.scrollToLine(issue.lineStart)
}

// ECharts
let severityPieChart, categoryBarChart
const severityPieRef = ref(null)
const categoryBarRef = ref(null)

watch(activeTab, async (tab) => {
  if (tab === 'report') {
    await nextTick()
    // 延迟确保 Element Plus 标签页过渡动画完成，容器有有效尺寸
    setTimeout(() => renderCharts(), 150)
  }
})

const renderCharts = () => {
  if (allIssues.value.length === 0) return
  renderSeverityPie()
  renderCategoryBar()
}

// 统一 resize 处理
const handleAllResize = () => {
  severityPieChart?.resize()
  categoryBarChart?.resize()
}

const renderSeverityPie = () => {
  if (!severityPieRef.value) return
  const el = severityPieRef.value
  if (el.offsetWidth === 0 || el.offsetHeight === 0) {
    setTimeout(renderSeverityPie, 150)
    return
  }
  severityPieChart?.dispose()
  severityPieChart = echarts.init(el)
  const labels = { CRITICAL: '严重', MAJOR: '重要', MINOR: '次要', INFO: '提示' }
  const colors = { CRITICAL: '#F56C6C', MAJOR: '#E6A23C', MINOR: '#409EFF', INFO: '#909399' }
  const counts = { CRITICAL: criticalCount.value, MAJOR: majorCount.value, MINOR: minorCount.value,
    INFO: allIssues.value.filter(i => i.severity === 'INFO').length }
  severityPieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', right: 0, top: 'center' },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['38%', '50%'],
      itemStyle: { borderRadius: 4 },
      data: Object.entries(counts).map(([k, v]) => ({
        name: labels[k], value: v, itemStyle: { color: colors[k] }
      }))
    }]
  })
}

const renderCategoryBar = () => {
  if (!categoryBarRef.value) return
  const el = categoryBarRef.value
  if (el.offsetWidth === 0 || el.offsetHeight === 0) {
    setTimeout(renderCategoryBar, 150)
    return
  }
  categoryBarChart?.dispose()
  categoryBarChart = echarts.init(el)
  const cats = { SECURITY: '安全', BUG: 'Bug', CODE_STYLE: '代码风格', PERFORMANCE: '性能', BEST_PRACTICE: '最佳实践' }
  const counts = { SECURITY: 0, BUG: 0, CODE_STYLE: 0, PERFORMANCE: 0, BEST_PRACTICE: 0 }
  allIssues.value.forEach(i => { if (counts[i.category] !== undefined) counts[i.category]++ })
  categoryBarChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: Object.values(cats) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar', data: Object.values(counts),
      itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1,
          [{ offset: 0, color: '#409EFF' }, { offset: 1, color: '#79bbff' }])
      }
    }]
  })
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', handleAllResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleAllResize)
  severityPieChart?.dispose()
  categoryBarChart?.dispose()
})
</script>

<style scoped>
.review-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; }

/* 顶部统计条 */
.review-summary-bar {
  display: flex; gap: 0; background: #fff; border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06); padding: 16px 0; margin-bottom: 12px; flex-shrink: 0;
}
.summary-item {
  flex: 1; text-align: center; border-right: 1px solid #f0f0f0;
  padding: 0 12px;
}
.summary-item:last-child { border-right: none; }
.summary-value { font-size: 24px; font-weight: 700; line-height: 1.2; }
.summary-label { font-size: 12px; color: #909399; margin-top: 2px; }

/* Tab */
.review-tabs { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.review-tabs :deep(.el-tabs__content) { flex: 1; overflow: hidden; }
.review-tabs :deep(.el-tab-pane) { height: 100%; overflow-y: auto; }

/* 双栏布局 */
.review-split-layout { display: flex; height: 100%; gap: 12px; }
.review-left { flex: 1; display: flex; flex-direction: column; overflow: hidden; min-width: 0; }
.review-right { width: 400px; flex-shrink: 0; display: flex; flex-direction: column; overflow: hidden; }

.file-selector-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-shrink: 0; }
.code-editor-area { flex: 1; border: 1px solid #e4e7ed; border-radius: 4px; overflow: hidden; background: #fff; }
.empty-editor { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #909399; }

/* 问题筛选 */
.issue-filters { padding: 12px; background: #fff; border-radius: 6px 6px 0 0; border: 1px solid #e4e7ed; border-bottom: none; flex-shrink: 0; }
.severity-filters { margin-top: 8px; display: flex; gap: 4px; }
.dot-critical { background: #F56C6C; }
.dot-major { background: #E6A23C; }
.dot-minor { background: #409EFF; }
.dot-info { background: #909399; }

.issue-scroll-list { flex: 1; overflow-y: auto; background: #fff; border: 1px solid #e4e7ed; border-top: none; border-radius: 0 0 6px 6px; padding: 8px; }
.empty-state { display: flex; align-items: center; justify-content: center; height: 100%; }

/* 问题卡片 */
.issue-card { padding: 12px; margin-bottom: 8px; border-radius: 6px; border-left: 4px solid #e4e7ed; cursor: pointer; transition: all 0.2s; }
.issue-card:hover { background: #f8f9fa; transform: translateX(2px); }
.issue-card.border-critical { border-left-color: #F56C6C; }
.issue-card.border-major { border-left-color: #E6A23C; }
.issue-card.border-minor { border-left-color: #409EFF; }
.issue-card.border-info { border-left-color: #909399; }

.issue-card-header { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; flex-wrap: wrap; }
.severity-badge { padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; color: #fff; }
.severity-badge.critical { background: #F56C6C; }
.severity-badge.major { background: #E6A23C; }
.severity-badge.minor { background: #409EFF; }
.severity-badge.info { background: #909399; }
.category-badge { padding: 2px 6px; border-radius: 3px; font-size: 11px; background: #f0f2f5; color: #606266; }
.issue-location { font-size: 12px; color: #909399; display: flex; align-items: center; gap: 2px; margin-left: auto; }

.issue-card-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 4px; }
.issue-card-path { font-size: 12px; color: #c0c4cc; margin-bottom: 6px; }
.issue-card-desc { font-size: 13px; color: #606266; line-height: 1.5; margin-bottom: 6px; }

.issue-card-code { margin-bottom: 6px; }
.issue-card-code pre { background: #f5f7fa; padding: 6px 10px; border-radius: 4px; font-size: 12px; line-height: 1.5; overflow-x: auto; max-height: 80px; }

.issue-card-suggestion { margin-bottom: 6px; }
.issue-card-suggestion .label { font-size: 12px; font-weight: 600; color: #67C23A; margin-bottom: 2px; }
.issue-card-suggestion .content { font-size: 12px; color: #606266; line-height: 1.5; }

.issue-card-fixed pre { background: #f0f9eb; padding: 6px 10px; border-radius: 4px; font-size: 12px; line-height: 1.5; overflow-x: auto; max-height: 80px; color: #67C23A; }

/* 报告面板 */
.report-panel { padding: 16px 0; }
.report-section { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); }
.report-section h3 { font-size: 16px; margin-bottom: 12px; color: #303133; }
.report-summary-text { font-size: 14px; color: #606266; line-height: 1.8; padding: 12px; background: #f8f9fa; border-radius: 6px; }

.report-stat-card { text-align: center; padding: 16px 8px; background: #f8f9fa; border-radius: 6px; margin-bottom: 8px; }
.report-stat-card .stat-num { font-size: 24px; font-weight: 700; }
.report-stat-card .stat-text { font-size: 12px; color: #909399; margin-top: 4px; }

.report-chart { background: #fff; border-radius: 6px; padding: 12px; margin-bottom: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.04); }
.report-chart h4 { font-size: 14px; margin-bottom: 8px; }
</style>
