<template>
  <el-row :gutter="16">
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
.stat-card {
  background: var(--bg-card);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--border-color);
  border-radius: 8px; padding: 20px;
  box-shadow: var(--shadow-card);
}
.stat-value { font-size: 28px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 13px; color: var(--text-placeholder); margin-top: 4px; }
</style>
