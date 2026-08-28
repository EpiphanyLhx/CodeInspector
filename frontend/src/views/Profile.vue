<template>
  <div class="fade-in">
    <h2 style="font-size:22px;font-weight:600;margin-bottom:20px;">个人信息</h2>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- 1. 头像管理 -->
      <el-tab-pane label="头像管理" name="avatar">
        <el-row :gutter="30">
          <el-col :span="8" style="text-align:center;">
            <el-avatar :size="160" :src="user.avatar ? user.avatar : ''"
              style="margin-bottom:16px;">
              <span style="font-size:60px;">{{ user.username?.charAt(0) }}</span>
            </el-avatar>
            <div>
              <el-upload :show-file-list="false" :before-upload="beforeUpload"
                :http-request="handleUpload" accept="image/*">
                <el-button type="primary" :loading="uploading">
                  <el-icon><Upload /></el-icon> 上传头像
                </el-button>
              </el-upload>
              <p style="font-size:12px;color:#909399;margin-top:8px;">支持 JPG/PNG，不超过 2MB</p>
            </div>
          </el-col>
          <el-col :span="16">
            <el-alert title="头像用于个人标识，建议使用清晰正面照" type="info" :closable="false" show-icon />
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 2. 基本信息 -->
      <el-tab-pane label="基本信息" name="info">
        <el-form ref="infoFormRef" :model="infoForm" :rules="infoRules" label-width="80px"
          style="max-width:480px;">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="infoForm.username" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="infoForm.email" />
          </el-form-item>
          <el-form-item label="角色">
            <el-tag :type="roleType">{{ roleLabel }}</el-tag>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saveInfoLoading" @click="saveInfo">保存修改</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 3. 联系方式 -->
      <el-tab-pane label="联系方式" name="contact">
        <el-form ref="contactFormRef" :model="contactForm" :rules="contactRules" label-width="80px"
          style="max-width:480px;">
          <el-form-item label="手机号">
            <el-input v-model="contactForm.phone" placeholder="待绑定" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="contactForm.email" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saveContactLoading" @click="saveContact">保存修改</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 4. 账号安全 -->
      <el-tab-pane label="账号安全" name="security">
        <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px"
          style="max-width:420px;">
          <el-form-item label="旧密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="pwdLoading" @click="changePwd">修改密码</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 5. 代码仓 -->
      <el-tab-pane label="代码仓" name="history">
        <div v-loading="historyLoading">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;flex-wrap:wrap;gap:8px;">
            <span style="font-size:14px;color:#606266;">
              上传记录（{{ history.length }}条）
              <span v-if="statusFilter" style="color:#409EFF;"> · 已筛选</span>
            </span>
            <div style="display:flex;gap:8px;align-items:center;">
              <el-select v-model="statusFilter" placeholder="全部状态" clearable
                @change="loadHistory" style="width:140px;" size="small">
                <el-option label="全部状态" value="" />
                <el-option label="待审查" value="PENDING" />
                <el-option label="审查中" value="IN_PROGRESS" />
                <el-option label="已完成" value="COMPLETED" />
                <el-option label="失败" value="FAILED" />
              </el-select>
              <el-button size="small" @click="loadHistory" :loading="historyLoading" icon="Refresh">刷新</el-button>
            </div>
          </div>
          <el-table :data="history" stripe max-height="420">
            <el-table-column prop="projectName" label="项目名称" min-width="140" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{row}">
                <el-tag :type="statusTagType(row.reviewStatus)" size="small" effect="dark">
                  {{ statusLabel(row.reviewStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="来源" width="70" align="center">
              <template #default="{row}">{{ row.sourceType === 'GIT' ? 'Git' : '上传' }}</template>
            </el-table-column>
            <el-table-column prop="language" label="语言" width="70" />
            <el-table-column label="文件/行数" width="110">
              <template #default="{row}">{{ row.files || 0 }}文件 / {{ row.lines || 0 }}行</template>
            </el-table-column>
            <el-table-column label="发现问题" width="130">
              <template #default="{row}">
                <template v-if="row.reviewStatus === 'COMPLETED'">
                  <span v-if="row.critical" style="color:#F56C6C;font-weight:600;">●{{ row.critical }}</span>
                  <span v-if="row.major" style="color:#E6A23C;margin-left:4px;">●{{ row.major }}</span>
                  <span style="color:#909399;margin-left:4px;">{{ row.issues }}个</span>
                </template>
                <span v-else style="color:#c0c4cc;">-</span>
              </template>
            </el-table-column>
            <el-table-column label="上传时间" width="160">
              <template #default="{row}">{{ formatDate(row.createDate) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small"
                  @click="$router.push('/projects/' + row.projectId)">详情</el-button>
                <el-button link type="primary" size="small"
                  v-if="row.reviewStatus === 'COMPLETED'"
                  @click="$router.push('/projects/' + row.projectId + '/review')">结果</el-button>
                <el-button link type="danger" size="small"
                  @click="handleDeleteHistory(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="history.length === 0" description="暂无上传记录" />
        </div>
      </el-tab-pane>

      <!-- 6. API密钥管理 -->
      <el-tab-pane label="API密钥" name="apikey">
        <div v-loading="apiKeysLoading">
          <el-alert
            title="在这里配置你自己的AI API密钥，审查时将优先使用你的密钥调用AI模型。"
            type="info" :closable="false" show-icon style="margin-bottom:16px;" />
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
            <span style="font-size:14px;color:#606266;">已配置 {{ apiKeys.length }} 个密钥</span>
            <el-button type="primary" size="small" @click="showAddKeyDialog">添加密钥</el-button>
          </div>
          <el-table :data="apiKeys" stripe>
            <el-table-column label="提供商" width="160">
              <template #default="{row}">{{ row.providerLabel }}</template>
            </el-table-column>
            <el-table-column label="Key" width="180">
              <template #default="{row}">{{ row.apiKeyMasked }}</template>
            </el-table-column>
            <el-table-column label="模型" width="160">
              <template #default="{row}">{{ row.modelName }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120" align="center">
              <template #default="{row}">
                <el-tag v-if="row.isActive" type="success" size="small" effect="dark">使用中</el-tag>
                <el-tag v-else-if="row.isValid" type="primary" size="small">已验证</el-tag>
                <el-tag v-else type="warning" size="small">未验证</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最后验证" width="160">
              <template #default="{row}">
                {{ row.lastValidatedAt ? new Date(row.lastValidatedAt).toLocaleString('zh-CN') : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="260">
              <template #default="{row}">
                <el-button link type="primary" size="small" v-if="!row.isActive"
                  @click="handleActivate(row)">启用</el-button>
                <el-button link type="primary" size="small"
                  @click="handleValidate(row)" :loading="validatingId === row.id">验证</el-button>
                <el-button link type="primary" size="small"
                  @click="showAddKeyDialog(row)">编辑</el-button>
                <el-popconfirm title="确认删除该密钥配置？" @confirm="handleDeleteKey(row)">
                  <template #reference>
                    <el-button link type="danger" size="small">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="apiKeys.length === 0 && !apiKeysLoading" description="尚未配置API密钥" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- API Key 编辑对话框 -->
    <el-dialog v-model="keyDialogVisible" :title="editingKeyId ? '编辑密钥' : '添加密钥'"
      width="520px" destroy-on-close>
      <el-form ref="keyFormRef" :model="keyForm" :rules="keyRules" label-width="90px"
        autocomplete="off">
        <el-form-item label="AI提供商" prop="provider">
          <el-select v-model="keyForm.provider" placeholder="请选择AI提供商"
            @change="onProviderChange" style="width:100%">
            <el-option label="通义千问 (阿里云)" value="tongyi" />
            <el-option label="文心一言 (百度)" value="wenxin" />
            <el-option label="OpenAI (ChatGPT)" value="openai" />
            <el-option label="自定义API (OpenAI兼容)" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="keyForm.apiKey" type="password" show-password
            autocomplete="one-time-code"
            placeholder="请输入你的API Key" />
          <div style="font-size:12px;color:#909399;margin-top:4px;">
            Key将使用AES加密存储，不会明文保存
          </div>
        </el-form-item>
        <el-form-item label="Secret Key" prop="secretKey"
          v-if="keyForm.provider === 'wenxin'">
          <el-input v-model="keyForm.secretKey" type="password" show-password
            autocomplete="one-time-code"
            placeholder="文心一言需要同时提供Secret Key" />
        </el-form-item>
        <el-form-item label="API端点" prop="baseUrl"
          v-if="keyForm.provider === 'custom'">
          <el-input v-model="keyForm.baseUrl"
            placeholder="如: https://api.deepseek.com/v1" />
          <div style="font-size:12px;color:#409EFF;margin-top:4px;" v-if="!keyForm.baseUrl && keyForm.modelName">
            系统将根据模型名自动推断端点：
            {{ keyForm.modelName.toLowerCase().includes('deepseek') ? 'https://api.deepseek.com/v1' :
               keyForm.modelName.toLowerCase().includes('glm') ? 'https://open.bigmodel.cn/api/paas/v4' :
               keyForm.modelName.toLowerCase().includes('moonshot') ? 'https://api.moonshot.cn/v1' :
               keyForm.modelName.toLowerCase().includes('qwen') ? 'https://dashscope.aliyuncs.com/compatible-mode/v1' :
               keyForm.modelName.toLowerCase().includes('gpt') ? 'https://api.openai.com/v1' : '' }}
          </div>
          <div style="font-size:12px;color:#909399;margin-top:4px;" v-else-if="!keyForm.modelName">
            请输入OpenAI兼容格式的API端点地址，选择模型后可自动推断
          </div>
          <div style="font-size:12px;color:#67C23A;margin-top:4px;" v-else>
            ✓ 已手动指定端点
          </div>
        </el-form-item>
        <el-form-item label="模型" prop="modelName">
          <el-select v-model="keyForm.modelName" placeholder="请选择或输入模型"
            filterable allow-create style="width:100%">
            <el-option v-for="m in availableModels" :key="m" :label="m" :value="m" />
          </el-select>
          <div style="font-size:12px;color:#909399;margin-top:4px;">
            可输入自定义模型名称（如 deepseek-chat、glm-4-plus 等）
          </div>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="keyForm.setActive" active-text="保存后立即启用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="keyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingKey" @click="handleSaveKey">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getCurrentUser, updateProfile, changePassword, uploadAvatar,
  getCodeHistory, deleteCodeHistory,
  getApiKeys, saveApiKey, activateApiKey, validateApiKey, deleteApiKey,
  getModelsForProvider } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const activeTab = ref('avatar')
const user = ref(authStore.user || {})

const roleType = computed(() =>
  ({ ADMIN: 'danger', TEAM_LEADER: 'warning', DEVELOPER: 'success', VIEWER: 'info' }[user.value.role]))
const roleLabel = computed(() =>
  ({ ADMIN: '管理员', TEAM_LEADER: '团队负责人', DEVELOPER: '开发者', VIEWER: '观察者' }[user.value.role]))

// ================ 基本信息 ================
const infoForm = reactive({ username: user.value.username || '', email: user.value.email || '' })
const infoRules = {
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}
const saveInfoLoading = ref(false)
const saveInfo = async () => {
  saveInfoLoading.value = true
  try {
    const res = await updateProfile({ username: infoForm.username, email: infoForm.email })
    user.value = res.data; authStore.user = res.data
    ElMessage.success('保存成功')
  } catch {}
  saveInfoLoading.value = false
}

// ================ 联系方式 ================
const contactForm = reactive({ email: user.value.email || '', phone: '' })
const contactRules = { email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }] }
const saveContactLoading = ref(false)
const saveContact = async () => {
  saveContactLoading.value = true
  try {
    await updateProfile({ email: contactForm.email })
    ElMessage.success('保存成功')
  } catch {}
  saveContactLoading.value = false
}

// ================ 密码 ================
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, message: '至少6位', trigger: 'blur' }],
  confirmPassword: [{
    required: true, validator: (r, v, cb) => v !== pwdForm.newPassword ? cb('两次密码不一致') : cb()
  }]
}
const pwdLoading = ref(false)
const changePwd = async () => {
  pwdLoading.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    pwdForm.oldPassword = ''; pwdForm.newPassword = ''; pwdForm.confirmPassword = ''
    setTimeout(() => authStore.logout(), 1500)
  } catch {}
  pwdLoading.value = false
}

