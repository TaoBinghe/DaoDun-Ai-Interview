<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useDark, useToggle } from '@vueuse/core'
import { Sunny, Moon } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const isDark = useDark()
const toggleDark = useToggle(isDark)

onMounted(() => {
  if (!userStore.user) {
    userStore.fetchUser()
  }
})

const handleLogout = () => {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  userStore.clearUser()
  router.push('/login')
}
</script>

<template>
  <div class="home-container" :class="{ 'is-dark': isDark }">
    <el-header class="app-header">
      <div class="header-left">
        <h2>AI 面试平台控制台</h2>
      </div>
      <div class="header-right">
        <span v-if="userStore.user" class="username">{{ userStore.user.username }}</span>
        <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>

    <el-main class="app-main">
      <el-card class="welcome-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>欢迎回来{{ userStore.user ? `，${userStore.user.username}` : '' }}</span>
          </div>
        </template>
        <div class="welcome-content">
          <p>您已成功登录系统。</p>
          <p>这里是一个简洁大气的示例控制台页面，后续可以在这里添加面试记录、个人能力图谱等功能。</p>
          
          <div class="action-buttons">
            <el-button type="primary" size="large">开始模拟面试</el-button>
            <el-button size="large">查看历史记录</el-button>
          </div>
        </div>
      </el-card>
    </el-main>

    <div class="theme-switch-fixed">
      <el-switch
        v-model="isDark"
        :active-icon="Moon"
        :inactive-icon="Sunny"
        inline-prompt
        style="--el-switch-on-color: #2c2c2c; --el-switch-off-color: #10b981"
      />
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

.home-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f3f4f6;
  transition: background-color 0.3s;
  position: relative;
}

.home-container.is-dark {
  background-color: #121212;
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #ffffff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  padding: 0 2rem;
  height: 64px;
}

.is-dark .app-header {
  background-color: #1e1e1e;
  box-shadow: 0 1px 4px rgba(0,0,0,0.2);
}

.header-left h2 {
  margin: 0;
  color: #10b981;
  font-size: 1.25rem;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.username {
  color: #4b5563;
  font-size: 0.95rem;
}

.is-dark .username {
  color: #9ca3af;
}

.app-main {
  flex: 1;
  padding: 2rem;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.welcome-card {
  border-radius: 12px;
  border: none;
  background: #ffffff;
}

.is-dark .welcome-card {
  background: #1e1e1e;
}

.card-header span {
  font-weight: 600;
  font-size: 1.1rem;
}

.is-dark .card-header span {
  color: #f9fafb;
}

.welcome-content {
  padding: 20px 0;
}

.welcome-content p {
  color: #4b5563;
  line-height: 1.6;
  margin-bottom: 1rem;
}

.is-dark .welcome-content p {
  color: #9ca3af;
}

.action-buttons {
  margin-top: 2rem;
  display: flex;
  gap: 1rem;
}

.theme-switch-fixed {
  position: fixed;
  bottom: 2rem;
  right: 2rem;
  z-index: 1000;
}
</style>