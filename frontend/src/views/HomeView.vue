<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useDark } from '@vueuse/core'
import { Microphone, Document, Reading, DataLine } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const isDark = useDark()
const heroLoaded = ref(false)
const cardsLoaded = ref(false)

onMounted(() => {
  if (!userStore.user) {
    userStore.fetchUser()
  }
  // 分阶段触发动画
  requestAnimationFrame(() => {
    heroLoaded.value = true
  })
  setTimeout(() => {
    cardsLoaded.value = true
  }, 200)
})

// 8pt 网格特性卡片
const features = [
  {
    icon: Microphone,
    title: 'AI 语音面试',
    desc: '实时语音交互，模拟真实面试场景',
    delay: 0
  },
  {
    icon: Document,
    title: '简历智能分析',
    desc: '上传简历后，AI 将针对性提问',
    delay: 1
  },
  {
    icon: Reading,
    title: '知识库验证',
    desc: '验证技术栈掌握程度',
    delay: 2
  },
  {
    icon: DataLine,
    title: '多维评估报告',
    desc: '专业知识与情绪表现全面分析',
    delay: 3
  }
]
</script>

<template>
  <div class="home" :class="{ 'is-dark': isDark }">
    <!-- Hero 区域 -->
    <section class="hero">
      <div class="hero-bg" />
      <div class="hero-content" :class="{ 'is-visible': heroLoaded }">
        <p class="hero-greeting">
          欢迎回来{{ userStore.user ? `，${userStore.user.username}` : '' }}
        </p>
        <h2 class="hero-title">
          智能面试 · 精准提升
        </h2>
        <p class="hero-desc">
          AI 面试官将根据您的简历与知识库，进行针对性提问与评估，
          <br />
          助您在真实面试中脱颖而出。
        </p>
      </div>
    </section>

    <!-- 特性卡片 -->
    <section class="features">
      <div class="features-grid" :class="{ 'is-visible': cardsLoaded }">
        <div
          v-for="(item, index) in features"
          :key="index"
          class="feature-card"
          :style="{ animationDelay: `${item.delay * 80}ms` }"
        >
          <div class="feature-icon">
            <component :is="item.icon" />
          </div>
          <h3 class="feature-title">{{ item.title }}</h3>
          <p class="feature-desc">{{ item.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 底部 CTA 区域 -->
    <section class="cta">
      <div class="cta-card" :class="{ 'is-visible': cardsLoaded }">
        <p class="cta-hint">准备就绪后，可从侧边导航开始体验</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ========== 8pt Grid 变量 ========== */
/* 8, 16, 24, 32, 40, 48, 56, 64, 72, 80, 96, 128 */
.home {
  --space-1: 8px;
  --space-2: 16px;
  --space-3: 24px;
  --space-4: 32px;
  --space-5: 40px;
  --space-6: 48px;
  --space-7: 56px;
  --space-8: 64px;
  --space-10: 80px;
  --space-12: 96px;
  --space-16: 128px;
  --primary: #10b981;
  --primary-hover: #34d399;
  --primary-muted: rgba(16, 185, 129, 0.12);
  --bg: #fafafa;
  --bg-card: #ffffff;
  --text: #111827;
  --text-secondary: #6b7280;
  --border: rgba(0, 0, 0, 0.06);
  min-height: 100vh;
  font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
  background: var(--bg);
  transition: background 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.home.is-dark {
  --bg: #0a0a0a;
  --bg-card: #141414;
  --text: #f9fafb;
  --text-secondary: #9ca3af;
  --border: rgba(255, 255, 255, 0.08);
  --primary-muted: rgba(16, 185, 129, 0.2);
}

/* ========== Hero ========== */
.hero {
  position: relative;
  padding: var(--space-16) var(--space-6) var(--space-12);
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    ellipse 80% 50% at 50% 0%,
    var(--primary-muted) 0%,
    transparent 70%
  );
  pointer-events: none;
}

.hero-content {
  position: relative;
  max-width: 640px;
  margin: 0 auto;
  text-align: center;
  opacity: 0;
  transform: translateY(24px);
  transition: opacity 0.6s cubic-bezier(0.4, 0, 0.2, 1),
    transform 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

.hero-content.is-visible {
  opacity: 1;
  transform: translateY(0);
}

.hero-greeting {
  margin: 0 0 var(--space-2);
  font-size: 0.9375rem;
  color: var(--text-secondary);
  font-weight: 500;
}

.hero-title {
  margin: 0 0 var(--space-4);
  font-size: clamp(2rem, 5vw, 2.75rem);
  font-weight: 700;
  color: var(--text);
  letter-spacing: -0.03em;
  line-height: 1.2;
}

.hero-desc {
  margin: 0;
  font-size: 1rem;
  line-height: 1.7;
  color: var(--text-secondary);
}

/* ========== Features ========== */
.features {
  padding: 0 var(--space-6) var(--space-12);
}

.features-grid {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--space-4);
  opacity: 0;
  transform: translateY(16px);
  transition: opacity 0.5s cubic-bezier(0.4, 0, 0.2, 1) 0.1s,
    transform 0.5s cubic-bezier(0.4, 0, 0.2, 1) 0.1s;
}

.features-grid.is-visible {
  opacity: 1;
  transform: translateY(0);
}

.feature-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: var(--space-5);
  transition: border-color 0.3s, box-shadow 0.3s, transform 0.3s;
  animation: cardFadeIn 0.5s cubic-bezier(0.4, 0, 0.2, 1) both;
}

.feature-card:hover {
  border-color: var(--primary);
  box-shadow: 0 8px 24px rgba(16, 185, 129, 0.08);
  transform: translateY(-2px);
}

.feature-icon {
  width: var(--space-6);
  height: var(--space-6);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary-muted);
  border-radius: 12px;
  margin-bottom: var(--space-3);
  color: var(--primary);
  font-size: 1.25rem;
  transition: background 0.3s, color 0.3s;
}

.feature-card:hover .feature-icon {
  background: var(--primary);
  color: white;
}

.feature-title {
  margin: 0 0 var(--space-2);
  font-size: 1rem;
  font-weight: 600;
  color: var(--text);
}

.feature-desc {
  margin: 0;
  font-size: 0.875rem;
  line-height: 1.5;
  color: var(--text-secondary);
}

@keyframes cardFadeIn {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ========== CTA ========== */
.cta {
  padding: 0 var(--space-6) var(--space-12);
}

.cta-card {
  max-width: 480px;
  margin: 0 auto;
  padding: var(--space-4);
  background: var(--bg-card);
  border: 1px dashed var(--border);
  border-radius: 12px;
  text-align: center;
  opacity: 0;
  transition: opacity 0.5s cubic-bezier(0.4, 0, 0.2, 1) 0.3s;
}

.cta-card.is-visible {
  opacity: 1;
}

.cta-hint {
  margin: 0;
  font-size: 0.875rem;
  color: var(--text-secondary);
}

/* Element Plus 主色覆盖 */
:deep(.el-button--primary) {
  --el-button-bg-color: var(--primary);
  --el-button-border-color: var(--primary);
  --el-button-hover-bg-color: var(--primary-hover);
  --el-button-hover-border-color: var(--primary-hover);
}
</style>
