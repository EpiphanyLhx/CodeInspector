<template>
  <div ref="chartRef" style="height:260px;min-height:260px;width:100%;"></div>
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ data: { type: Object, default: () => ({}) } })
const chartRef = ref(null)
let chart = null

const labels = { SECURITY: '安全', BUG: 'Bug', CODE_STYLE: '代码风格', PERFORMANCE: '性能', BEST_PRACTICE: '最佳实践' }
const colors = ['#F56C6C', '#E6A23C', '#409EFF', '#67C23A', '#9254de']

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
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: Object.keys(props.data).map(k => labels[k] || k) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar', data: Object.values(props.data),
      itemStyle: { borderRadius: [6, 6, 0, 0], color: (p) => colors[p.dataIndex % colors.length] }
    }]
  })
  window.addEventListener('resize', handleResize)
}

watch(() => props.data, () => nextTick(render), { deep: true })
onMounted(() => nextTick(render))

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>
