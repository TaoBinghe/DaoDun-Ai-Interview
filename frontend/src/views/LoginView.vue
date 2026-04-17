<template>
  <div class="theme-page flex min-h-screen flex-col items-center justify-center px-4">
    <router-link
      to="/"
      class="mb-10 flex items-center gap-2.5 text-[var(--app-text)] no-underline transition-opacity hover:opacity-80"
    >
      <img :src="brandLogo" alt="智面未来" class="h-9 w-auto shrink-0 rounded-lg object-contain" width="120" height="36" />
      <span class="text-xl font-medium tracking-tight">智面未来</span>
    </router-link>

    <div class="login-shell relative z-10 w-full max-w-md rounded-4xl p-8 md:p-12">

      <!-- ========== 登录 ========== -->
      <template v-if="activeTab === 'login'">
        <!-- 登录方式：账号密码 / 邮箱验证码 -->
        <div class="flex gap-4 mb-6">
          <button
            type="button"
            :class="['login-mode-btn flex-1 py-2 rounded-lg text-sm font-medium transition-colors', loginMode === 'password' ? 'is-active' : '']"
            @click="loginMode = 'password'; resetLoginForm()"
          >
            账号密码
          </button>
          <button
            type="button"
            :class="['login-mode-btn flex-1 py-2 rounded-lg text-sm font-medium transition-colors', loginMode === 'email' ? 'is-active' : '']"
            @click="loginMode = 'email'; resetLoginForm()"
          >
            邮箱验证码
          </button>
        </div>

        <Transition name="login-mode" mode="out-in">
          <!-- 账号密码登录 -->
          <form v-if="loginMode === 'password'" key="password" class="space-y-4" @submit.prevent="handlePasswordLogin">
            <div>
              <input
                v-model="loginUsername"
                type="text"
                placeholder="用户名"
                class="input-dark w-full"
                required
                autocomplete="username"
              />
            </div>
            <div>
              <input
                v-model="loginPassword"
                type="password"
                placeholder="密码"
                class="input-dark w-full"
                required
                autocomplete="current-password"
              />
            </div>
            <label class="flex cursor-pointer items-center gap-2 text-sm theme-text-muted">
              <input v-model="rememberMe" type="checkbox" class="login-checkbox rounded" />
              记住我
            </label>
            <button
              type="submit"
              :disabled="loading"
              class="btn-primary w-full py-4"
            >
              <span v-if="loading" class="animate-spin rounded-full h-5 w-5 border-2 border-black border-t-transparent mr-2 inline-block"></span>
              登录
            </button>
          </form>

          <!-- 邮箱验证码登录 -->
          <div v-else key="email" class="space-y-4">
            <template v-if="loginEmailStep === 1">
              <input
                v-model="loginEmail"
                type="email"
                placeholder="请输入邮箱"
                class="input-dark w-full"
                @keyup.enter="sendLoginCode"
              />
              <button
                type="button"
                :disabled="loading || !isValidEmail(loginEmail)"
                class="btn-primary w-full py-4"
                @click="sendLoginCode"
              >
                <span v-if="loading" class="animate-spin rounded-full h-5 w-5 border-2 border-black border-t-transparent mr-2 inline-block"></span>
                获取验证码
              </button>
            </template>
            <template v-else>
              <p class="text-sm theme-text-muted">验证码已发送至 <span class="theme-title">{{ loginEmail }}</span></p>
            <div class="grid grid-cols-6 gap-2">
              <input
                v-for="(_, i) in 6"
                :key="i"
                :ref="(el) => setCodeRef(el, i)"
                v-model="loginCodeArray[i]"
                type="text"
                inputmode="numeric"
                maxlength="1"
                class="code-input w-full aspect-square rounded-xl p-0 text-center text-lg font-bold outline-none transition-colors"
                @input="onCodeInput(loginCodeArray, $event, i)"
                @keydown.backspace="onCodeBackspace(loginCodeArray, $event, i)"
              />
            </div>
              <button
                type="button"
                class="login-link text-sm"
                @click="loginEmailStep = 1; resetCodeArray(loginCodeArray)"
              >
                更换邮箱
              </button>
              <button
                type="button"
                :disabled="loading || loginCodeArray.join('').length < 6"
                class="btn-primary w-full py-4"
                @click="handleEmailLogin"
              >
                <span v-if="loading" class="animate-spin rounded-full h-5 w-5 border-2 border-black border-t-transparent mr-2 inline-block"></span>
                登录
              </button>
            </template>
          </div>
        </Transition>

        <div class="mt-6 text-center text-sm theme-text-faint">
          没有账号？
          <button type="button" class="login-link underline underline-offset-4" @click="activeTab = 'register'; resetForm()">
            立即注册
          </button>
        </div>
      </template>

      <!-- ========== 注册 ========== -->
      <template v-else>
        <!-- Step 1: 填写邮箱、用户名、密码、确认密码 -->
        <form v-if="registerStep === 1" class="space-y-4" @submit.prevent="sendRegisterCode">
          <input
            v-model="registerEmail"
            type="email"
            placeholder="请输入邮箱"
            class="input-dark w-full"
            required
            autocomplete="email"
          />
          <input
            v-model="registerUsername"
            type="text"
            placeholder="用户名（5~10 个字符，中英文、数字）"
            class="input-dark w-full"
            required
            minlength="5"
            maxlength="10"
            autocomplete="username"
          />
          <input
            v-model="registerPassword"
            type="password"
            placeholder="密码（5~16 位，含大小写字母和数字）"
            class="input-dark w-full"
            required
            minlength="5"
            maxlength="16"
            autocomplete="new-password"
          />
          <div v-if="registerPassword" class="mt-2 space-y-2">
            <div class="flex items-center gap-2">
              <div class="strength-bar">
                <div
                  class="strength-seg"
                  :class="registerPasswordStrength.level >= 0 ? `strength-on strength-${registerPasswordStrength.label}` : ''"
                ></div>
                <div
                  class="strength-seg"
                  :class="registerPasswordStrength.level >= 1 ? `strength-on strength-${registerPasswordStrength.label}` : ''"
                ></div>
                <div
                  class="strength-seg"
                  :class="registerPasswordStrength.level >= 2 ? `strength-on strength-${registerPasswordStrength.label}` : ''"
                ></div>
              </div>
              <span class="text-xs theme-text-muted">强度：{{ registerPasswordStrength.label }}</span>
            </div>
          </div>
          <div>
            <input
              v-model="registerConfirmPassword"
              type="password"
              placeholder="确认密码"
              class="input-dark w-full"
              required
              minlength="5"
              maxlength="16"
              autocomplete="new-password"
            />
            <p
              v-if="registerPassword && registerConfirmPassword && !isRegisterPasswordMatch"
              class="mt-1 text-xs text-[var(--app-danger)]"
            >
              两次输入的密码不一致
            </p>
            <p
              v-else-if="registerPassword && registerConfirmPassword && isRegisterPasswordMatch"
              class="mt-1 text-xs text-[var(--app-success)]"
            >
              两次密码一致
            </p>
          </div>
          <button
            type="submit"
            :disabled="loading || !isRegisterStep1Valid"
            class="btn-primary w-full py-4"
          >
            <span v-if="loading" class="animate-spin rounded-full h-5 w-5 border-2 border-black border-t-transparent mr-2 inline-block"></span>
            获取验证码
          </button>
        </form>

        <!-- Step 2: 输入验证码 -->
        <form v-else class="space-y-4" @submit.prevent="handleRegister">
          <p class="text-sm theme-text-muted">验证码已发送至 <span class="theme-title">{{ registerEmail }}</span></p>
          <div class="grid grid-cols-6 gap-2">
            <input
              v-for="(_, i) in 6"
              :key="i"
              :ref="(el) => setCodeRef(el, i)"
              v-model="registerCodeArray[i]"
              type="text"
              inputmode="numeric"
              maxlength="1"
              class="code-input w-full aspect-square rounded-xl p-0 text-center text-lg font-bold outline-none transition-colors"
              @input="onCodeInput(registerCodeArray, $event, i)"
              @keydown.backspace="onCodeBackspace(registerCodeArray, $event, i)"
            />
          </div>
          <button
            type="button"
            class="login-link text-sm"
            @click="registerStep = 1; resetCodeArray(registerCodeArray)"
          >
            返回编辑
          </button>
          <button
            type="submit"
            :disabled="loading || registerCodeArray.join('').length < 6"
            class="btn-primary w-full py-4"
          >
            <span v-if="loading" class="animate-spin rounded-full h-5 w-5 border-2 border-black border-t-transparent mr-2 inline-block"></span>
            注册
          </button>
        </form>
        <div class="mt-6 text-center text-sm theme-text-faint">
          已有账号？
          <button type="button" class="login-link underline underline-offset-4" @click="activeTab = 'login'; resetForm()">
            返回登录
          </button>
        </div>
      </template>

      <p class="mt-8 text-center text-[10px] leading-relaxed theme-text-faint">
        继续即表示同意 AI Interview 的
        <a href="#" class="login-link underline">隐私政策</a>。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, computed } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import brandLogo from '@resouce/logo.png'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref<'login' | 'register'>('login')
