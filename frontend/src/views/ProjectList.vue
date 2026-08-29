<template>
  <div class="fade-in">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
      <h2 style="font-size:22px;font-weight:600;">项目管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> 新建项目
      </el-button>
    </div>

    <!-- 项目列表 -->
    <el-row :gutter="16">
      <el-col :span="8" v-for="project in projects" :key="project.id" style="margin-bottom:16px;">
        <el-card shadow="hover" style="border-radius:8px;position:relative;">
          <div style="display:flex;justify-content:space-between;align-items:flex-start;cursor:pointer;" @click="router.push('/projects/' + project.id)">
            <div style="flex:1;">
              <h3 style="font-size:16px;margin-bottom:4px;">{{ project.name }}</h3>
              <p style="font-size:13px;color:#909399;margin-bottom:12px;">
                {{ project.description || '暂无描述' }}
              </p>
              <div style="display:flex;gap:8px;flex-wrap:wrap;">
                <el-tag :type="statusType(project.reviewStatus)" size="small">
                  {{ statusLabel(project.reviewStatus) }}
                </el-tag>
                <el-tag type="info" size="small">{{ project.sourceType === 'GIT' ? 'Git' : '上传' }}</el-tag>
                <el-tag type="info" size="small">{{ project.language || 'Java' }}</el-tag>
              </div>
            </div>
          </div>
          <div style="margin-top:12px;display:flex;gap:16px;font-size:12px;color:#909399;">
            <span>{{ project.totalFiles || 0 }} 文件</span>
            <span>{{ project.totalLines || 0 }} 行</span>
            <span>{{ project.issueCount || 0 }} 问题</span>
          </div>
          <div v-if="project.issueCount > 0" style="margin-top:8px;display:flex;gap:4px;">
            <span v-if="project.criticalCount" style="color:#F56C6C;font-size:12px;">●{{ project.criticalCount }}</span>
            <span v-if="project.majorCount" style="color:#E6A23C;font-size:12px;">●{{ project.majorCount }}</span>
            <span v-if="project.minorCount" style="color:#409EFF;font-size:12px;">●{{ project.minorCount }}</span>
          </div>
          <div style="margin-top:10px;text-align:right;border-top:1px solid #f0f0f0;padding-top:8px;">
            <el-button link type="danger" size="small" icon="Delete"
              @click.stop="handleDelete(project)">删除</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col v-if="projects.length === 0" :span="24">
        <el-empty description="暂无项目，点击【新建项目】开始" />
      </el-col>
    </el-row>

    <!-- 分页 -->
    <div style="text-align:center;margin-top:24px;" v-if="total > pageSize">
      <el-pagination v-model:current-page="page" :page-size="pageSize"
        :total="total" layout="prev, pager, next" @current-change="loadProjects" />
    </div>

    <!-- 创建项目对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建项目" width="520px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="所属团队">
          <el-select v-model="createForm.teamId" placeholder="个人项目（可选）" clearable style="width:100%">
            <el-option label="👤 个人项目（不归属团队）" :value="null" />
            <el-option v-for="team in teams" :key="team.id"
              :label="team.name" :value="team.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目名称" required>
          <el-input v-model="createForm.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="代码来源" required>
          <el-radio-group v-model="createForm.sourceType">
            <el-radio value="UPLOAD">上传代码包</el-radio>
            <el-radio value="GIT">Git仓库</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="createForm.sourceType === 'GIT'" label="Git URL" required>
          <el-input v-model="createForm.gitUrl" placeholder="https://github.com/user/repo.git" />
        </el-form-item>
        <el-form-item v-if="createForm.sourceType === 'GIT'" label="分支">
          <el-input v-model="createForm.gitBranch" placeholder="main" />
        </el-form-item>
        <template v-if="createForm.sourceType === 'GIT'">
          <el-form-item label="Git 用户名">
            <el-input v-model="createForm.gitUsername" placeholder="私有仓库用户名（公开仓库可不填）" />
          </el-form-item>
          <el-form-item label="访问令牌">
            <el-input v-model="createForm.gitToken" type="password" show-password
              placeholder="私有仓库访问令牌（公开仓库可不填，加密存储）" />
          </el-form-item>
        </template>
        <el-form-item label="主要语言">
          <el-select v-model="createForm.language" style="width:100%">
            <el-option label="Java" value="java" />
            <el-option label="Python" value="python" />
            <el-option label="JavaScript" value="javascript" />
            <el-option label="TypeScript" value="typescript" />
            <el-option label="Go" value="go" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getProjectList, createProject, getMyTeams, deleteProject } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const projects = ref([])
const page = ref(1)
const pageSize = ref(9)
const total = ref(0)

const statusType = (s) => {
  const map = { PENDING: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success', FAILED: 'danger' }
  return map[s] || 'info'
}
const statusLabel = (s) => {
  const map = { PENDING: '待审查', IN_PROGRESS: '审查中', COMPLETED: '已完成', FAILED: '失败' }
  return map[s] || s
}

const loadProjects = async () => {
  try {
    const res = await getProjectList(page.value, pageSize.value)
    projects.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { /* ignore */ }
}

// 创建项目
const showCreateDialog = ref(false)
const creating = ref(false)
const teams = ref([])
const createForm = reactive({
  teamId: null, name: '', description: '',
  sourceType: 'UPLOAD', gitUrl: '', gitBranch: 'main', language: 'java',
  gitUsername: '', gitToken: ''
})

const loadTeams = async () => {
  try {
    const res = await getMyTeams()
    teams.value = res.data || []
  } catch { /* ignore */ }
}

const handleCreate = async () => {
  if (!createForm.name) {
    ElMessage.warning('请填写必填项')
    return
  }
  creating.value = true
  try {
    await createProject(createForm)
    ElMessage.success('项目创建成功')
    showCreateDialog.value = false
    Object.assign(createForm, {
      name: '', description: '', gitUrl: '',
      gitUsername: '', gitToken: ''
    })
    loadProjects()
  } catch { /* ignore */ }
  creating.value = false
}

const handleDelete = async (project) => {
  try {
    await ElMessageBox.confirm(
      `确认删除项目「${project.name}」？删除后不可恢复。`,
      '删除确认',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteProject(project.id)
    ElMessage.success('项目已删除')
    loadProjects()
  } catch { /* ignore cancel */ }
}

onMounted(() => {
  loadProjects()
  loadTeams()
})
</script>
