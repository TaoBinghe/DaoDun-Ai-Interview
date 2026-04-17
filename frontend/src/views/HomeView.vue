<template>
  <div class="theme-page min-h-[calc(100vh-64px)] flex flex-col items-center justify-start px-4 pt-16 pb-20 md:pt-20">
    <div class="text-center space-y-6 mb-10">
      <h1 class="text-4xl md:text-6xl font-bold tracking-tight theme-title">
        <span class="home-brand">智面未来</span>
        <RotatingText
          :texts="['即时反馈', '简历解析', '能力评估']"
          mainClassName="ml-4 md:ml-6 px-2 sm:px-2 md:px-3 bg-[#6ef17d] text-black overflow-hidden py-0.5 sm:py-1 md:py-2 justify-center rounded-xl inline-flex align-middle"
          :staggerFrom="'last'"
          :initial="{ y: '100%' }"
          :animate="{ y: 0 }"
          :exit="{ y: '-120%' }"
          :staggerDuration="0.02"
          splitLevelClassName="overflow-hidden pb-0.5 sm:pb-1 md:pb-1"
          :transition="{ type: 'spring', damping: 28, stiffness: 420 }"
          :rotationInterval="2500"
        />
      </h1>
      <p class="home-subtitle text-base md:text-lg max-w-2xl mx-auto leading-relaxed">
        基于大模型的智能面试官，模拟真实场景，给你可执行的反馈与改进建议。
      </p>
    </div>

    <div class="flex flex-col sm:flex-row gap-4 items-center">
      <router-link to="/resume">
        <el-button size="large" class="theme-el-btn-primary !rounded-full !border-0 !px-8 !py-5 !text-base !font-bold transition-all">
          立即开始体验
        </el-button>
      </router-link>
      <router-link to="/profile">
        <el-button size="large" class="home-secondary-btn !rounded-full !px-8 !py-5 !text-base !font-bold transition-all">
          查看面试记录
        </el-button>
      </router-link>
    </div>

    <!-- 三张功能卡片 -->
    <div class="w-full max-w-3xl mx-auto mt-16 md:mt-20 grid grid-cols-1 sm:grid-cols-3 gap-4">
      <div class="home-card flex flex-col p-6 rounded-2xl transition-colors">
        <div class="w-10 h-10 mb-4 flex items-center justify-center home-icon">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
            <path d="M8 7h8" />
            <path d="M8 11h8" />
          </svg>
        </div>
        <h3 class="text-lg font-bold theme-title mb-1">Java后端知识库</h3>
        <p class="text-sm theme-text-muted mb-4 flex-1">丰富内容</p>
        <span class="text-xs theme-text-faint">涵盖主流技术栈与面试题</span>
      </div>

      <router-link to="/resume" class="home-card flex flex-col p-6 rounded-2xl transition-colors group">
        <div class="w-10 h-10 mb-4 flex items-center justify-center home-icon">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
            <path d="M12 18v-6" />
            <path d="M9 15h6" />
          </svg>
        </div>
        <h3 class="text-lg font-bold theme-title mb-1">立即导入简历</h3>
        <p class="text-sm theme-text-muted mb-4 flex-1">智能匹配与分析</p>
        <span class="home-cta text-center block w-full py-2.5 rounded-full font-medium group-hover:opacity-90 transition-opacity">
          开始导入
        </span>
      </router-link>

      <div class="home-card flex flex-col p-6 rounded-2xl transition-colors">
        <div class="w-10 h-10 mb-4 flex items-center justify-center home-icon">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
            <path d="M8 10h8" />
            <path d="M8 14h8" />
            <path d="M8 18h4" />
          </svg>
        </div>
        <h3 class="text-lg font-bold theme-title mb-1">笔试场景支持</h3>
        <p class="text-sm theme-text-muted mb-4 flex-1">支持模拟笔试，真实还原</p>
        <span class="text-xs theme-text-faint">在线编程与选择题模拟</span>
      </div>
    </div>

    <!-- 背景装饰 -->
    <div class="home-glow absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] rounded-full blur-[120px] -z-10 pointer-events-none"></div>
  </div>
</template>

<script setup lang="ts">
import RotatingText from '../component/RotatingText/RotatingText.vue'
</script>

<style scoped>
.home-brand {
  color: color-mix(in srgb, var(--app-text) 92%, transparent);
}

.home-subtitle {
  color: var(--app-text-muted);
}

.home-card {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
}

.home-card:hover {
  border-color: var(--app-border-strong);
}

.home-icon {
  color: color-mix(in srgb, var(--app-text) 80%, transparent);
}

.home-secondary-btn {
  background: var(--app-surface);
  color: var(--app-text);
  border: 1px solid var(--app-border);
}

.home-secondary-btn:hover {
  background: var(--app-surface-strong);
}

.home-cta {
  background: var(--app-primary);
  color: var(--app-primary-contrast);
}

.home-glow {
  background: color-mix(in srgb, var(--app-accent) 14%, transparent);
}

:deep(.el-button) {
  height: auto;
}
</style>