const loading = ref(false)

// 登录：账号密码
const loginMode = ref<'password' | 'email'>('password')
const loginUsername = ref('')
const loginPassword = ref('')
const rememberMe = ref(true)

// 登录：邮箱验证码
const loginEmail = ref('')
const loginEmailStep = ref(1)
const loginCodeArray = reactive<string[]>(['', '', '', '', '', ''])

// 注册
const registerStep = ref(1)
const registerEmail = ref('')
const registerCodeArray = reactive<string[]>(['', '', '', '', '', ''])
const registerUsername = ref('')
const registerPassword = ref('')
const registerConfirmPassword = ref('')

const codeRefs = ref<(HTMLInputElement | null)[]>([])

function getPasswordStrength(password: string) {
  const hasLower = /[a-z]/.test(password)
  const hasUpper = /[A-Z]/.test(password)
  const hasDigit = /\d/.test(password)
  const hasSpecial = /[^A-Za-z0-9]/.test(password)
  const longEnough = password.length >= 8

  const score = [longEnough, hasLower, hasUpper, hasDigit, hasSpecial].filter(Boolean).length
  const level = score <= 2 ? 0 : score <= 4 ? 1 : 2
  const label = level === 0 ? '弱' : level === 1 ? '中' : '强'

  return { score, level, label }
}

