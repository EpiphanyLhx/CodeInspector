<template>
  <div ref="chartRef" style="height:200px;"></div>
</template>
<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ score: { type: Number, default: 0 } })
const chartRef = ref(null)
let chart = null

const render = () => {
  if (!chartRef.value) return
  chart?.dispose()
  chart = echarts.init(chartRef.value)
  chart.setOption({
    series: [{
      type: 'gauge', startAngle: 200, endAngle: -20, center: ['50%', '55%'], radius: '90%',
      min: 0, max: 100,
      axisLine: { lineStyle: { width: 16,
        color: [[0.3, '#F56C6C'], [0.6, '#E6A23C'], [0.8, '#409EFF'], [1, '#67C23A']]
      }},
      pointer: { length: '70%', width: 6, itemStyle: { color: '#303133' } },
      detail: { valueAnimation: true, fontSize: 22, offsetCenter: [0, '70%'], formatter: '{value}分' },
      data: [{ value: props.score }]
    }]
  })
}

watch(() => props.score, () => nextTick(render))
onMounted(() => nextTick(render))
</script>
