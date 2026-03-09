<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useDark, useToggle } from '@vueuse/core'
import { User, Lock, Message, Key, Sunny, Moon } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const isDark = useDark()
const toggleDark = useToggle(isDark)

const isRegister = ref(false)
const loginType = ref('password')
const loading = ref(false)

const pwdForm = reactive({
  username: '',
  password: '',
  rememberMe: false
})

const emailForm = reactive({
  email: '',
  code: '',
  rememberMe: false
})

const regForm = reactive({
  email: '',
  code: '',
  username: '',
  password: '',
  confirmPassword: ''
})

const passwordCriteria = computed(() => {
  const pwd = regForm.password
  return {
    length: pwd.length >= 5 && pwd.length <= 16,
    hasUpper: /[A-Z]/.test(pwd),
    hasLower: /[a-z]/.test(pwd),
    hasNumber: /[0-9]/.test(pwd),
    match: regForm.confirmPassword === pwd && pwd !== ''
  }
})

const passwordStrength = computed(() => {
  const criteria = passwordCriteria.value
  let score = 0
  if (criteria.length) score++
  if (criteria.hasUpper) score++
  if (criteria.hasLower) score++
  if (criteria.hasNumber) score++
  return score
})

const strengthText = computed(() => {
  const score = passwordStrength.value
  if (score === 0) return ''
  if (score <= 2) return '弱'
  if (score === 3) return '中'
  return '强'
})

const strengthColor = computed(() => {
  const score = passwordStrength.value
  if (score <= 2) return '#f87171'
  if (score === 3) return '#fbbf24'
  return '#10b981'
})

const countDown = ref(0)

