<template>
  <div class="fade-in">
    <div class="page-header">
      <h2>深度统计分析</h2>
      <el-select v-model="selectedProjectId" placeholder="全部项目" clearable
        @change="onProjectChange" style="width:260px;">
        <el-option label="全部项目（汇总）" :value="null" />
        <el-option v-for="p in reviewedProjects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
    </div>

    <!-- 指标卡 -->
    <StatCards :cards="statCards" />

    <!-- 图表区 -->
    <el-row :gutter="16" style="margin-top:16px;">
      <el-col :span="8">
        <div class="chart-box"><h4>严重程度分布</h4><SeverityPie :data="severityData" /></div>
      </el-col>
      <el-col :span="8">
        <div class="chart-box"><h4>问题分类统计</h4><CategoryBar :data="categoryData" /></div>
      </el-col>
      <el-col :span="8">
        <div class="chart-box"><h4>综合评分</h4><ScoreGauge :score="scoreValue" /></div>
      </el-col>
    </el-row>

    <!-- 趋势 + 详细报表 -->
    <el-row :gutter="16" style="margin-top:16px;">
      <el-col :span="16">
        <div class="chart-box" v-if="!selectedProjectId">
          <h4>Bug率趋势（近期项目）</h4>
          <TrendLine :data="trendData" />
        </div>
        <div class="chart-box" v-else-if="report">
          <h4>审查报告摘要</h4>
          <p class="report-summary">{{ report.summary || '暂无摘要' }}</p>
          <el-descriptions :column="3" border size="small" style="margin-top:12px;">
            <el-descriptions-item label="已审查文件">{{ report.reviewedFiles || 0 }}</el-descriptions-item>
            <el-descriptions-item label="已审查行数">{{ report.reviewedLines || 0 }}</el-descriptions-item>
            <el-descriptions-item label="Bug率(‰)">{{ report.bugRate }}</el-descriptions-item>
            <el-descriptions-item label="安全">{{ report.securityCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="Bug">{{ report.bugCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="代码风格">{{ report.styleCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="性能">{{ report.performanceCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="最佳实践">{{ report.bestPracticeCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="综合评分">{{ report.score || 0 }}分</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-box">
          <h4>{{ selectedProjectId ? '文件级问题密度' : '各评分段分布' }}</h4>
          <div ref="extraChartRef" style="height:280px;"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import StatCards from '@/components/StatCards.vue'
import SeverityPie from '@/components/charts/SeverityPie.vue'
import CategoryBar from '@/components/charts/CategoryBar.vue'
import ScoreGauge from '@/components/charts/ScoreGauge.vue'
import TrendLine from '@/components/charts/TrendLine.vue'
import { useStats } from '@/composables/useStats'

const { statCards, reviewedProjects, loadGlobalStats, loadProjectStats, loadTrendData, loadProjects } = useStats()
const selectedProjectId = ref(null)
const severityData = ref({})
const categoryData = ref({})
const report = ref(null)
const trendData = ref([])
const scoreValue = computed(() => report.value?.score ?? statCards.value[3].value ?? 0)
const extraChartRef = ref(null)
let extraChart = null

const loadAll = async () => {
  const r = await loadGlobalStats()
  severityData.value = r.severity
  categoryData.value = r.category
  report.value = null
  renderScoreDistribution()
}

const loadProject = async () => {
  const r = await loadProjectStats(selectedProjectId.value)
  severityData.value = r.severity
  categoryData.value = r.category
  report.value = r.report
  renderFileHeatmap(r.issues)
}

const onProjectChange = () => selectedProjectId.value ? loadProject() : loadAll()

const renderScoreDistribution = () => {
  if (!extraChartRef.value) return
  extraChart?.dispose()
  extraChart = echarts.init(extraChartRef.value)
  extraChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: ['0-60', '60-80', '80-100'] },
    yAxis: { type: 'value' },
    series: [{
      type: 'bar', data: [1, 2, 3],
      itemStyle: { borderRadius: [6, 6, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1,
          [{ offset: 0, color: '#9254de' }, { offset: 1, color: '#c4a8f0' }])
      }
    }]
  })
}

const renderFileHeatmap = (issues) => {
  if (!extraChartRef.value) return
  extraChart?.dispose()
  extraChart = echarts.init(extraChartRef.value)
  const fileMap = {}
  issues.forEach(i => { fileMap[i.filePath] = (fileMap[i.filePath] || 0) + 1 })
  const entries = Object.entries(fileMap).sort((a, b) => b[1] - a[1]).slice(0, 10)
  extraChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: entries.map(e => e[0].split('/').pop()),
      axisLabel: { fontSize: 10 } },
    series: [{
      type: 'bar', data: entries.map(e => e[1]),
      itemStyle: { borderRadius: [0, 6, 6, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0,
          [{ offset: 0, color: '#F56C6C' }, { offset: 1, color: '#E6A23C' }])
      }
    }]
  })
}

onMounted(async () => {
  await loadProjects()
  await loadAll()
  try { trendData.value = await loadTrendData() } catch { /* ignore */ }
  nextTick(() => renderScoreDistribution())
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; font-weight: 600; }
.chart-box {
  background: #fff; border-radius: 8px; padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06); margin-bottom: 0;
}
.chart-box h4 { font-size: 14px; margin-bottom: 8px; color: #303133; }
.report-summary { font-size: 13px; color: #606266; line-height: 1.8; padding: 10px; background: #f8f9fa; border-radius: 4px; }
</style>
