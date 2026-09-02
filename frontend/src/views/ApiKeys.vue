<template>
  <div class="fade-in">
    <h2 style="font-size:22px;font-weight:600;margin-bottom:20px;">API密钥</h2>

    <div v-loading="apiKeysLoading">
      <el-alert
        title="在这里配置你自己的AI API密钥，审查时将优先使用你的密钥调用AI模型。"
        type="info" :closable="false" show-icon style="margin-bottom:16px;" />
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
        <span style="font-size:14px;color:var(--text-secondary);">已配置 {{ apiKeys.length }} 个密钥</span>
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
          <div style="font-size:12px;color:var(--text-placeholder);margin-top:4px;">
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
          <div style="font-size:12px;color:var(--text-placeholder);margin-top:4px;" v-else-if="!keyForm.modelName">
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
          <div style="font-size:12px;color:var(--text-placeholder);margin-top:4px;">
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
import { ref, reactive, onMounted } from 'vue'
import { getApiKeys, saveApiKey, activateApiKey, validateApiKey, deleteApiKey,
  getModelsForProvider } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

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

onMounted(() => {
  loadApiKeys()
})
</script>
