<template>
  <div class="fade-in">
    <!-- 项目选择器 -->
    <div class="project-selector">
      <el-select v-model="selectedProjectId" placeholder="全部项目"
        clearable @change="onProjectChange" size="large" style="width:320px;">
        <el-option label="📊 全部项目（汇总概览）" :value="null" />
        <el-option-group label="已审查项目">
          <el-option v-for="p in reviewedProjects" :key="p.id" :label="p.name" :value="p.id">
            <span>{{ p.name }}</span>
            <el-tag size="small" style="margin-left:6px;" :type="scoreType(p.score)">{{ p.score ?? '-' }}分</el-tag>
          </el-option>
        </el-option-group>
      </el-select>
      <span v-if="selectedProject" class="selected-info">当前：<strong>{{ selectedProject.name }}</strong></span>
    </div>

    <!-- 核心指标卡 -->
    <StatCards :cards="statCards" />

    <!-- 快速图表 -->
    <el-row :gutter="20" style="margin-top:16px;">
      <el-col :span="12">
        <div class="chart-container">
          <h3>严重程度分布</h3>
          <SeverityPie :data="severityData" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-container">
          <h3>问题分类统计</h3>
          <CategoryBar :data="categoryData" />
        </div>
      </el-col>
    </el-row>

    <!-- 趋势图（仅全部项目） -->
    <div v-if="!selectedProjectId" style="margin-top:16px;">
      <div class="chart-container">
        <h3>Bug率与评分趋势</h3>
        <TrendLine :data="trendData" />
      </div>
    </div>

    <!-- 选中项目时的快捷入口 -->
    <div v-if="selectedProjectId" style="margin-top:16px;">
      <div class="chart-container">
        <h3>项目快捷操作</h3>
        <div style="display:flex;gap:16px;">
          <el-button type="primary" @click="$router.push('/projects/' + selectedProjectId + '/review')">
            查看审查结果
          </el-button>
          <el-button @click="$router.push('/review-center')">
            审查详情页
          </el-button>
          <el-button @click="$router.push('/stats')">
            深度统计分析
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import StatCards from '@/components/StatCards.vue'
import SeverityPie from '@/components/charts/SeverityPie.vue'
import CategoryBar from '@/components/charts/CategoryBar.vue'
import TrendLine from '@/components/charts/TrendLine.vue'
import { useStats } from '@/composables/useStats'

const { statCards, reviewedProjects, loadGlobalStats, loadProjectStats, loadTrendData, loadProjects } = useStats()
const selectedProjectId = ref(null)
const selectedProject = computed(() => reviewedProjects.value.find(p => p.id === selectedProjectId.value))
const severityData = ref({})
const categoryData = ref({})
const trendData = ref([])

const scoreType = (s) => s >= 80 ? 'success' : s >= 60 ? 'warning' : 'danger'

const loadAll = async () => {
  const result = await loadGlobalStats()
  severityData.value = result.severity
  categoryData.value = result.category
}

const loadProject = async () => {
  const result = await loadProjectStats(selectedProjectId.value)
  severityData.value = result.severity
  categoryData.value = result.category
}

const onProjectChange = async () => {
  if (selectedProjectId.value) await loadProject()
  else await loadAll()
}

onMounted(async () => {
  await loadProjects()
  await loadAll()
  try { trendData.value = await loadTrendData() } catch { /* ignore */ }
})
</script>

<style scoped>
.project-selector {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 16px; padding: 12px 16px;
  background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.selected-info { font-size: 13px; color: #606266; }
.chart-container {
  background: #fff; border-radius: 8px; padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.chart-container h3 { font-size: 15px; margin-bottom: 10px; color: #303133; }
</style>
