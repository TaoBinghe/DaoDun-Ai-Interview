<template>
  <div class="min-h-screen bg-[#141413] text-[#f5f5f5] selection:bg-white/20 relative overflow-x-hidden font-sans">
    <!-- 导航栏 -->
    <nav class="sticky top-0 left-0 w-full z-50 bg-[#141413]">
      <div class="max-w-7xl mx-auto px-8 h-16 grid grid-cols-3 items-center">
        <!-- 左侧：项目名 -->
        <router-link to="/" class="text-xl font-medium tracking-tight hover:opacity-80 transition-opacity cursor-pointer text-[#f5f5f5] no-underline justify-self-start">
          刀盾ai面试
        </router-link>

        <!-- 中间：导航条目居中 -->
        <div class="hidden md:flex items-center justify-center space-x-8">
          <router-link to="/" class="nav-link" exact-active-class="nav-link-active">首页</router-link>
          <router-link to="/resume" class="nav-link" active-class="nav-link-active">导入简历</router-link>
          <router-link to="/interview" class="nav-link" active-class="nav-link-active">AI面试</router-link>
          <router-link to="/forum" class="nav-link" active-class="nav-link-active">论坛</router-link>
          <router-link to="/profile" class="nav-link" active-class="nav-link-active">个人中心</router-link>
        </div>

        <!-- 右侧：未登录时显示登录/注册 -->
        <div v-if="!userStore.isLoggedIn" class="flex items-center justify-end">
          <router-link to="/login">
            <el-button type="primary" bg class="bg-white! text-black! border-none! rounded-full! px-6! hover:opacity-90!">
              登录 / 注册
            </el-button>
          </router-link>
        </div>
        <div v-else class="justify-self-end"></div>
      </div>
    </nav>

    <!-- 内容渲染区 -->
    <main class="relative z-10">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()

onMounted(() => {
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

.dark-dropdown-popper .el-popper__arrow::before {
  background-color: #141413 !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
}
</style>
