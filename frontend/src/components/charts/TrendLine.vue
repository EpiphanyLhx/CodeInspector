<template>
  <div ref="chartRef" style="height:300px;"></div>
</template>
<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ data: { type: Array, default: () => [] } })
const chartRef = ref(null)
let chart = null

const render = () => {
  if (!chartRef.value) return
  chart?.dispose()
  chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['Bug率(‰)', '评分'] },
    xAxis: { type: 'category', data: props.data.map(d => d.date) },
    yAxis: [
      { type: 'value', name: 'Bug率(‰)', min: 0 },
      { type: 'value', name: '评分', min: 0, max: 100 }
    ],
    series: [
      { name: 'Bug率(‰)', type: 'line', yAxisIndex: 0, smooth: true,
        data: props.data.map(d => d.bugRate), itemStyle: { color: '#F56C6C' },
        areaStyle: { color: 'rgba(245,108,108,0.15)' } },
      { name: '评分', type: 'line', yAxisIndex: 1, smooth: true,
        data: props.data.map(d => d.score), itemStyle: { color: '#409EFF' },
        areaStyle: { color: 'rgba(64,158,255,0.15)' } }
    ]
  })
}

watch(() => props.data, () => nextTick(render), { deep: true })
onMounted(() => nextTick(render))
</script>
