<template>
  <div class="theme-page relative min-h-[calc(100vh-64px)] px-4 py-8">
    <div class="mx-auto flex w-full max-w-6xl flex-col gap-6">
      <section class="py-2 text-center">
        <h1 class="theme-title text-3xl font-semibold tracking-tight">个人中心</h1>
        <p class="mt-2 text-sm theme-text-muted">管理账户信息、历史简历与面试记录及评估报告。</p>
      </section>

      <el-row :gutter="24" align="top" class="!m-0">
        <el-col :span="6" class="!pl-0">
          <aside
            class="sidebar-container rounded-2xl p-4 text-center"
          >
            <h5 class="mb-3 text-xs font-medium tracking-wide theme-text-faint">个人中心菜单</h5>
            <el-menu
              :key="activePath"
              router
              :default-active="activePath"
              class="profile-menu !border-0 !bg-transparent"
            >
              <el-menu-item
                v-for="item in navItems"
                :key="item.path"
                :index="item.path"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </el-menu-item>
            </el-menu>
            <div class="mt-4 border-t border-[#faf9f5]/8 pt-4">
              <el-button
                class="profile-danger-btn w-full !h-10 !justify-center !rounded-xl !border-0 [&_.el-icon]:!text-white"
                @click="handleLogout"
              >
                <el-icon class="mr-1"><SwitchButton /></el-icon>
                退出登录
              </el-button>
            </div>
          </aside>
        </el-col>

        <el-col :span="18" class="!pr-0">
          <router-view />
        </el-col>
      </el-row>
    </div>

    <div
      class="profile-glow pointer-events-none absolute left-1/2 top-1/2 -z-10 h-[800px] w-[800px] -translate-x-1/2 -translate-y-1/2 rounded-full blur-[120px]"
      aria-hidden="true"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Grid, Lock, Setting, SwitchButton, User } from '@element-plus/icons-vue'
import { logout } from '../utils/authSession'

const route = useRoute()

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  logout()
}

const navItems = [
  { label: '基本信息', path: '/profile/info', icon: User },
  { label: '安全设置', path: '/profile/security', icon: Lock },
  { label: '面试历史', path: '/profile/messages', icon: Grid },
  { label: '账号绑定', path: '/profile/binding', icon: Setting }
]

const activePath = computed(() => route.path)

function resetScrollTop() {
  window.scrollTo({ top: 0, behavior: 'auto' })
}

onMounted(() => {
  resetScrollTop()
})

watch(
  () => route.fullPath,
  () => {
    resetScrollTop()
  }
)
</script>

<style scoped>
.sidebar-container {
  position: sticky;
  top: 84px;
  align-self: flex-start;
  width: 100%;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  box-shadow: var(--app-shadow);
}

:deep(.profile-menu) {
  width: 100%;
}

:deep(.profile-menu .el-menu-item) {
  border-radius: 10px;
  margin: 4px 0;
  color: var(--app-text-muted);
  justify-content: center;
}

:deep(.profile-menu .el-menu-item:hover) {
  background-color: var(--app-surface-strong);
  color: var(--app-text);
}

:deep(.profile-menu .el-menu-item:hover .el-icon) {
  color: var(--app-text);
}

:deep(.profile-menu .el-menu-item.is-active) {
  background-color: var(--app-primary);
  color: var(--app-primary-contrast);
}

:deep(.profile-menu .el-menu-item.is-active .el-icon) {
  color: var(--app-primary-contrast);
}

:deep(.profile-menu .el-menu-item.is-active:hover) {
  background-color: var(--app-primary);
  color: var(--app-primary-contrast);
}

:deep(.profile-menu .el-menu-item.is-active:hover .el-icon) {
  color: var(--app-primary-contrast);
}

.profile-danger-btn {
  background: var(--app-danger) !important;
  color: #fff !important;
}

.profile-glow {
  background: color-mix(in srgb, var(--app-accent) 12%, transparent);
}
</style>
