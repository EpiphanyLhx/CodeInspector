<template>
  <div ref="chartRef" style="height:300px;min-height:300px;width:100%;"></div>
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useTheme } from '@/composables/useTheme'

const props = defineProps({ data: { type: Array, default: () => [] } })
const { isDark } = useTheme()
const chartRef = ref(null)
let chart = null

const handleResize = () => chart?.resize()

const render = () => {
  if (!chartRef.value) return
  if (chartRef.value.offsetWidth === 0 || chartRef.value.offsetHeight === 0) {
    setTimeout(render, 150)
    return
  }
  chart?.dispose()
  chart = echarts.init(chartRef.value)
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark.value ? 'rgba(22,27,34,0.95)' : 'rgba(255,255,255,0.95)',
      borderColor: isDark.value ? '#30363d' : '#e4e7ed',
      textStyle: { color: isDark.value ? '#c9d1d9' : '#303133' }
    },
    legend: {
      data: ['Bug率(‰)'],
      textStyle: { color: isDark.value ? '#8b949e' : '#606266' }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: props.data.map(d => d.date),
      axisLine: { lineStyle: { color: isDark.value ? '#30363d' : '#e4e7ed' } },
      axisLabel: { color: isDark.value ? '#8b949e' : '#606266' }
    },
    yAxis: [
      {
        type: 'value', name: 'Bug率(‰)', min: 0,
        axisLine: { lineStyle: { color: isDark.value ? '#30363d' : '#e4e7ed' } },
        axisLabel: { color: isDark.value ? '#8b949e' : '#606266' },
        splitLine: { lineStyle: { color: isDark.value ? 'rgba(48,54,61,0.6)' : '#e4e7ed' } },
        nameTextStyle: { color: isDark.value ? '#8b949e' : '#606266' }
      }
    ],
    series: [
      { name: 'Bug率(‰)', type: 'line', yAxisIndex: 0, smooth: true,
        data: props.data.map(d => d.bugRate), itemStyle: { color: '#F56C6C' },
        areaStyle: { color: 'rgba(245,108,108,0.15)' } }
    ]
  })
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
