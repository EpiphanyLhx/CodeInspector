<template>
  <div class="fade-in">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
      <h2 style="font-size:22px;font-weight:600;">团队管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> 创建团队
      </el-button>
    </div>

    <el-row :gutter="16">
      <el-col :span="8" v-for="team in teams" :key="team.id" style="margin-bottom:16px;">
        <el-card shadow="hover" style="border-radius:8px;">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center;">
              <span style="font-weight:600;">{{ team.name }}</span>
              <el-button link type="primary" size="small" @click="selectTeam(team)">
                管理成员
              </el-button>
            </div>
          </template>
          <p style="font-size:13px;color:#909399;min-height:40px;">
            {{ team.description || '暂无描述' }}
          </p>
          <div style="display:flex;justify-content:space-between;align-items:center;margin-top:8px;">
            <span style="font-size:12px;color:#c0c4cc;">创建于 {{ formatDate(team.createTime) }}</span>
            <el-button link type="danger" size="small" icon="Delete"
              @click="handleDeleteTeam(team)">删除</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col v-if="teams.length === 0" :span="24">
        <el-empty description="暂无团队" />
      </el-col>
    </el-row>

    <!-- 成员管理对话框 -->
    <el-dialog v-model="showMemberDialog" :title="`${selectedTeam?.name} - 成员管理`" width="600px">
      <div style="display:flex;gap:8px;margin-bottom:16px;">
        <el-input v-model="newMemberId" placeholder="输入用户ID" style="width:160px;" />
        <el-select v-model="newMemberRole" placeholder="角色" style="width:120px;">
          <el-option label="成员" value="MEMBER" />
          <el-option label="管理员" value="ADMIN" />
          <el-option label="负责人" value="LEADER" />
        </el-select>
        <el-button type="primary" @click="handleAddMember">添加成员</el-button>
      </div>
      <el-table :data="members" style="width:100%">
        <el-table-column prop="userId" label="用户ID" width="80" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="roleType(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="joinTime" label="加入时间" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="handleRemoveMember(row.userId)">
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 创建团队对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建团队" width="450px">
      <el-form :model="createForm">
        <el-form-item label="团队名称" required>
          <el-input v-model="createForm.name" placeholder="请输入团队名称" />
        </el-form-item>
        <el-form-item label="团队描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getMyTeams, getTeamMembers, addTeamMember, removeTeamMember, createTeam, deleteTeam } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const teams = ref([])
const members = ref([])
const showCreateDialog = ref(false)
const showMemberDialog = ref(false)
const selectedTeam = ref(null)
const newMemberId = ref('')
const newMemberRole = ref('MEMBER')
const createForm = reactive({ name: '', description: '' })

const roleType = (r) => ({ LEADER: 'danger', ADMIN: 'warning', MEMBER: 'success' }[r] || 'info')
const roleLabel = (r) => ({ LEADER: '负责人', ADMIN: '管理员', MEMBER: '成员' }[r] || r)
const formatDate = (d) => d ? new Date(d).toLocaleDateString('zh-CN') : ''

const loadTeams = async () => {
  try {
    const res = await getMyTeams()
    teams.value = res.data || []
  } catch { /* ignore */ }
}

const selectTeam = async (team) => {
  selectedTeam.value = team
  showMemberDialog.value = true
  try {
    const res = await getTeamMembers(team.id)
    members.value = res.data || []
  } catch { /* ignore */ }
}

const handleAddMember = async () => {
  if (!newMemberId.value) { ElMessage.warning('请输入用户ID'); return }
  try {
    await addTeamMember(selectedTeam.value.id, Number(newMemberId.value), newMemberRole.value)
    ElMessage.success('添加成功')
    newMemberId.value = ''
    selectTeam(selectedTeam.value)
  } catch { /* ignore */ }
}

const handleRemoveMember = async (userId) => {
  try {
    await ElMessageBox.confirm('确认移除该成员？', '提示', { type: 'warning' })
    await removeTeamMember(selectedTeam.value.id, userId)
    ElMessage.success('已移除')
    selectTeam(selectedTeam.value)
  } catch { /* ignore */ }
}

const handleDeleteTeam = async (team) => {
  try {
    await ElMessageBox.confirm(
      `确认删除团队「${team.name}」？\n\n若团队下存在项目将无法删除。`,
      '删除团队',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteTeam(team.id)
    ElMessage.success('团队已删除')
    loadTeams()
  } catch { /* ignore cancel or error */ }
}

const handleCreate = async () => {
  if (!createForm.name) { ElMessage.warning('请输入团队名称'); return }
  try {
    await createTeam(createForm)
    ElMessage.success('团队创建成功')
    showCreateDialog.value = false
    createForm.name = ''
    createForm.description = ''
    loadTeams()
  } catch { /* ignore */ }
}

onMounted(loadTeams)
</script>
