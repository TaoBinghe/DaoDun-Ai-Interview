<template>
  <div class="relative min-h-[calc(100vh-64px)] bg-[#141413] px-4 py-8 text-[#faf9f5]">
    <div class="mx-auto flex w-full max-w-6xl flex-col gap-6">
      <section class="py-2 text-center">
        <h1 class="text-3xl font-semibold tracking-tight text-[#faf9f5]">个人中心</h1>
        <p class="mt-2 text-sm text-gray-400">管理账户信息、查询历史简历与每日面试活跃度。</p>
      </section>

      <el-row :gutter="24" align="top" class="!m-0">
        <el-col :span="6" class="!pl-0">
          <aside
            class="sidebar-container rounded-2xl border border-[#faf9f5]/8 bg-[#161716] p-4 text-center"
          >
            <h5 class="mb-3 text-xs font-medium tracking-wide text-[#9f9f99]">个人中心菜单</h5>
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
          </aside>
        </el-col>

        <el-col :span="18" class="!pr-0">
          <router-view />
        </el-col>
      </el-row>
    </div>

    <div
      class="pointer-events-none absolute left-1/2 top-1/2 -z-10 h-[800px] w-[800px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#faf9f5]/5 blur-[120px]"
      aria-hidden="true"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Grid, Lock, Setting, User } from '@element-plus/icons-vue'

const route = useRoute()

const navItems = [
  { label: '基本信息', path: '/profile/info', icon: User },
  { label: '安全设置', path: '/profile/security', icon: Lock },
  { label: '消息中心', path: '/profile/messages', icon: Grid },
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
}

:deep(.profile-menu) {
  width: 100%;
}

:deep(.profile-menu .el-menu-item) {
  border-radius: 10px;
  margin: 4px 0;
  color: #c9c9c3;
  justify-content: center;
}

:deep(.profile-menu .el-menu-item:hover) {
  background-color: #faf9f5;
  color: #141413;
}

:deep(.profile-menu .el-menu-item:hover .el-icon) {
  color: #141413;
}

:deep(.profile-menu .el-menu-item.is-active) {
  background-color: #faf9f5;
  color: #141413;
}

:deep(.profile-menu .el-menu-item.is-active .el-icon) {
  color: #141413;
}

:deep(.profile-menu .el-menu-item.is-active:hover) {
  background-color: #faf9f5;
  color: #141413;
}

:deep(.profile-menu .el-menu-item.is-active:hover .el-icon) {
  color: #141413;
}
</style>
