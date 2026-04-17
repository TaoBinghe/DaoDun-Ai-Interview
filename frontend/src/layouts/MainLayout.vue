<template>
  <div class="theme-page min-h-screen relative overflow-x-hidden font-sans">
    <!-- 导航栏 -->
    <nav class="sticky top-0 left-0 w-full z-50 bg-[color:var(--app-overlay)] backdrop-blur-xl">
      <div class="max-w-7xl mx-auto px-8 h-16 grid grid-cols-3 items-center gap-4">
        <!-- 左侧：品牌 -->
        <router-link
          to="/"
          class="flex items-center gap-2.5 justify-self-start text-[var(--app-text)] no-underline transition-opacity hover:opacity-80"
        >
          <img :src="brandLogo" alt="智面未来" class="h-9 w-auto shrink-0 rounded-lg object-contain" width="120" height="36" />
          <span class="text-xl font-medium tracking-tight">智面未来</span>
        </router-link>

        <!-- 中间：导航条目居中 -->
        <div class="hidden md:flex items-center justify-center space-x-8">
          <router-link to="/" class="nav-link" exact-active-class="nav-link-active">首页</router-link>
          <router-link to="/resume" class="nav-link" active-class="nav-link-active">导入简历</router-link>
          <el-dropdown trigger="hover" popper-class="dark-dropdown-popper" placement="bottom">
            <span class="nav-link nav-link-trigger" :class="{ 'nav-link-active': isInterviewActive }">
              AI面试
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="position in interviewPositions"
                  :key="position.value"
                >
                  <router-link
                    :to="{ path: '/interview', query: { position: position.value } }"
                    class="dropdown-link"
                  >
                    {{ position.label }}
                  </router-link>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <router-link to="/forum" class="nav-link" active-class="nav-link-active">论坛</router-link>
          <router-link to="/profile" class="nav-link" active-class="nav-link-active">个人中心</router-link>
        </div>

        <div class="flex items-center justify-end gap-3">
          <el-dropdown trigger="click" popper-class="theme-dropdown-popper" placement="bottom-end">
            <button
              type="button"
              class="theme-toggle-btn inline-flex items-center gap-2 rounded-full px-4 py-2 text-sm font-medium"
            >
              <span class="text-base leading-none">{{ themeIcon }}</span>
              <span>{{ themeLabel }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="option in themeOptions"
                  :key="option.value"
                  :class="{ 'is-selected': themeMode === option.value }"
                  @click="setThemeMode(option.value)"
                >
                  <div class="theme-option-row">
                    <span>{{ option.icon }}</span>
                    <span>{{ option.label }}</span>
                  </div>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <!-- 右侧：未登录时显示登录/注册 -->
          <div v-if="!userStore.isLoggedIn" class="flex items-center justify-end">
            <router-link to="/login">
              <el-button class="theme-el-btn-primary !rounded-full !border-0 !px-6">
                登录 / 注册
              </el-button>
            </router-link>
          </div>
          <div v-else class="justify-self-end"></div>
        </div>
      </div>
    </nav>

    <!-- 内容渲染区 -->
    <main class="relative z-10">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useTheme, type ThemeMode } from '../composables/useTheme'
import brandLogo from '@resouce/logo.png'

const userStore = useUserStore()
const route = useRoute()
const { themeMode, resolvedTheme, setThemeMode } = useTheme()
const interviewPositions = [
  { label: '后端开发', value: 'backend' },
  { label: '前端开发', value: 'frontend' },
  { label: '大模型算法', value: 'llm' }
]
const themeOptions: Array<{ value: ThemeMode; label: string; icon: string }> = [
  { value: 'system', label: '跟随系统', icon: '🖥' },
  { value: 'light', label: '浅色模式', icon: '☀' },
  { value: 'dark', label: '深色模式', icon: '🌙' }
]

const isInterviewActive = computed(() => route.path.startsWith('/interview'))
const themeLabel = computed(() => themeOptions.find((item) => item.value === themeMode.value)?.label ?? '跟随系统')
const themeIcon = computed(() => {
  if (themeMode.value === 'system') {
    return resolvedTheme.value === 'dark' ? '🖥🌙' : '🖥☀'
  }
  return resolvedTheme.value === 'dark' ? '🌙' : '☀'
})

onMounted(() => {
  if (!userStore.user) {
    userStore.fetchUser()
  }
})
</script>

<style scoped>
.nav-link {
  font-size: 0.875rem;
  color: var(--app-text-muted);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.nav-link-trigger {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
}

.nav-link:hover,
.nav-link-active {
  color: var(--app-text);
}

.dropdown-link {
  display: block;
  width: 100%;
  color: inherit;
  text-decoration: none;
}
</style>

<style>
/* 全局样式处理 Teleport 后的下拉菜单 */
.theme-dropdown-popper.el-popper,
.dark-dropdown-popper.el-popper {
  background-color: var(--app-surface) !important;
  border: 1px solid var(--app-border) !important;
  border-radius: 0.75rem !important;
  box-shadow: var(--app-shadow) !important;
}

.theme-dropdown-popper .el-dropdown-menu,
.dark-dropdown-popper .el-dropdown-menu {
  background-color: transparent !important;
  padding: 0.25rem !important;
}

.theme-dropdown-popper .el-dropdown-menu__item,
.dark-dropdown-popper .el-dropdown-menu__item {
  color: var(--app-text-muted) !important;
  border-radius: 0.5rem !important;
  padding: 0.5rem 1rem !important;
  font-size: 0.875rem !important;
}

.theme-dropdown-popper .el-dropdown-menu__item:hover,
.dark-dropdown-popper .el-dropdown-menu__item:hover,
.theme-dropdown-popper .el-dropdown-menu__item.is-selected,
.dark-dropdown-popper .el-dropdown-menu__item.is-selected {
  background-color: var(--app-surface-strong) !important;
  color: var(--app-text) !important;
}

.theme-dropdown-popper .el-popper__arrow::before,
.dark-dropdown-popper .el-popper__arrow::before {
  background-color: var(--app-surface) !important;
  border: 1px solid var(--app-border) !important;
}

.theme-toggle-btn {
  background: var(--app-surface);
  color: var(--app-text);
  border: 1px solid var(--app-border);
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.theme-toggle-btn:hover {
  background: var(--app-surface-strong);
  border-color: var(--app-border-strong);
}

.theme-option-row {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}
</style>
