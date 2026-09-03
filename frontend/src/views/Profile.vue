<template>
  <div class="fade-in">
    <h2 style="font-size:22px;font-weight:600;margin-bottom:20px;">个人信息</h2>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- 1. 头像管理 -->
      <el-tab-pane label="头像管理" name="avatar">
        <el-row :gutter="16">
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
              <p style="font-size:12px;color:var(--text-placeholder);margin-top:8px;">支持 JPG/PNG，不超过 2MB</p>
            </div>
          </el-col>
          <el-col :span="16">
            <el-alert title="头像用于个人标识" type="info" :closable="false" show-icon />
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
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getCurrentUser, updateProfile, changePassword, uploadAvatar } from '@/api'
import { ElMessage } from 'element-plus'
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
    localStorage.setItem('user', JSON.stringify(res.data))
    ElMessage.success('保存成功')
  } catch {}
  saveInfoLoading.value = false
}

// ================ 联系方式 ================
const contactForm = reactive({ email: user.value.email || '', phone: user.value.phone || '' })
const contactRules = { email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }] }
const saveContactLoading = ref(false)
const saveContact = async () => {
  saveContactLoading.value = true
  try {
    const res = await updateProfile({ email: contactForm.email, phone: contactForm.phone })
    user.value = res.data; authStore.user = res.data
    localStorage.setItem('user', JSON.stringify(res.data))
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
    localStorage.setItem('user', JSON.stringify(authStore.user))
    ElMessage.success('头像更新成功')
  } catch {}
  uploading.value = false
}

onMounted(async () => {
  try {
    const res = await getCurrentUser()
    user.value = res.data
    contactForm.email = res.data.email || ''
    contactForm.phone = res.data.phone || ''
  } catch {}
})
</script>
