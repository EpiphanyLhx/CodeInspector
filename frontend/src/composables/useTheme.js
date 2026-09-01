/**
 * useTheme — 双主题切换引擎 (Light / Dark)
 *
 * 设计要点：
 * 1. 模块级单例状态，全应用共享同一个 isDark 响应式引用
 * 2. 持久化优先级：localStorage > 系统媒体查询 (prefers-color-scheme)
 * 3. 通过 <html class="dark"> 激活 Element Plus 内置暗黑主题，零硬编码覆写
 * 4. initTheme() 在应用挂载前调用，配合 index.html 内联脚本杜绝 FOUC
 */

import { ref, watch } from 'vue'

const STORAGE_KEY = 'codeinspector-theme'

/** 模块级单例 — 所有组件共享同一份响应式状态 */
const isDark = ref(false)
let initialized = false

/**
 * 将主题状态同步到 <html> 标签的 dark 类名
 * 这是激活 Element Plus 暗黑主题的官方推荐方式
 */
function applyDarkClass(dark) {
  const html = document.documentElement
  if (dark) {
    html.classList.add('dark')
  } else {
    html.classList.remove('dark')
  }
}

/**
 * 初始化主题：读取 localStorage，无则降级到系统偏好
 * 必须在应用挂载前调用一次
 */
function initTheme() {
  if (initialized) return
  initialized = true

  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved !== null) {
    isDark.value = saved === 'dark'
  } else {
    // 降级：读取系统媒体查询
    isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
  }

  applyDarkClass(isDark.value)

  // 监听系统主题变化（仅当用户未手动设置过时跟随系统）
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  mediaQuery.addEventListener('change', (e) => {
    if (localStorage.getItem(STORAGE_KEY) === null) {
      isDark.value = e.matches
      applyDarkClass(isDark.value)
    }
  })
}

/**
 * 切换主题：翻转 isDark，同步 <html> 类名与 localStorage
 */
function toggleTheme() {
  isDark.value = !isDark.value
  applyDarkClass(isDark.value)
  localStorage.setItem(STORAGE_KEY, isDark.value ? 'dark' : 'light')
}

/**
 * 组合式 API 入口
 * @returns {{ isDark: Ref<boolean>, toggleTheme: Function, initTheme: Function }}
 */
export function useTheme() {
  return { isDark, toggleTheme, initTheme }
}
