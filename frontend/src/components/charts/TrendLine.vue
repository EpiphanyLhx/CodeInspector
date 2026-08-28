<template>
  <div ref="chartRef" style="height:300px;min-height:300px;width:100%;"></div>
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ data: { type: Array, default: () => [] } })
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
    tooltip: { trigger: 'axis' },
    legend: { data: ['Bug率(‰)'] },
    xAxis: { type: 'category', data: props.data.map(d => d.date) },
    yAxis: [
      { type: 'value', name: 'Bug率(‰)', min: 0 }
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
onMounted(() => nextTick(render))

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>
