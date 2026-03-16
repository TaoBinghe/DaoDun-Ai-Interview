<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDark } from '@vueuse/core'
import { ArrowLeft, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const route = useRoute()
const router = useRouter()
const isDark = useDark()

const sessionId = Number(route.params.sessionId)

type ReportStatus = 'GENERATING' | 'READY' | 'FAILED' | 'NOT_STARTED'

interface TopicDetail {
  topic: string
  rating: 'good' | 'fair' | 'poor'
  comment: string
}

interface Report {
  overallScore: number
  overallComment: string
  knowledgeAssessment: {
    strengths: string[]
    weaknesses: string[]
    topicDetails: TopicDetail[]
  }
  emotionAssessment: {
    summary: string
    positives: string[]
    issues: string[]
  }
  recommendations: {
    learning: string[]
    emotional: string[]
  }
}

const status = ref<ReportStatus>('GENERATING')
const report = ref<Report | null>(null)
const isLoading = ref(true)
const pollCount = ref(0)
const maxPolls = 20 // 最多轮询20次（60秒）
let pollTimer: ReturnType<typeof setInterval> | null = null

const fetchEvaluation = async () => {
  try {
    const res: any = await request.get(`/api/interview/sessions/${sessionId}/evaluation`)
    const data = res.data
    status.value = data.status as ReportStatus
    if (data.status === 'READY' && data.report) {
      report.value = data.report
      stopPolling()
    } else if (data.status === 'FAILED') {
      stopPolling()
    } else if (data.status === 'NOT_STARTED') {
      stopPolling()
    }
  } catch (e: any) {
    ElMessage.error('获取评估报告失败：' + (e?.message || '未知错误'))
    stopPolling()
    status.value = 'FAILED'
  } finally {
    isLoading.value = false
  }
}

const startPolling = () => {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    pollCount.value++
    if (pollCount.value > maxPolls) {
      stopPolling()
      return
    }
    await fetchEvaluation()
  }, 3000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const retryGenerate = async () => {
  isLoading.value = true
  status.value = 'GENERATING'
  pollCount.value = 0
  await fetchEvaluation()
  if (status.value === 'GENERATING') {
    startPolling()
  }
}

onMounted(async () => {
  await fetchEvaluation()
  if (status.value === 'GENERATING') {
    startPolling()
  }
})

onUnmounted(() => {
  stopPolling()
})

// 评分相关
const scoreColor = computed(() => {
  const s = report.value?.overallScore ?? 0
  if (s >= 80) return '#67c23a'
  if (s >= 60) return '#e6a23c'
  return '#f56c6c'
})

const scoreLabel = computed(() => {
  const s = report.value?.overallScore ?? 0
  if (s >= 90) return '优秀'
  if (s >= 75) return '良好'
  if (s >= 60) return '一般'
  if (s >= 40) return '较差'
  return '不足'
})

const ratingTagType = (rating: string) => {
  if (rating === 'good') return 'success'
  if (rating === 'fair') return 'warning'
  return 'danger'
}

const ratingLabel = (rating: string) => {
  if (rating === 'good') return '掌握扎实'
  if (rating === 'fair') return '基本掌握'
  return '需加强'
}

const emotionLabelMap: Record<string, string> = {
  anger: '抵触/压力',
  disgust: '反感',
  happy: '自信/放松',
  neutral: '专注',
  sad: '略显低落/思考中',
  surprise: '意外'
}

const formatTimestamp = (ms: number) => {
  const totalSeconds = Math.floor(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
}
</script>

<template>
  <div class="result-page" :class="{ 'is-dark': isDark }">
    <!-- 顶部导航 -->
    <header class="result-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" circle @click="router.push('/')" />
        <span class="header-title">面试评估报告</span>
      </div>
      <div class="header-right">
        <span class="session-label">会话 #{{ sessionId }}</span>
      </div>
    </header>

    <main class="result-main">
      <!-- 加载中状态 -->
      <div v-if="isLoading" class="status-center">
        <el-icon class="spin-icon" :size="48"><RefreshRight /></el-icon>
        <p class="status-text">正在加载评估报告...</p>
      </div>

      <!-- 生成中 -->
      <div v-else-if="status === 'GENERATING'" class="status-center">
        <div class="generating-animation">
          <div class="dot"></div>
          <div class="dot"></div>
          <div class="dot"></div>
        </div>
        <p class="status-title">AI 正在分析你的面试表现</p>
        <p class="status-subtitle">正在综合评估知识积累与情绪表现，请稍候...</p>
        <el-progress
          :percentage="Math.min(pollCount * 5, 95)"
          :stroke-width="6"
          :show-text="false"
          class="progress-bar"
        />
      </div>

      <!-- 失败状态 -->
      <div v-else-if="status === 'FAILED'" class="status-center">
        <el-icon :size="64" color="#f56c6c">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
          </svg>
        </el-icon>
        <p class="status-title">评估报告生成失败</p>
        <p class="status-subtitle">请检查网络连接或稍后重试</p>
        <el-button type="primary" @click="retryGenerate">重新生成</el-button>
      </div>

      <!-- 未开始 -->
      <div v-else-if="status === 'NOT_STARTED'" class="status-center">
        <p class="status-title">面试尚未结束</p>
        <el-button type="primary" @click="router.push('/interview')">返回面试</el-button>
      </div>

      <!-- 报告内容 -->
      <div v-else-if="status === 'READY' && report" class="report-content">

        <!-- 综合评分卡片 -->
        <div class="report-card score-card">
          <div class="score-section">
            <div class="score-circle" :style="{ borderColor: scoreColor }">
              <span class="score-number" :style="{ color: scoreColor }">{{ report.overallScore }}</span>
              <span class="score-max">/100</span>
            </div>
            <div class="score-info">
              <div class="score-badge" :style="{ backgroundColor: scoreColor }">{{ scoreLabel }}</div>
              <p class="overall-comment">{{ report.overallComment }}</p>
            </div>
          </div>
        </div>

        <!-- 知识评估卡片 -->
        <div class="report-card">
          <h3 class="card-title">
            <span class="title-icon knowledge-icon">📚</span>
            知识积累评估
          </h3>

          <div class="assessment-columns">
            <!-- 优点 -->
            <div class="assessment-col">
              <h4 class="col-title strengths-title">表现优异</h4>
              <ul class="assessment-list">
                <li
                  v-for="(item, idx) in report.knowledgeAssessment.strengths"
                  :key="idx"
                  class="assessment-item strength-item"
                >
                  <span class="item-icon">✓</span>
                  <span>{{ item }}</span>
                </li>
                <li v-if="!report.knowledgeAssessment.strengths?.length" class="empty-item">暂无</li>
              </ul>
            </div>

            <!-- 不足 -->
            <div class="assessment-col">
              <h4 class="col-title weaknesses-title">有待提升</h4>
              <ul class="assessment-list">
                <li
                  v-for="(item, idx) in report.knowledgeAssessment.weaknesses"
                  :key="idx"
                  class="assessment-item weakness-item"
                >
                  <span class="item-icon">✗</span>
                  <span>{{ item }}</span>
                </li>
                <li v-if="!report.knowledgeAssessment.weaknesses?.length" class="empty-item">暂无</li>
              </ul>
            </div>
          </div>

          <!-- 考点详情 -->
          <div v-if="report.knowledgeAssessment.topicDetails?.length" class="topic-details">
            <h4 class="section-subtitle">各考点详情</h4>
            <div class="topic-grid">
              <div
                v-for="(detail, idx) in report.knowledgeAssessment.topicDetails"
                :key="idx"
                class="topic-item"
                :class="`topic-${detail.rating}`"
              >
                <div class="topic-header">
                  <span class="topic-name">{{ detail.topic }}</span>
                  <el-tag :type="ratingTagType(detail.rating)" size="small">
                    {{ ratingLabel(detail.rating) }}
                  </el-tag>
                </div>
                <p class="topic-comment">{{ detail.comment }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 情绪评估卡片 -->
        <div class="report-card">
          <h3 class="card-title">
            <span class="title-icon emotion-icon">🎭</span>
            情绪状态评估
          </h3>

          <p class="emotion-summary">{{ report.emotionAssessment.summary }}</p>

          <div class="assessment-columns">
            <!-- 积极表现 -->
            <div class="assessment-col">
              <h4 class="col-title strengths-title">积极表现</h4>
              <ul class="assessment-list">
                <li
                  v-for="(item, idx) in report.emotionAssessment.positives"
                  :key="idx"
                  class="assessment-item strength-item"
                >
                  <span class="item-icon">✓</span>
                  <span>{{ item }}</span>
                </li>
                <li v-if="!report.emotionAssessment.positives?.length" class="empty-item">暂无</li>
              </ul>
            </div>

            <!-- 情绪问题 -->
            <div class="assessment-col">
              <h4 class="col-title weaknesses-title">需要注意</h4>
              <ul class="assessment-list">
                <li
                  v-for="(item, idx) in report.emotionAssessment.issues"
                  :key="idx"
                  class="assessment-item weakness-item"
                >
                  <span class="item-icon">!</span>
                  <span>{{ item }}</span>
                </li>
                <li v-if="!report.emotionAssessment.issues?.length" class="empty-item">情绪状态良好</li>
              </ul>
            </div>
          </div>
        </div>

        <!-- 建议卡片 -->
        <div class="report-card">
          <h3 class="card-title">
            <span class="title-icon advice-icon">💡</span>
            提升建议
          </h3>

          <div class="advice-columns">
            <!-- 学习建议 -->
            <div class="advice-col">
              <h4 class="advice-col-title">
                <span>📖</span> 学习提升
              </h4>
              <ol class="advice-list">
                <li
                  v-for="(item, idx) in report.recommendations.learning"
                  :key="idx"
                  class="advice-item learning-item"
                >
                  {{ item }}
                </li>
              </ol>
            </div>

            <!-- 情绪管理建议 -->
            <div class="advice-col">
              <h4 class="advice-col-title">
                <span>🧘</span> 情绪管理
              </h4>
              <ol class="advice-list">
                <li
                  v-for="(item, idx) in report.recommendations.emotional"
                  :key="idx"
                  class="advice-item emotional-item"
                >
                  {{ item }}
                </li>
              </ol>
            </div>
          </div>
        </div>

        <!-- 底部操作 -->
        <div class="report-actions">
          <el-button size="large" @click="router.push('/')">返回首页</el-button>
          <el-button type="primary" size="large" @click="router.push('/interview')">再次面试</el-button>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.result-page {
  min-height: 100vh;
  background: #f5f7fa;
  color: #303133;
  display: flex;
  flex-direction: column;
}

.result-page.is-dark {
  background: #1a1a2e;
  color: #e5eaf3;
}

/* ── 顶部导航 ── */
.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  position: sticky;
  top: 0;
  z-index: 100;
}

.is-dark .result-header {
  background: #16213e;
  border-color: #2d3748;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
}

.session-label {
  font-size: 13px;
  color: #909399;
  background: #f0f2f5;
  padding: 4px 10px;
  border-radius: 12px;
}

.is-dark .session-label {
  background: #2d3748;
  color: #a0aec0;
}

/* ── 主内容 ── */
.result-main {
  flex: 1;
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

/* ── 状态中间区域 ── */
.status-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
  gap: 16px;
  text-align: center;
}

.status-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.status-subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.status-text {
  font-size: 16px;
  color: #909399;
}

.spin-icon {
  animation: spin 1.5s linear infinite;
  color: #409eff;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ── 生成中动画 ── */
.generating-animation {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.dot {
  width: 12px;
  height: 12px;
  background: #409eff;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out;
}

.dot:nth-child(1) { animation-delay: 0s; }
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.progress-bar {
  width: 300px;
  margin-top: 8px;
}

/* ── 报告卡片 ── */
.report-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.report-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.is-dark .report-card {
  background: #16213e;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
}

/* ── 综合评分 ── */
.score-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.is-dark .score-card {
  background: linear-gradient(135deg, #2d3561 0%, #4a2060 100%);
}

.score-section {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
}

.score-circle {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  border: 4px solid;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  flex-shrink: 0;
}

.score-number {
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
  color: #fff !important;
}

.score-max {
  font-size: 12px;
  color: rgba(255,255,255,0.7);
  margin-top: 2px;
}

.score-info {
  flex: 1;
  min-width: 200px;
}

.score-badge {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 10px;
  background: rgba(255,255,255,0.25);
}

.overall-comment {
  font-size: 15px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.92);
  margin: 0;
}

/* ── 卡片标题 ── */
.card-title {
  font-size: 17px;
  font-weight: 600;
  margin: 0 0 20px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-icon {
  font-size: 20px;
}

/* ── 双列布局 ── */
.assessment-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

@media (max-width: 600px) {
  .assessment-columns {
    grid-template-columns: 1fr;
  }
}

.assessment-col {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 16px;
}

.is-dark .assessment-col {
  background: #0f3460;
}

.col-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px 0;
}

.strengths-title { color: #67c23a; }
.weaknesses-title { color: #f56c6c; }

.assessment-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.assessment-item {
  display: flex;
  gap: 8px;
  font-size: 14px;
  line-height: 1.5;
  align-items: flex-start;
}

.item-icon {
  font-weight: 700;
  flex-shrink: 0;
  margin-top: 1px;
  font-size: 13px;
  width: 16px;
  text-align: center;
}

.strength-item .item-icon { color: #67c23a; }
.weakness-item .item-icon { color: #f56c6c; }

.empty-item {
  font-size: 13px;
  color: #c0c4cc;
  font-style: italic;
}

/* ── 考点详情 ── */
.section-subtitle {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px 0;
  color: #606266;
}

.is-dark .section-subtitle {
  color: #909399;
}

.topic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.topic-item {
  border-radius: 8px;
  padding: 14px;
  border-left: 3px solid;
}

.topic-good {
  background: #f0f9eb;
  border-color: #67c23a;
}

.topic-fair {
  background: #fdf6ec;
  border-color: #e6a23c;
}

.topic-poor {
  background: #fef0f0;
  border-color: #f56c6c;
}

.is-dark .topic-good {
  background: rgba(103, 194, 58, 0.12);
}

.is-dark .topic-fair {
  background: rgba(230, 162, 60, 0.12);
}

.is-dark .topic-poor {
  background: rgba(245, 108, 108, 0.12);
}

.topic-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  gap: 8px;
  flex-wrap: wrap;
}

.topic-name {
  font-size: 14px;
  font-weight: 600;
}

.topic-comment {
  font-size: 13px;
  color: #606266;
  margin: 0;
  line-height: 1.5;
}

.is-dark .topic-comment {
  color: #909399;
}

/* ── 情绪评估 ── */
.emotion-summary {
  font-size: 14px;
  line-height: 1.7;
  color: #606266;
  margin: 0 0 16px 0;
  padding: 12px 16px;
  background: #f8f9fa;
  border-radius: 8px;
  border-left: 3px solid #409eff;
}

.is-dark .emotion-summary {
  background: #0f3460;
  color: #a0aec0;
}

/* ── 建议卡片 ── */
.advice-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

@media (max-width: 600px) {
  .advice-columns {
    grid-template-columns: 1fr;
  }
}

.advice-col {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 16px;
}

.is-dark .advice-col {
  background: #0f3460;
}

.advice-col-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 14px 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.advice-list {
  margin: 0;
  padding: 0 0 0 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.advice-item {
  font-size: 14px;
  line-height: 1.6;
}

.learning-item::marker { color: #409eff; }
.emotional-item::marker { color: #67c23a; }

/* ── 底部操作 ── */
.report-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 8px 0 24px;
}
</style>