const registerPasswordStrength = computed(() => getPasswordStrength(registerPassword.value))
const isRegisterPasswordMatch = computed(() => {
  if (!registerPassword.value || !registerConfirmPassword.value) return true
  return registerPassword.value === registerConfirmPassword.value
})

const isRegisterStep1Valid = computed(() => {
  const username = registerUsername.value.trim()
  const password = registerPassword.value
  const strength = registerPasswordStrength.value

  const usernameOk = username.length >= 5 && username.length <= 10
  const emailOk = isValidEmail(registerEmail.value)
  // 发送验证码前沿用后端要求的基础校验（5~16 + 大小写 + 数字），并加上确认密码一致
  const passwordOk =
    password.length >= 5 &&
    password.length <= 16 &&
    /[a-z]/.test(password) &&
    /[A-Z]/.test(password) &&
    /\d/.test(password)

  const confirmOk = !!registerConfirmPassword.value && isRegisterPasswordMatch.value

  // strength 只是展示，不作为硬门槛（硬门槛仍是接口文档里的规则）
  void strength
  return emailOk && usernameOk && passwordOk && confirmOk
})

function setCodeRef(el: unknown, i: number) {
  if (el instanceof HTMLInputElement) codeRefs.value[i] = el
}

function isValidEmail(s: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s)
}

