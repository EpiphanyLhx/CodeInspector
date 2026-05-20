<template>
  <div class="app-layout">
    <!-- 侧边栏 -->
    <div class="app-sidebar">
      <div style="padding:20px;text-align:center;border-bottom:1px solid rgba(255,255,255,0.1);">
        <h2 style="font-size:20px;color:#fff;font-weight:700;">CodeInspector</h2>
        <p style="font-size:12px;color:rgba(255,255,255,0.5);margin-top:4px;">智能代码审查系统</p>
      </div>
      <el-menu :default-active="activeMenu" router background-color="#1d1e2c"
        text-color="rgba(255,255,255,0.7)" active-text-color="#fff" style="border:none;margin-top:8px;">
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/projects">
          <el-icon><Folder /></el-icon>
          <span>项目管理</span>
        </el-menu-item>
        <el-menu-item index="/teams">
          <el-icon><UserFilled /></el-icon>
          <span>团队管理</span>
        </el-menu-item>
        <el-menu-item index="/stats">
          <el-icon><DataAnalysis /></el-icon>
          <span>统计分析</span>
        </el-menu-item>
        <el-menu-item index="/review-center">
          <el-icon><DocumentChecked /></el-icon>
          <span>审查详情</span>
        </el-menu-item>
      </el-menu>
    </div>

    <!-- 主区域 -->
    <div class="app-main">
      <div class="app-header">
        <div>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div style="display:flex;align-items:center;gap:16px;">
          <el-tag :type="roleTagType" size="small" effect="plain">
            {{ roleLabel }}
          </el-tag>
          <el-dropdown trigger="click">
            <span style="display:flex;align-items:center;gap:8px;cursor:pointer;">
              <el-avatar :size="32" icon="UserFilled" />
              <span>{{ authStore.user?.username || '用户' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>
                  <el-icon><User /></el-icon> 个人信息
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <div class="app-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()

const activeMenu = computed(() => route.path)

const roleLabel = computed(() => {
  const map = { ADMIN: '管理员', TEAM_LEADER: '团队负责人', DEVELOPER: '开发者', VIEWER: '观察者' }
  return map[authStore.userRole] || authStore.userRole
})

const roleTagType = computed(() => {
  const map = { ADMIN: 'danger', TEAM_LEADER: 'warning', DEVELOPER: 'success', VIEWER: 'info' }
  return map[authStore.userRole] || 'info'
})

const handleLogout = () => {
  authStore.logout()
}
</script>
