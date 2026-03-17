<template>
  <div class="min-h-screen bg-[#141413] text-[#f5f5f5] selection:bg-white/20 relative overflow-x-hidden font-sans">
    <!-- 导航栏 -->
    <nav class="sticky top-0 left-0 w-full z-50 bg-[#141413]">
      <div class="max-w-7xl mx-auto px-8 h-16 flex items-center justify-between">
        <div class="flex items-center space-x-12">
          <router-link to="/" class="text-xl font-medium tracking-tight hover:opacity-80 transition-opacity cursor-pointer text-[#f5f5f5] no-underline">
            AI Interview
          </router-link>
          
          <div class="hidden md:flex items-center space-x-8">
            <router-link to="/" class="nav-link" active-class="nav-link-active">首页</router-link>
            <router-link to="/resume" class="nav-link" active-class="nav-link-active">导入简历</router-link>
            
            <!-- AI面试下拉框：动态对接接口数据 -->
            <el-dropdown trigger="hover" @command="handlePositionCommand" popper-class="dark-dropdown-popper">
              <span class="nav-link flex items-center cursor-pointer outline-none group">
                AI面试 
                <el-icon class="ml-1 transition-transform duration-200 group-hover:rotate-180">
                  <ArrowDown />
                </el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="p in positions" :key="p.id" :command="p.id">
                    {{ p.name }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="positions.length === 0" disabled>暂无岗位</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            
            <router-link to="/profile" class="nav-link" active-class="nav-link-active">个人中心</router-link>
          </div>
        </div>

        <!-- 右侧用户状态或登录按钮 -->
        <div class="flex items-center space-x-4">
          <template v-if="userStore.isLoggedIn">
            <span class="text-sm text-gray-400">{{ userStore.user?.username }}</span>
            <el-button link class="text-gray-400! hover:text-white!" @click="handleLogout">退出</el-button>
          </template>
          <template v-else>
            <router-link to="/login">
              <el-button type="primary" bg class="bg-white! text-black! border-none! rounded-full! px-6! hover:opacity-90!">
                登录 / 注册
              </el-button>
            </router-link>
          </template>
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import request from '../utils/request'

const router = useRouter()
const userStore = useUserStore()

interface Position {
  id: number
  name: string
  description: string
  sortOrder: number
}

const positions = ref<Position[]>([])

const fetchPositions = async () => {
  try {
    const res = await request.get('/api/position/list') as any
    if (res.code === 200) {
      positions.value = res.data || []
    }
  } catch (error) {
    console.error('Failed to fetch positions:', error)
  }
}

const handlePositionCommand = (id: number) => {
  router.push({ name: 'interview', query: { positionId: id } })
}

const handleLogout = () => {
  userStore.clearUser()
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  router.push('/login')
}

onMounted(() => {
  fetchPositions()
  if (!userStore.user) {
    userStore.fetchUser()
  }
})
</script>

<style scoped>
.nav-link {
  font-size: 0.875rem;
  color: rgb(156 163 175);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}
.nav-link:hover,
.nav-link-active {
  color: rgb(245 245 245);
}

/* 自定义下拉菜单样式以匹配暗黑风格 */
/* 下拉菜单样式已移动到下方的全局 style 块中处理 Teleport 问题 */
</style>

<style>
/* 全局样式处理 Teleport 后的下拉菜单 */
.dark-dropdown-popper.el-popper {
  background-color: #141413 !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  border-radius: 0.75rem !important;
  box-shadow: 0 25px 50px -12px rgb(0 0 0 / 0.5) !important;
}

.dark-dropdown-popper .el-dropdown-menu {
  background-color: transparent !important;
  padding: 0.25rem !important;
}

.dark-dropdown-popper .el-dropdown-menu__item {
  color: rgb(156 163 175) !important;
  border-radius: 0.5rem !important;
  padding: 0.5rem 1rem !important;
  font-size: 0.875rem !important;
}

.dark-dropdown-popper .el-dropdown-menu__item:hover {
  background-color: rgba(255, 255, 255, 0.05) !important;
  color: rgb(245 245 245) !important;
}

.dark-dropdown-popper .el-dropdown-menu__item.is-disabled {
  opacity: 0.5 !important;
  cursor: not-allowed !important;
}

.dark-dropdown-popper .el-popper__arrow::before {
  background-color: #141413 !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
}
</style>
