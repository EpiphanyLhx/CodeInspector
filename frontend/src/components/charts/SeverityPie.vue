<template>
  <div ref="chartRef" style="height:260px;min-height:260px;width:100%;"></div>
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useTheme } from '@/composables/useTheme'

const props = defineProps({ data: { type: Object, default: () => ({}) } })
const { isDark } = useTheme()
const chartRef = ref(null)
let chart = null

const labels = { CRITICAL: '严重', MAJOR: '重要', MINOR: '次要', INFO: '提示' }
const colors = { CRITICAL: '#F56C6C', MAJOR: '#E6A23C', MINOR: '#409EFF', INFO: '#909399' }

const handleResize = () => chart?.resize()

const render = () => {
  if (!chartRef.value) return
  // 检查容器是否有有效尺寸，避免在隐藏/零尺寸容器上初始化
  if (chartRef.value.offsetWidth === 0 || chartRef.value.offsetHeight === 0) {
    // 容器不可见，延迟重试
    setTimeout(render, 150)
    return
  }
  chart?.dispose()
  chart = echarts.init(chartRef.value)
  const items = Object.entries(props.data).map(([k, v]) => ({
    name: labels[k] || k, value: v, itemStyle: { color: colors[k] || '#909399' }
  }))
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: isDark.value ? 'rgba(22,27,34,0.95)' : 'rgba(255,255,255,0.95)',
      borderColor: isDark.value ? '#30363d' : '#e4e7ed',
      textStyle: { color: isDark.value ? '#c9d1d9' : '#303133' }
    },
    legend: {
      orient: 'vertical', right: 0, top: 'center',
      textStyle: { color: isDark.value ? '#8b949e' : '#606266' }
    },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['38%', '50%'],
      itemStyle: { borderRadius: 4 },
      label: { color: isDark.value ? '#8b949e' : '#606266' },
      data: items.length ? items : [{ name: '无数据', value: 1, itemStyle: { color: isDark.value ? '#30363d' : '#f0f0f0' } }]
    }]
  })
  // 注册 resize 监听
  window.addEventListener('resize', handleResize)
}

watch(() => props.data, () => nextTick(render), { deep: true })
/* 主题联动：切换时销毁并以对应主题重新 init */
watch(isDark, () => nextTick(render))
onMounted(() => nextTick(render))

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>
