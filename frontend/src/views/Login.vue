<template>
  <div class="auth-bg">
    <!-- 装饰元素 -->
    <div class="bg-shapes">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
      <div class="shape shape-3"></div>
    </div>

    <!-- 卡片容器 -->
    <div class="auth-card" :class="{ 'is-register': isRegister }">
      <!-- 品牌区 -->
      <div class="card-brand">
        <div class="brand-icon">
          <el-icon :size="36"><Monitor /></el-icon>
        </div>
        <h1 class="brand-name">CodeInspector</h1>
        <p class="brand-desc">智能代码审查系统</p>
      </div>

      <!-- 登录表单 -->
      <transition name="slide-fade" mode="out-in">
        <div v-if="!isRegister" key="login" class="card-form">
          <h2 class="form-title">欢迎回来</h2>
          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules"
            size="large" @keyup.enter="handleLogin">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="用户名"
                :prefix-icon="User" clearable />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password"
                placeholder="密码" :prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" round
                @click="handleLogin" class="submit-btn">
                <span v-if="!loading">登 录</span>
              </el-button>
            </el-form-item>
          </el-form>
          <div class="form-switch">
            还没有账号？
            <span class="switch-link" @click="switchToRegister">立即注册</span>
          </div>
        </div>

        <!-- 注册表单 -->
        <div v-else key="register" class="card-form">
          <h2 class="form-title">创建账号</h2>
          <el-form ref="regFormRef" :model="regForm" :rules="regRules"
            size="large" @keyup.enter="handleRegister">
            <el-form-item prop="username">
              <el-input v-model="regForm.username" placeholder="用户名"
                :prefix-icon="User" clearable />
            </el-form-item>
            <el-form-item prop="email">
              <el-input v-model="regForm.email" placeholder="邮箱（选填）"
                :prefix-icon="Message" clearable />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="regForm.password" type="password"
                placeholder="密码（至少6位）" :prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input v-model="regForm.confirmPassword" type="password"
                placeholder="确认密码" :prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="regLoading" round
                @click="handleRegister" class="submit-btn">
                <span v-if="!regLoading">注 册</span>
              </el-button>
            </el-form-item>
          </el-form>
          <div class="form-switch">
            已有账号？
            <span class="switch-link" @click="switchToLogin">返回登录</span>
          </div>
        </div>
      </transition>
    </div>

    <!-- 底部版权 -->
    <div class="auth-footer">© 2026 CodeInspector · 智能代码审查系统</div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { register as apiRegister } from '@/api'
import { ElMessage } from 'element-plus'
import { User, Lock, Message, Monitor } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

// 状态
const isRegister = ref(false)
const loading = ref(false)
const regLoading = ref(false)

// 登录
const loginFormRef = ref(null)
const loginForm = reactive({ username: '', password: '' })
const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await authStore.login(loginForm)
    ElMessage.success('登录成功')
    router.push('/projects')
  } catch {}
  loading.value = false
}

// 注册
const regFormRef = ref(null)
const regForm = reactive({ username: '', email: '', password: '', confirmPassword: '' })
const regRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }],
  confirmPassword: [{
    required: true, validator: (r, v, cb) =>
      v !== regForm.password ? cb('两次密码不一致') : cb(), trigger: 'blur'
  }]
}

const handleRegister = async () => {
  const valid = await regFormRef.value?.validate().catch(() => false)
  if (!valid) return
  regLoading.value = true
  try {
    await apiRegister({ username: regForm.username, password: regForm.password, email: regForm.email })
    ElMessage.success('注册成功，请登录')
    switchToLogin()
  } catch {}
  regLoading.value = false
}

const switchToRegister = () => { isRegister.value = true }
const switchToLogin = () => { isRegister.value = false }
</script>

<style scoped>
.auth-bg {
  min-height: 100vh; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 40%, #0f3460 100%);
  position: relative; overflow: hidden; padding: 20px;
}

/* 背景装饰形状 */
.bg-shapes { position: absolute; inset: 0; pointer-events: none; }
.shape {
  position: absolute; border-radius: 50%;
  background: rgba(255,255,255,0.03); animation: float 20s infinite ease-in-out;
}
.shape-1 { width: 600px; height: 600px; top: -200px; right: -150px; animation-delay: 0s; }
.shape-2 { width: 400px; height: 400px; bottom: -100px; left: -100px; animation-delay: -7s; }
.shape-3 { width: 300px; height: 300px; top: 40%; left: 50%; animation-delay: -14s;
  background: rgba(64,158,255,0.04); }
@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -30px) scale(1.05); }
  66% { transform: translate(-20px, 20px) scale(0.95); }
}

/* 卡片 */
.auth-card {
  background: rgba(255,255,255,0.97); border-radius: 20px;
  box-shadow: 0 25px 80px rgba(0,0,0,0.35);
  backdrop-filter: blur(10px); width: 420px; max-width: 95vw;
  overflow: hidden; z-index: 1; transition: min-height 0.4s ease;
}

/* 品牌区 */
.card-brand { text-align: center; padding: 36px 40px 20px; }
.brand-icon {
  width: 64px; height: 64px; margin: 0 auto 16px; border-radius: 18px;
  background: linear-gradient(135deg, #409EFF, #67C23A);
  display: flex; align-items: center; justify-content: center; color: #fff;
}
.brand-name { font-size: 26px; font-weight: 800; color: #1a1a2e; margin: 0; letter-spacing: -0.5px; }
.brand-desc { font-size: 13px; color: #909399; margin: 6px 0 0; }

/* 表单 */
.card-form { padding: 0 40px 32px; }
.form-title { font-size: 20px; font-weight: 700; color: #303133; margin: 0 0 24px; text-align: center; }
.submit-btn { width: 100%; height: 48px; font-size: 16px; letter-spacing: 4px; }

/* 切换链接 */
.form-switch { text-align: center; font-size: 14px; color: #909399; margin-top: 4px; }
.switch-link {
  color: #409EFF; font-weight: 600; cursor: pointer; margin-left: 2px;
  transition: color 0.2s; text-decoration: underline;
  text-underline-offset: 3px;
}
.switch-link:hover { color: #66b1ff; }

/* 底部 */
.auth-footer {
  position: absolute; bottom: 20px; font-size: 12px; color: rgba(255,255,255,0.3); z-index: 1;
}

/* 过渡动画 */
.slide-fade-enter-active { transition: all 0.35s ease-out; }
.slide-fade-leave-active { transition: all 0.2s ease-in; }
.slide-fade-enter-from { opacity: 0; transform: translateX(30px); }
.slide-fade-leave-to { opacity: 0; transform: translateX(-30px); }

/* Element Plus 输入框覆盖 */
:deep(.el-input__wrapper) { border-radius: 10px; box-shadow: 0 0 0 1px #e4e7ed inset; transition: all 0.2s; }
:deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px #c0c4cc inset; }
:deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 2px rgba(64,158,255,0.3) inset; }
:deep(.el-button--primary) {
  background: linear-gradient(135deg, #409EFF, #66b1ff); border: none;
  transition: all 0.3s;
}
:deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #66b1ff, #409EFF);
  transform: translateY(-1px); box-shadow: 0 6px 20px rgba(64,158,255,0.4);
}
:deep(.el-form-item) { margin-bottom: 20px; }

/* 响应式 */
@media (max-width: 480px) {
  .auth-card { width: 100%; border-radius: 14px; }
  .card-brand { padding: 28px 24px 16px; }
  .card-form { padding: 0 24px 24px; }
  .brand-name { font-size: 22px; }
}
</style>
