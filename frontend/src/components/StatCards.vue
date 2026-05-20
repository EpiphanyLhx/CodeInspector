<template>
  <el-row :gutter="20">
    <el-col :span="6" v-for="item in cards" :key="item.label">
      <div class="stat-card">
        <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
        <div class="stat-label">{{ item.label }}</div>
      </div>
    </el-col>
  </el-row>
</template>

<script setup>
defineProps({ cards: { type: Array, default: () => [] } })
</script>

<style scoped>
.stat-card { background: #fff; border-radius: 8px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.stat-value { font-size: 28px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
</style>
