<template>
  <div class="stats-page fade-in" v-loading="pageLoading">
    <!-- 顶部标题栏 -->
    <div class="page-toolbar">
      <div class="toolbar-left">
        <h2>深度统计分析</h2>
        <span class="subtitle" v-if="selectedProjectId">当前项目：{{ selectedProject?.name }}</span>
        <span class="subtitle" v-else>我的全部代码汇总</span>
      </div>
      <div class="toolbar-right">
        <el-select v-model="selectedProjectId" placeholder="全部项目" clearable
          @change="onProjectChange" size="large" style="width:240px;">
          <el-option label="📊 全部项目（汇总）" :value="null" />
          <el-option v-for="p in reviewedProjects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </div>
    </div>

    <!-- 第一行：核心指标卡 -->
    <div class="kpi-row">
      <div class="kpi-card" v-for="item in statCards" :key="item.label">
        <div class="kpi-value" :style="{color:item.color}">{{ item.value }}</div>
        <div class="kpi-label">{{ item.label }}</div>
      </div>
    </div>

    <!-- 第二行：图表区 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="12">
        <div class="panel">
          <div class="panel-header">问题严重程度分布</div>
          <div class="panel-body"><SeverityPie :data="severityData" /></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="panel">
          <div class="panel-header">问题分类统计</div>
          <div class="panel-body"><CategoryBar :data="categoryData" /></div>
        </div>
      </el-col>
    </el-row>

    <!-- 第三行：趋势图 / 报告 / 评分分布 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="16">
        <!-- 全部项目：趋势图 -->
        <div class="panel" v-if="!selectedProjectId">
          <div class="panel-header">Bug率趋势对比</div>
          <div class="panel-body"><TrendLine :data="trendData" /></div>
        </div>
        <!-- 选中项目：审查报告 -->
        <div class="panel" v-else-if="report">
          <div class="panel-header">审查报告</div>
          <div class="panel-body">
            <p class="report-text">{{ report.summary || '暂无摘要' }}</p>
            <el-descriptions :column="4" border size="small" class="report-table">
              <el-descriptions-item label="已审查文件">{{ report.reviewedFiles || 0 }}</el-descriptions-item>
              <el-descriptions-item label="已审查行数">{{ report.reviewedLines || 0 }}</el-descriptions-item>
              <el-descriptions-item label="Bug率(‰)">{{ report.bugRate }}</el-descriptions-item>
              <el-descriptions-item label="安全">{{ report.securityCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="Bug">{{ report.bugCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="代码风格">{{ report.styleCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="性能">{{ report.performanceCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="最佳实践">{{ report.bestPracticeCount || 0 }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </div>
      </el-col>
      <!-- 文件问题密度 -->
      <el-col :xs="24" :md="8">
        <div class="panel">
          <div class="panel-header">文件问题密度Top10</div>
          <div class="panel-body"><div ref="extraChartRef" class="extra-chart"></div></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import StatCards from '@/components/StatCards.vue'
import SeverityPie from '@/components/charts/SeverityPie.vue'
import CategoryBar from '@/components/charts/CategoryBar.vue'
import TrendLine from '@/components/charts/TrendLine.vue'
import { useStats } from '@/composables/useStats'
import { useTheme } from '@/composables/useTheme'

const { isDark } = useTheme()

const { statCards, reviewedProjects, loadGlobalStats, loadProjectStats, loadTrendData, loadProjects } = useStats()
const selectedProjectId = ref(null)
const selectedProject = computed(() => reviewedProjects.value.find(p => p.id === selectedProjectId.value))
const severityData = ref({})
const categoryData = ref({})
const report = ref(null)
const trendData = ref([])
const pageLoading = ref(false)
const extraChartRef = ref(null)
let extraChart = null

const handleExtraResize = () => extraChart?.resize()

const loadAll = async (skipChart = false) => {
  const r = await loadGlobalStats()
  severityData.value = r.severity
  categoryData.value = r.category
  report.value = null
  // 全部项目时显示趋势图，不需要extra chart
  if (!skipChart && extraChartRef.value) {
    renderEmptyExtraChart()
  }
  return r
}

const loadProject = async () => {
  const r = await loadProjectStats(selectedProjectId.value)
  severityData.value = r.severity
  categoryData.value = r.category
  report.value = r.report
  renderFileHeatmap(r.issues)
}

const onProjectChange = () => selectedProjectId.value ? loadProject() : loadAll()

const renderEmptyExtraChart = () => {
  if (!extraChartRef.value) return
  if (extraChartRef.value.offsetWidth === 0 || extraChartRef.value.offsetHeight === 0) {
    setTimeout(renderEmptyExtraChart, 150)
    return
  }
  extraChart?.dispose()
  extraChart = echarts.init(extraChartRef.value)
  extraChart.setOption({
    backgroundColor: 'transparent',
    title: { text: '请选择具体项目查看详情', left: 'center', top: 'center', textStyle: { fontSize: 14, color: isDark.value ? '#6e7681' : '#909399' } }
  })
}

const renderFileHeatmap = (issues) => {
  if (!extraChartRef.value) return
  if (extraChartRef.value.offsetWidth === 0 || extraChartRef.value.offsetHeight === 0) {
    setTimeout(() => renderFileHeatmap(issues), 150)
    return
  }
  extraChart?.dispose()
  extraChart = echarts.init(extraChartRef.value)
  const fileMap = {}
  issues.forEach(i => { fileMap[i.filePath] = (fileMap[i.filePath] || 0) + 1 })
  const entries = Object.entries(fileMap).sort((a, b) => b[1] - a[1]).slice(0, 10)
  extraChart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      backgroundColor: isDark.value ? 'rgba(22,27,34,0.95)' : 'rgba(255,255,255,0.95)',
      borderColor: isDark.value ? '#30363d' : '#e4e7ed',
      textStyle: { color: isDark.value ? '#c9d1d9' : '#303133' }
    },
    grid: { left: 4, right: 10, top: 10, bottom: 20, containLabel: true },
    xAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: isDark.value ? '#30363d' : '#e4e7ed' } },
      axisLabel: { color: isDark.value ? '#8b949e' : '#606266' },
      splitLine: { lineStyle: { color: isDark.value ? 'rgba(48,54,61,0.6)' : '#e4e7ed' } }
    },
    yAxis: {
      type: 'category', data: entries.map(e => e[0].split('/').pop()),
      axisLine: { lineStyle: { color: isDark.value ? '#30363d' : '#e4e7ed' } },
      axisLabel: { fontSize: 10, color: isDark.value ? '#8b949e' : '#606266' }
    },
    series: [{
      type: 'bar', data: entries.map(e => e[1]),
      itemStyle: { borderRadius: [0, 6, 6, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0,
          [{ offset: 0, color: '#F56C6C' }, { offset: 1, color: '#E6A23C' }])
      }
    }]
  })
}

/* 主题联动：切换时重新渲染 extraChart */
watch(isDark, () => {
  nextTick(() => {
    if (selectedProjectId.value && report.value) {
      // 有报告数据时重新渲染热力图（issues 数据从 report 无法获取，用空数据降级）
      renderEmptyExtraChart()
    } else {
      renderEmptyExtraChart()
    }
  })
})

onMounted(async () => {
  pageLoading.value = true
  await loadProjects()
  // 先加载"我的全部代码"汇总，后端同时返回最新已审查项目ID
  const global = await loadAll(true)
  // 默认统计当前用户最新上传且已完成审查的项目
  if (global.latestProjectId) {
    selectedProjectId.value = global.latestProjectId
    await loadProject()
  }
  try { trendData.value = await loadTrendData() } catch {}
  nextTick(() => {
    if (!selectedProjectId.value) renderEmptyExtraChart()
  })
  pageLoading.value = false
  window.addEventListener('resize', handleExtraResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleExtraResize)
  extraChart?.dispose()
  extraChart = null
})
</script>

<style scoped>
.stats-page { min-height: 100%; }

/* 顶部工具栏 */
.page-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 20px; flex-wrap: wrap; gap: 12px;
}
.toolbar-left { display: flex; align-items: baseline; gap: 12px; }
.toolbar-left h2 { font-size: 22px; font-weight: 700; color: var(--text-regular); margin: 0; }
.subtitle { font-size: 13px; color: var(--text-placeholder); }

/* KPI卡片行 */
.kpi-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 16px; }
.kpi-card {
  background: var(--bg-card);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--border-color);
  border-radius: 10px; padding: 22px 20px;
  box-shadow: var(--shadow-card); text-align: center;
  transition: transform 0.15s;
}
.kpi-card:hover { transform: translateY(-2px); }
.kpi-value { font-size: 32px; font-weight: 800; line-height: 1.2; }
.kpi-label { font-size: 13px; color: var(--text-placeholder); margin-top: 6px; }

/* 图表行 */
.chart-row { margin-bottom: 0; }

/* 统一面板 */
.panel {
  background: var(--bg-card);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: var(--shadow-card);
  margin-bottom: 16px; overflow: hidden;
}
.panel-header {
  padding: 14px 20px; font-size: 15px; font-weight: 600;
  color: var(--text-regular); border-bottom: 1px solid var(--border-light);
}
.panel-body { padding: 16px 20px; }

/* 报告 */
.report-text { font-size: 14px; color: var(--text-secondary); line-height: 1.8; margin-bottom: 16px; }
.report-table { margin-top: 8px; }

/* 额外图表 */
.extra-chart { height: 280px; }

/* 响应式 */
@media (max-width: 1200px) {
  .kpi-row { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 768px) {
  .kpi-row { grid-template-columns: 1fr; }
  .page-toolbar { flex-direction: column; align-items: flex-start; }
}
</style>
