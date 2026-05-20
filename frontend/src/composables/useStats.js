import { ref } from 'vue'
import { getDashboard, getBugRateTrend, getProjectList, getIssueStats, getReviewReport, getProjectIssues } from '@/api'

export function useStats() {
  const reviewedProjects = ref([])
  const statCards = ref([
    { label: '项目总数', value: 0, color: '#409EFF' },
    { label: '已完成审查', value: 0, color: '#67C23A' },
    { label: '发现问题总数', value: 0, color: '#E6A23C' },
    { label: '综合评分', value: 0, color: '#F56C6C' }
  ])

  async function loadGlobalStats() {
    const res = await getDashboard()
    const d = res.data
    statCards.value[0].value = d.totalProjects || 0
    statCards.value[1].value = d.reviewedProjects || 0
    statCards.value[2].value = d.totalIssues || 0
    statCards.value[3].value = d.averageScore || 0
    return {
      severity: d.severityDistribution || {},
      category: d.categoryDistribution || {},
      report: null,
      issues: [],
      trend: null
    }
  }

  async function loadProjectStats(projectId) {
    const [statsRes, reportRes, issuesRes] = await Promise.all([
      getIssueStats(projectId),
      getReviewReport(projectId),
      getProjectIssues(projectId)
    ])
    const stats = statsRes.data || {}
    statCards.value[0].value = 1
    statCards.value[1].value = 1
    statCards.value[2].value = (issuesRes.data || []).length
    statCards.value[3].value = reportRes.data?.score ?? '-'
    return {
      severity: stats.severityStats || {},
      category: stats.categoryStats || {},
      report: reportRes.data || null,
      issues: issuesRes.data || [],
      trend: null
    }
  }

  async function loadTrendData() {
    const res = await getBugRateTrend()
    return res.data || []
  }

  async function loadProjects() {
    const res = await getProjectList(1, 100)
    const all = res.data?.records || []
    reviewedProjects.value = all.filter(p => p.reviewStatus === 'COMPLETED')
  }

  return { statCards, reviewedProjects, loadGlobalStats, loadProjectStats, loadTrendData, loadProjects }
}