function resetForm() {
  loading.value = false
  resetLoginForm()
  registerStep.value = 1
  registerEmail.value = ''
  registerUsername.value = ''
  registerPassword.value = ''
  registerConfirmPassword.value = ''
  resetCodeArray(registerCodeArray)
}

function resetLoginForm() {
  loginEmailStep.value = 1
  loginEmail.value = ''
  loginUsername.value = ''
  loginPassword.value = ''
  resetCodeArray(loginCodeArray)
}

function resetCodeArray(arr: string[]) {
  for (let i = 0; i < 6; i++) arr[i] = ''
}

function onCodeInput(arr: string[], e: Event, index: number) {
  const val = (e.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 1)
  arr[index] = val
  if (val && index < 5) {
    nextTick(() => codeRefs.value[index + 1]?.focus())
  }
}

function onCodeBackspace(arr: string[], e: KeyboardEvent, index: number) {
  if (!arr[index] && index > 0) {
    nextTick(() => codeRefs.value[index - 1]?.focus())
  }
}

// 账号密码登录：POST /api/auth/login/password
async function handlePasswordLogin() {
  if (!loginUsername.value.trim() || !loginPassword.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/api/auth/login/password', {
      username: loginUsername.value.trim(),
      password: loginPassword.value,
      rememberMe: rememberMe.value
    }) as { code: number; msg?: string; data?: { accessToken: string; refreshToken: string; userId: number; username: string; email: string } }
    if (res.code === 200 && res.data) {
      saveAuthAndRedirect(res.data)
      ElMessage.success('登录成功')
    }
  } catch {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    loading.value = false
  }
}

// 发送登录验证码：POST /api/auth/send-login-code
async function sendLoginCode() {
  if (!isValidEmail(loginEmail.value)) {
    ElMessage.warning('请输入正确的邮箱')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/api/auth/send-login-code', { email: loginEmail.value }) as { code: number; msg?: string }
    if (res.code === 200) {
      ElMessage.success('验证码已发送，请查收邮件')
      loginEmailStep.value = 2
      nextTick(() => codeRefs.value[0]?.focus())
    }
  } catch {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    loading.value = false
  }
}

// 邮箱验证码登录：POST /api/auth/login/email
async function handleEmailLogin() {
  const code = loginCodeArray.join('')
  if (code.length < 6) return
  loading.value = true
  try {
    const res = await request.post('/api/auth/login/email', {
      email: loginEmail.value,
      code,
      rememberMe: rememberMe.value
    }) as { code: number; msg?: string; data?: { accessToken: string; refreshToken: string; userId: number; username: string; email: string } }
    if (res.code === 200 && res.data) {
      saveAuthAndRedirect(res.data)
      ElMessage.success('登录成功')
    }
  } catch {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    loading.value = false
  }
}

// 发送注册验证码：POST /api/auth/send-register-code
async function sendRegisterCode() {
  if (!isValidEmail(registerEmail.value)) {
    ElMessage.warning('请输入正确的邮箱')
    return
  }
  const username = registerUsername.value.trim()
  if (username.length < 5 || username.length > 10) {
    ElMessage.warning('用户名为 5~10 个字符')
    return
  }
  if (registerPassword.value.length < 5 || registerPassword.value.length > 16) {
    ElMessage.warning('密码为 5~16 位，且须包含英文大小写和数字')
    return
  }
  if (!/[a-z]/.test(registerPassword.value) || !/[A-Z]/.test(registerPassword.value) || !/\d/.test(registerPassword.value)) {
    ElMessage.warning('密码须包含英文大小写字母和数字')
    return
  }
  if (registerPassword.value !== registerConfirmPassword.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/api/auth/send-register-code', { email: registerEmail.value }) as { code: number; msg?: string }
    if (res.code === 200) {
      ElMessage.success('验证码已发送，请查收邮件')
      registerStep.value = 2
      nextTick(() => codeRefs.value[0]?.focus())
    }
  } catch {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    loading.value = false
  }
}

