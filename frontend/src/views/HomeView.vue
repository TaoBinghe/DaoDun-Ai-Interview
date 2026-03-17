<template>
  <div class="min-h-[calc(100vh-64px)] flex flex-col items-center justify-start bg-[#141413] px-4 pt-16 md:pt-20">
    <div class="text-center space-y-6 mb-10">
      <h1 class="text-4xl md:text-6xl font-bold tracking-tight text-white">
        <span class="text-white/90">刀盾AI面试</span>
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
      <p class="text-base md:text-lg text-gray-400 max-w-2xl mx-auto leading-relaxed">
        基于大模型的智能面试官，模拟真实场景，给你可执行的反馈与改进建议。
      </p>
    </div>

    <div class="flex flex-col sm:flex-row gap-4 items-center">
      <router-link to="/resume">
        <el-button type="primary" size="large" class="bg-[#6ef17d]! text-black! border-none! rounded-full! px-8! py-5! text-base! font-bold! hover:opacity-90! transition-all">
          立即开始体验
        </el-button>
      </router-link>
      <router-link to="/profile">
        <el-button size="large" class="bg-transparent! text-white! border-white/20! border! rounded-full! px-8! py-5! text-base! font-bold! hover:bg-white/5! transition-all">
          查看面试记录
        </el-button>
      </router-link>
    </div>

    <!-- 背景装饰 -->
    <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] bg-white/5 rounded-full blur-[120px] -z-10 pointer-events-none"></div>
  </div>
</template>

<script setup lang="ts">
import RotatingText from '../component/RotatingText/RotatingText.vue'
</script>

<style scoped>
:deep(.el-button) {
  height: auto;
}
</style>
