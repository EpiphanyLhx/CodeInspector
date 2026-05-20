<template>
  <div class="auth-container">
    <div class="auth-card fade-in">
      <h2>CodeInspector</h2>
      <p class="subtitle">智能代码审查系统</p>
      <el-form ref="formRef" :model="form" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码"
            prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleLogin"
            style="width:100%;height:44px;font-size:16px;background:linear-gradient(135deg,#667eea,#764ba2);border:none">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div style="text-align:center;margin-top:16px;">
        <el-button link type="primary" @click="showRegister = true">注册账号</el-button>
      </div>

      <!-- 注册对话框 -->
      <el-dialog v-model="showRegister" title="注册账号" width="400px">
        <el-form :model="regForm" label-width="80px">
          <el-form-item label="用户名">
            <el-input v-model="regForm.username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="regForm.password" type="password" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="regForm.email" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleRegister">注册</el-button>
            <el-button @click="showRegister = false">取消</el-button>
          </el-form-item>
        </el-form>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { register } from '@/api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  loading.value = true
  try {
    await authStore.login(form)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch {
    // error handled in interceptor
  } finally {
    loading.value = false
  }
}

// 注册
const showRegister = ref(false)
const regForm = reactive({ username: '', password: '', email: '' })
const handleRegister = async () => {
  try {
    await register(regForm)
    ElMessage.success('注册成功，请登录')
    showRegister.value = false
  } catch { /* ignore */ }
}
</script>
