import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { guest: true }
  },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' }
      },
      {
        path: 'projects',
        name: 'ProjectList',
        component: () => import('@/views/ProjectList.vue'),
        meta: { title: '项目管理', icon: 'Folder' }
      },
      {
        path: 'projects/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/ProjectDetail.vue'),
        meta: { title: '项目详情' }
      },
      {
        path: 'projects/:id/review',
        name: 'ReviewDetail',
        component: () => import('@/views/ReviewDetail.vue'),
        meta: { title: '代码审查' }
      },
      {
        path: 'teams',
        name: 'TeamManage',
        component: () => import('@/views/TeamManage.vue'),
        meta: { title: '团队管理', icon: 'UserFilled' }
      },
      {
        path: 'stats',
        name: 'Stats',
        component: () => import('@/views/Stats.vue'),
        meta: { title: '统计分析', icon: 'DataAnalysis' }
      },
      {
        path: 'review-center',
        name: 'ReviewCenter',
        component: () => import('@/views/ReviewCenter.vue'),
        meta: { title: '审查详情', icon: 'DocumentChecked' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  if (to.meta.guest) {
    if (authStore.isLoggedIn) {
      return next('/dashboard')
    }
    return next()
  }
  if (!authStore.isLoggedIn) {
    return next('/login')
  }
  next()
})

export default router