// ================ 头像 ================
const uploading = ref(false)
const beforeUpload = (file) => {
  if (file.size > 2 * 1024 * 1024) { ElMessage.error('文件不超过2MB'); return false }
  return true
}
const handleUpload = async ({ file }) => {
  uploading.value = true
  try {
    const res = await uploadAvatar(file)
    user.value.avatar = res.data.url
    authStore.user.avatar = res.data.url
    ElMessage.success('头像更新成功')
  } catch {}
  uploading.value = false
}

// ================ 代码仓 ================
const history = ref([])
const historyLoading = ref(false)
const statusFilter = ref('')
const statusLabel = (s) => ({ PENDING: '待审查', IN_PROGRESS: '审查中', COMPLETED: '已完成', FAILED: '失败' }[s] || s)
const statusTagType = (s) => ({ PENDING: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success', FAILED: 'danger' }[s] || 'info')
const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await getCodeHistory(user.value.id, statusFilter.value || undefined)
    history.value = res.data || []
  } catch {}
  historyLoading.value = false
}
const handleDeleteHistory = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除「${row.projectName}」的审查记录？`, '删除确认',
      { type: 'warning' })
    await deleteCodeHistory(row.projectId)
    ElMessage.success('已删除')
    loadHistory()
  } catch {}
}
const formatDate = (d) => d ? new Date(d).toLocaleString('zh-CN') : ''

// ================ API Key 管理 ================
const apiKeys = ref([])
const apiKeysLoading = ref(false)
const keyDialogVisible = ref(false)
const editingKeyId = ref(null)
const savingKey = ref(false)
const validatingId = ref(null)
const availableModels = ref([])
const keyFormRef = ref(null)

const keyForm = reactive({
  provider: 'tongyi',
  apiKey: '',
  secretKey: '',
  baseUrl: '',
  modelName: '',
  setActive: true
})

const keyRules = {
  provider: [{ required: true, message: '请选择AI提供商', trigger: 'change' }],
  apiKey: [{ required: true, message: '请输入API Key', trigger: 'blur' },
    { min: 8, message: 'API Key长度至少8位', trigger: 'blur' }],
  modelName: [{ required: true, message: '请选择或输入模型名称', trigger: 'change' }]
}

const loadApiKeys = async () => {
  apiKeysLoading.value = true
  try {
    const res = await getApiKeys()
    apiKeys.value = res.data || []
  } catch {}
  apiKeysLoading.value = false
}

const showAddKeyDialog = (row) => {
  if (row) {
    editingKeyId.value = row.id
    keyForm.provider = row.provider
    keyForm.apiKey = ''
    keyForm.secretKey = ''
    keyForm.baseUrl = row.baseUrl || ''
    keyForm.modelName = row.modelName
    keyForm.setActive = row.isActive
    loadModelsForProvider(row.provider)
  } else {
    editingKeyId.value = null
    keyForm.provider = 'tongyi'
    keyForm.apiKey = ''
    keyForm.secretKey = ''
    keyForm.baseUrl = ''
    keyForm.modelName = ''
    keyForm.setActive = true
    loadModelsForProvider('tongyi')
  }
  keyDialogVisible.value = true
}

const onProviderChange = (provider) => {
  keyForm.modelName = ''
  keyForm.secretKey = ''
  keyForm.baseUrl = ''
  loadModelsForProvider(provider)
}

const loadModelsForProvider = async (provider) => {
  try {
    const res = await getModelsForProvider(provider)
    availableModels.value = res.data || []
  } catch {
    availableModels.value = []
  }
}

const handleSaveKey = async () => {
  if (!keyFormRef.value) return
  try {
    await keyFormRef.value.validate()
  } catch { return }

  savingKey.value = true
  try {
    await saveApiKey({
      provider: keyForm.provider,
      apiKey: keyForm.apiKey,
      secretKey: keyForm.secretKey || undefined,
      baseUrl: keyForm.baseUrl || undefined,
      modelName: keyForm.modelName,
      setActive: keyForm.setActive
    })
    ElMessage.success(editingKeyId.value ? '密钥已更新' : '密钥已添加')
    keyDialogVisible.value = false
    await loadApiKeys()
  } catch {}
  savingKey.value = false
}

const handleActivate = async (row) => {
  try {
    await activateApiKey(row.id)
    ElMessage.success('已切换为当前密钥')
    await loadApiKeys()
  } catch {}
}

const handleValidate = async (row) => {
  validatingId.value = row.id
  try {
    await validateApiKey(row.id)
    ElMessage.success('API Key验证通过 ')
  } catch (e) {
    if (e.data && e.data.message) {
      ElMessageBox.alert(e.data.message, 'API Key验证失败', {
        confirmButtonText: '知道了',
        type: 'warning'
      })
    }
  }
  validatingId.value = null
  await loadApiKeys()
}

const handleDeleteKey = async (row) => {
  try {
    await deleteApiKey(row.id)
    ElMessage.success('已删除密钥配置')
    await loadApiKeys()
  } catch {}
}

onMounted(async () => {
  try { const res = await getCurrentUser(); user.value = res.data } catch {}
  loadHistory()
  loadApiKeys()
})
</script>
