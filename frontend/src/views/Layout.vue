<template>
  <div class="app-layout">
    <!-- 侧边栏 -->
    <div class="app-sidebar">
      <div style="padding:20px;text-align:center;border-bottom:1px solid var(--border-sidebar);">
        <h2 style="font-size:20px;color:var(--sidebar-text);font-weight:700;">CodeInspector</h2>
        <p style="font-size:12px;color:var(--sidebar-text-secondary);margin-top:4px;">智能代码审查系统</p>
      </div>
      <el-menu :default-active="activeMenu" router
        :background-color="menuBg"
        :text-color="menuText"
        :active-text-color="menuActiveText"
        style="border:none;margin-top:8px;">
        <el-menu-item index="/projects">
          <el-icon><Folder /></el-icon>
          <span>项目管理</span>
        </el-menu-item>
        <el-menu-item index="/teams">
          <el-icon><UserFilled /></el-icon>
          <span>团队管理</span>
        </el-menu-item>
        <el-menu-item index="/team-tasks">
          <el-icon><Tickets /></el-icon>
          <span>审查任务</span>
        </el-menu-item>
        <el-menu-item index="/stats">
          <el-icon><DataAnalysis /></el-icon>
          <span>统计分析</span>
        </el-menu-item>
        <el-menu-item index="/review-center">
          <el-icon><DocumentChecked /></el-icon>
          <span>审查详情</span>
        </el-menu-item>
        <el-menu-item index="/profile">
          <el-icon><User /></el-icon>
          <span>个人信息</span>
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
          <!-- 主题切换按钮：太阳 / 月亮 极简 SVG -->
          <button class="theme-toggle-btn" @click="toggleTheme" :title="isDark ? '切换到浅色模式' : '切换到深色模式'">
            <!-- 月亮图标 (浅色模式下显示，点击进入深色) -->
            <svg v-if="!isDark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
            </svg>
            <!-- 太阳图标 (深色模式下显示，点击回到浅色) -->
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="5"></circle>
              <line x1="12" y1="1" x2="12" y2="3"></line>
              <line x1="12" y1="21" x2="12" y2="23"></line>
              <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line>
              <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line>
              <line x1="1" y1="12" x2="3" y2="12"></line>
              <line x1="21" y1="12" x2="23" y2="12"></line>
              <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line>
              <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line>
            </svg>
          </button>
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
                <el-dropdown-item @click="$router.push('/profile')">
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
import { useTheme } from '@/composables/useTheme'

const route = useRoute()
const authStore = useAuthStore()
const { isDark, toggleTheme } = useTheme()

const activeMenu = computed(() => route.path)

/* 侧边栏菜单颜色随主题自适应 */
const menuBg = computed(() => isDark.value ? '#0d1117' : '#ffffff')
const menuText = computed(() => isDark.value ? 'rgba(201,209,217,0.7)' : 'rgba(36,41,47,0.7)')
const menuActiveText = computed(() => isDark.value ? '#ffffff' : '#24292f')

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