const sendCode = async (email: string, type: 'register' | 'login') => {
  if (!email) {
    ElMessage.warning('请输入邮箱')
    return
  }
  const url = type === 'register' ? '/api/auth/send-register-code' : '/api/auth/send-login-code'
  try {
    await request.post(url, { email })
    ElMessage.success('验证码发送成功')
    countDown.value = 60
    const timer = setInterval(() => {
      countDown.value--
      if (countDown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error) {
    // Error handled by interceptor
  }
}

const handlePwdLogin = async () => {
  if (!pwdForm.username || !pwdForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res: any = await request.post('/api/auth/login/password', pwdForm)
    localStorage.setItem('accessToken', res.data.accessToken)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (error) {
    // error
  } finally {
    loading.value = false
  }
}

const handleEmailLogin = async () => {
  if (!emailForm.email || !emailForm.code) {
    ElMessage.warning('请输入邮箱和验证码')
    return
  }
  loading.value = true
  try {
    const res: any = await request.post('/api/auth/login/email', emailForm)
    localStorage.setItem('accessToken', res.data.accessToken)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (error) {
    // error
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!regForm.email || !regForm.code || !regForm.username || !regForm.password || !regForm.confirmPassword) {
    ElMessage.warning('请填写完整注册信息')
    return
  }
  if (regForm.password !== regForm.confirmPassword) {
    ElMessage.warning('两次输入密码不一致')
    return
  }
  if (passwordStrength.value < 4) {
    ElMessage.warning('密码未达标，请根据提示修改')
    return
  }
  loading.value = true
  try {
    await request.post('/api/auth/register', {
      email: regForm.email,
      code: regForm.code,
      username: regForm.username,
      password: regForm.password
    })
    ElMessage.success('注册成功，请登录')
    isRegister.value = false
    loginType.value = 'password'
  } catch (error) {
    // error
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container" :class="{ 'is-dark': isDark }">
    <div class="theme-switch">
      <el-switch
        v-model="isDark"
        :active-icon="Moon"
        :inactive-icon="Sunny"
        inline-prompt
        style="--el-switch-on-color: #2c2c2c; --el-switch-off-color: #10b981"
      />
    </div>

    <div class="login-box">
      <div class="brand-section">
        <div class="logo-circle">
          <el-icon :size="32" color="#fff"><Message /></el-icon>
        </div>
        <h1>AI 模拟面试平台</h1>
        <p>成就你的下一次优秀面试</p>
      </div>

      <el-card class="login-card" shadow="hover">
        <transition name="fade-transform" mode="out-in">
          <!-- 登录视图 -->
          <div v-if="!isRegister" key="login">
            <div class="login-type-switch">
              <div class="segmented-control">
                <div
                  class="segmented-control__item"
                  :class="{ active: loginType === 'password' }"
                  @click="loginType = 'password'"
                >
                  密码登录
                </div>
                <div
                  class="segmented-control__item"
                  :class="{ active: loginType === 'email' }"
                  @click="loginType = 'email'"
                >
                  邮箱登录
                </div>
                <div
                  class="segmented-control__glider"
                  :style="{ transform: `translateX(${loginType === 'password' ? '0' : '100'}%)` }"
                ></div>
              </div>
            </div>

            <div v-show="loginType === 'password'">
              <el-form :model="pwdForm" size="large" class="mt-4">
                <el-form-item>
                  <el-input v-model="pwdForm.username" placeholder="请输入用户名" :prefix-icon="User" />
                </el-form-item>
                <el-form-item>
                  <el-input
                    v-model="pwdForm.password"
                    type="password"
                    placeholder="请输入密码"
                    :prefix-icon="Lock"
                    show-password
                    @keyup.enter="handlePwdLogin"
                  />
                </el-form-item>
                <el-form-item>
                  <el-checkbox v-model="pwdForm.rememberMe">记住我 (14天免登录)</el-checkbox>
                </el-form-item>
                <el-button type="primary" class="w-full" :loading="loading" @click="handlePwdLogin">登录</el-button>
              </el-form>
            </div>

            <div v-show="loginType === 'email'">
              <el-form :model="emailForm" size="large" class="mt-4">
                <el-form-item>
                  <el-input v-model="emailForm.email" placeholder="请输入注册邮箱" :prefix-icon="Message" />
                </el-form-item>
                <el-form-item>
                  <div class="flex-row">
                    <el-input v-model="emailForm.code" placeholder="请输入6位验证码" :prefix-icon="Key" />
                    <el-button
                      type="primary"
                      plain
                      class="ml-2"
                      :disabled="countDown > 0"
                      @click="sendCode(emailForm.email, 'login')"
                    >
                      {{ countDown > 0 ? `${countDown}s 后重发` : '获取验证码' }}
                    </el-button>
                  </div>
                </el-form-item>
                <el-form-item>
                  <el-checkbox v-model="emailForm.rememberMe">记住我 (14天免登录)</el-checkbox>
                </el-form-item>
                <el-button type="primary" class="w-full" :loading="loading" @click="handleEmailLogin">登录</el-button>
              </el-form>
            </div>

            <div class="action-links">
              <span class="text-link" @click="isRegister = true">没有账号？立即注册</span>
            </div>
          </div>

          <!-- 注册视图 -->
          <div v-else key="register">
            <h2 class="form-title">注册账号</h2>
            <el-form :model="regForm" size="large" class="mt-4">
              <el-form-item>
                <el-input v-model="regForm.username" placeholder="请输入用户名 (5-10位)" :prefix-icon="User" />
              </el-form-item>
              <el-form-item>
                <el-input v-model="regForm.email" placeholder="请输入有效邮箱" :prefix-icon="Message" />
              </el-form-item>
              <el-form-item>
                <div class="flex-row">
                  <el-input v-model="regForm.code" placeholder="6位验证码" :prefix-icon="Key" />
                  <el-button
                    type="primary"
                    plain
                    class="ml-2"
                    :disabled="countDown > 0"
                    @click="sendCode(regForm.email, 'register')"
                  >
                    {{ countDown > 0 ? `${countDown}s 后重发` : '获取验证码' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item>
                <el-input
                  v-model="regForm.password"
                  type="password"
                  placeholder="设置登录密码"
                  :prefix-icon="Lock"
                  show-password
                />
                <div v-show="regForm.password" class="password-feedback">
                  <div class="strength-bar">
                    <div
                      class="strength-progress"
                      :style="{ width: (passwordStrength / 4) * 100 + '%', backgroundColor: strengthColor }"
                    ></div>
                  </div>
                  <div class="strength-text" :style="{ color: strengthColor }">密码强度: {{ strengthText }}</div>
                </div>
              </el-form-item>
              <el-form-item>
                <el-input
                  v-model="regForm.confirmPassword"
                  type="password"
                  placeholder="确认登录密码"
                  :prefix-icon="Lock"
                  show-password
                />
                <div v-show="regForm.confirmPassword" class="password-feedback">
                  <div class="match-text" :style="{ color: passwordCriteria.match ? '#10b981' : '#f87171' }">
                    {{ passwordCriteria.match ? '密码匹配' : '密码不一致' }}
                  </div>
                </div>
              </el-form-item>
              <el-button type="primary" class="w-full" :loading="loading" @click="handleRegister">立即注册</el-button>
            </el-form>
            <div class="action-links">
              <span class="text-link" @click="isRegister = false">已有账号？去登录</span>
            </div>
          </div>
        </transition>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
/* 覆盖 Element Plus 的主色调为绿色 */
:deep(.el-button--primary) {
  --el-button-bg-color: #10b981;
  --el-button-border-color: #10b981;
  --el-button-hover-bg-color: #34d399;
  --el-button-hover-border-color: #34d399;
}
:deep(.el-button--primary.is-plain) {
  --el-button-bg-color: #ecfdf5;
  --el-button-text-color: #10b981;
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: #10b981;
  --el-button-border-color: #a7f3d0;
}
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #10b981 inset;
}

/* 去除浏览器自动填充的默认背景，与表单背景一致 */
:deep(.el-input__wrapper input:-webkit-autofill),
:deep(.el-input__wrapper input:-webkit-autofill:hover),
:deep(.el-input__wrapper input:-webkit-autofill:focus),
:deep(.el-input__wrapper input:-webkit-autofill:active),
:deep(.el-input__wrapper .el-input__inner:-webkit-autofill),
:deep(.el-input__wrapper .el-input__inner:-webkit-autofill:hover),
:deep(.el-input__wrapper .el-input__inner:-webkit-autofill:focus),
:deep(.el-input__wrapper .el-input__inner:-webkit-autofill:active) {
  -webkit-box-shadow: 0 0 0 1000px #ffffff inset;
  box-shadow: 0 0 0 1000px #ffffff inset;
  transition: background-color 5000s ease-in-out 0s;
}

.is-dark :deep(.el-input__wrapper input:-webkit-autofill),
.is-dark :deep(.el-input__wrapper input:-webkit-autofill:hover),
.is-dark :deep(.el-input__wrapper input:-webkit-autofill:focus),
.is-dark :deep(.el-input__wrapper input:-webkit-autofill:active),
.is-dark :deep(.el-input__wrapper .el-input__inner:-webkit-autofill),
.is-dark :deep(.el-input__wrapper .el-input__inner:-webkit-autofill:hover),
.is-dark :deep(.el-input__wrapper .el-input__inner:-webkit-autofill:focus),
.is-dark :deep(.el-input__wrapper .el-input__inner:-webkit-autofill:active) {
  -webkit-box-shadow: 0 0 0 1000px #1e1e1e inset;
  box-shadow: 0 0 0 1000px #1e1e1e inset;
}
:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: #10b981;
  border-color: #10b981;
}
:deep(.el-checkbox__input.is-checked + .el-checkbox__label) {
  color: #10b981;
}

/* 按钮组主色调覆盖 */
:deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background-color: #10b981;
  border-color: #10b981;
  box-shadow: -1px 0 0 0 #10b981;
}
:deep(.el-radio-button__inner:hover) {
  color: #10b981;
}

/* 自定义分段控制开关 (Segmented Control) */
.segmented-control {
  position: relative;
  display: flex;
  width: 100%;
  background-color: #f3f4f6;
  border-radius: 10px;
  padding: 4px;
  user-select: none;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.05);
}

.is-dark .segmented-control {
  background-color: #2c2c2c;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.2);
}

.segmented-control__item {
  flex: 1;
  padding: 10px 0;
  font-size: 0.95rem;
  font-weight: 500;
  text-align: center;
  color: #6b7280;
  cursor: pointer;
  z-index: 1;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.is-dark .segmented-control__item {
  color: #9ca3af;
}

.segmented-control__item.active {
  color: #ffffff;
}

.segmented-control__glider {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  background-color: #10b981;
  border-radius: 8px;
  box-shadow: 0 4px 10px rgba(16, 185, 129, 0.3);
  transition: transform 0.4s cubic-bezier(0.18, 0.89, 0.32, 1.28);
}

/* 动画效果（仅用于登录/注册视图切换） */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateY(15px);
}
.fade-transform-leave-to {
  opacity: 0;
  transform: translateY(-15px);
}

.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f3f4f6;
  transition: all 0.5s ease;
  position: relative;
}

.login-container.is-dark {
  background-color: #121212;
}

.theme-switch {
  position: absolute;
  bottom: 2rem;
  right: 2rem;
}

.login-box {
  width: 100%;
  max-width: 420px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.brand-section {
  text-align: center;
  margin-bottom: 2rem;
}

.logo-circle {
  width: 64px;
  height: 64px;
  background-color: #10b981;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 1rem;
  box-shadow: 0 4px 14px rgba(16, 185, 129, 0.4);
}

.brand-section h1 {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: #111827;
  transition: color 0.3s;
}

.is-dark .brand-section h1 {
  color: #f9fafb;
}

.brand-section p {
  color: #6b7280;
  margin: 0;
  font-size: 0.9rem;
  transition: color 0.3s;
}

.is-dark .brand-section p {
  color: #9ca3af;
}

.login-card {
  width: 100%;
  border-radius: 16px;
  border: none;
  background: #ffffff;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
  transition: all 0.5s ease;
}

.is-dark .login-card {
  background: #1e1e1e;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3), 0 8px 10px -6px rgba(0, 0, 0, 0.3);
}

.mt-4 {
  margin-top: 1.5rem;
}

.w-full {
  width: 100%;
}

.flex-row {
  display: flex;
  align-items: center;
  width: 100%;
}

.ml-2 {
  margin-left: 0.5rem;
}

.login-type-switch {
  display: flex;
  justify-content: center;
  margin-bottom: 0.5rem;
}

.action-links {
  margin-top: 1.5rem;
  text-align: center;
}

.text-link {
  font-size: 0.9rem;
  color: #10b981;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.3s;
  padding: 4px 8px;
  border-radius: 4px;
}

.text-link:hover {
  color: #34d399;
  background-color: rgba(16, 185, 129, 0.05);
}

.is-dark .text-link:hover {
  background-color: rgba(16, 185, 129, 0.1);
}

.form-title {
  text-align: center;
  margin-top: 0;
  margin-bottom: 1.5rem;
  font-size: 1.3rem;
  font-weight: 600;
  color: #111827;
  transition: color 0.3s;
}

.is-dark .form-title {
  color: #f9fafb;
}

.password-feedback {
  width: 100%;
  margin-top: 8px;
  font-size: 0.8rem;
}

.strength-bar {
  height: 4px;
  background-color: #e5e7eb;
  border-radius: 2px;
  margin-bottom: 4px;
  overflow: hidden;
}

.is-dark .strength-bar {
  background-color: #374151;
}

.strength-progress {
  height: 100%;
  transition: all 0.3s ease;
}

.strength-text {
  font-weight: 500;
  margin-bottom: 4px;
}

.match-text {
  font-weight: 500;
}
</style>