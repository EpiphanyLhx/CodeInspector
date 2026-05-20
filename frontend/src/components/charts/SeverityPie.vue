<template>
  <div ref="chartRef" style="height:260px;"></div>
</template>
<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ data: { type: Object, default: () => ({}) } })
const chartRef = ref(null)
let chart = null

const labels = { CRITICAL: '严重', MAJOR: '重要', MINOR: '次要', INFO: '提示' }
const colors = { CRITICAL: '#F56C6C', MAJOR: '#E6A23C', MINOR: '#409EFF', INFO: '#909399' }

const render = () => {
  if (!chartRef.value) return
  chart?.dispose()
  chart = echarts.init(chartRef.value)
  const items = Object.entries(props.data).map(([k, v]) => ({
    name: labels[k] || k, value: v, itemStyle: { color: colors[k] || '#909399' }
  }))
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', right: 0, top: 'center' },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['38%', '50%'],
      itemStyle: { borderRadius: 4 },
      data: items.length ? items : [{ name: '无数据', value: 1, itemStyle: { color: '#f0f0f0' } }]
    }]
  })
}

watch(() => props.data, () => nextTick(render), { deep: true })
onMounted(() => nextTick(render))
</script>
