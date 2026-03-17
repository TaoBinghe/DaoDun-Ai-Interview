<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useDark } from '@vueuse/core'
import CardNav from './component/CardNav/CardNav.vue'

const route = useRoute()
const isDark = useDark()

const showNav = computed(() => route.name !== 'login')

// 刀盾Ai面试 logo（SVG data URL）
const logo =
  'data:image/svg+xml,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 32" width="160" height="32"><text x="0" y="24" font-family="system-ui,-apple-system,sans-serif" font-size="20" font-weight="700" fill="%2310b981">刀盾Ai面试</text></svg>'
  )

const navItems = [
  {
    label: '首页',
    bgColor: '#065f46',
    textColor: '#fff',
    links: [{ label: '首页', href: '/', ariaLabel: '前往首页' }],
  },
  {
    label: '个人中心',
    bgColor: '#047857',
    textColor: '#fff',
    links: [{ label: '个人中心', href: '/profile', ariaLabel: '个人中心' }],
  },
  {
    label: '简历上传',
    bgColor: '#059669',
    textColor: '#fff',
    links: [{ label: '简历上传', href: '/resume', ariaLabel: '简历上传' }],
  },
  {
    label: '语音面试',
    bgColor: '#10b981',
    textColor: '#fff',
    links: [{ label: '语音面试', href: '/interview', ariaLabel: '语音面试' }],
  },
]
</script>

<template>
  <div v-if="showNav" class="app-layout" :class="{ 'app-layout--dark': isDark }">
    <CardNav
      :logo="logo"
      logo-alt="刀盾Ai面试"
      :items="navItems"
      :base-color="isDark ? '#141414' : '#fff'"
      :menu-color="isDark ? '#f9fafb' : '#000'"
      button-bg-color="#111"
      button-text-color="#fff"
      ease="power3.out"
    />
    <main class="app-main">
      <router-view />
    </main>
  </div>
  <router-view v-else />
</template>

<style scoped>
.app-layout {
  position: relative;
  min-height: 100vh;
  padding-top: 100px;
  background: #fafafa;
}

.app-layout.app-layout--dark {
  background: #141414;
}

@media (min-width: 768px) {
  .app-layout {
    padding-top: 120px;
  }
}

.app-main {
  flex: 1;
}
</style>

<style>
body {
  margin: 0;
  padding: 0;
  font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
    'Helvetica Neue', Arial, 'Noto Sans', sans-serif;
  background: #fafafa;
}

html.dark body {
  background: #141414;
}
</style>