// 用户注册：POST /api/auth/register
async function handleRegister() {
  const code = registerCodeArray.join('')
  if (code.length < 6) {
    ElMessage.warning('请输入 6 位验证码')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/api/auth/register', {
      email: registerEmail.value,
      code,
      username: registerUsername.value.trim(),
      password: registerPassword.value
    }) as { code: number; msg?: string }
    if (res.code === 200) {
      ElMessage.success('注册成功，请登录')
      activeTab.value = 'login'
      loginMode.value = 'password'
      resetForm()
    }
  } catch {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    loading.value = false
  }
}

function saveAuthAndRedirect(data: { accessToken: string; refreshToken: string; userId: number; username: string; email: string }) {
  localStorage.setItem('accessToken', data.accessToken)
  localStorage.setItem('refreshToken', data.refreshToken)
  userStore.setUser({
    userId: data.userId,
    username: data.username,
    email: data.email
  })
  router.push('/')
}
</script>

<style scoped>
.input-dark {
  background-color: var(--app-surface-soft);
  border: 1px solid var(--app-border);
  border-radius: 0.75rem;
  padding: 0.75rem 1rem;
  color: var(--app-text);
  outline: none;
  transition: border-color 0.2s;
}
.input-dark::placeholder {
  color: var(--app-text-faint);
}
.input-dark:focus {
  border-color: color-mix(in srgb, var(--app-accent) 45%, transparent);
}

.input-dark:-webkit-autofill,
.input-dark:-webkit-autofill:hover,
.input-dark:-webkit-autofill:focus,
.input-dark:-webkit-autofill:active {
  -webkit-box-shadow: 0 0 0 1000px var(--app-surface-soft) inset !important;
  -webkit-text-fill-color: var(--app-text) !important;
  transition: background-color 5000s ease-in-out 0s;
}

.login-shell {
  background: var(--app-bg);
  border: 1px solid var(--app-border);
  box-shadow: var(--app-shadow);
}

.login-mode-btn {
  color: var(--app-text-faint);
}

.login-mode-btn:hover {
  color: var(--app-text-muted);
}

.login-mode-btn.is-active {
  background: var(--app-surface-strong);
  color: var(--app-text);
}

.btn-primary {
  background-color: var(--app-primary);
  color: var(--app-primary-contrast);
  font-weight: 600;
  border-radius: 0.75rem;
  border: none;
  transition: opacity 0.2s;
}
.btn-primary:hover:not(:disabled) {
  background-color: var(--app-primary-hover);
  opacity: 0.95;
}
.btn-primary:disabled {
  opacity: 1;
  background-color: var(--app-primary);
  color: var(--app-primary-contrast);
  cursor: not-allowed;
}

.strength-bar {
  display: flex;
  gap: 0.35rem;
  width: 6.5rem;
}

.strength-seg {
  flex: 1;
  height: 0.35rem;
  border-radius: 999px;
  background: var(--app-border);
}

.strength-on.strength-弱 {
  background: rgba(239, 68, 68, 0.55);
}
.strength-on.strength-中 {
  background: rgba(234, 179, 8, 0.55);
}
.strength-on.strength-强 {
  background: rgba(16, 185, 129, 0.55);
}

.code-input {
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
  color: var(--app-text);
}

.code-input:focus {
  border-color: color-mix(in srgb, var(--app-accent) 45%, transparent);
}

.login-link {
  color: var(--app-text-muted);
  transition: color 0.2s ease;
}

.login-link:hover {
  color: var(--app-text);
}

.login-checkbox {
  border: 1px solid var(--app-border-strong);
  background: var(--app-surface-soft);
  color: var(--app-primary);
}

.login-checkbox:focus {
  outline: 2px solid color-mix(in srgb, var(--app-accent) 35%, transparent);
  outline-offset: 1px;
}

.login-mode-enter-active,
.login-mode-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.login-mode-enter-from {
  opacity: 0;
  transform: translateY(-8px);
}
.login-mode-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
