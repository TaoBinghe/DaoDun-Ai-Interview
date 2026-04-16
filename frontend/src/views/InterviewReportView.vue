<template>
  <div class="min-h-[calc(100vh-64px)] bg-[#141413] text-[#faf9f5] px-4 py-10">
    <div class="max-w-4xl mx-auto">

      <!-- 加载状态 -->
      <div v-if="loading" class="flex flex-col items-center justify-center py-32 gap-6">
        <div class="w-16 h-16 border-4 border-[#6ef17d]/30 border-t-[#6ef17d] rounded-full animate-spin"></div>
        <p class="text-gray-400 text-lg">{{ loadingText }}</p>
      </div>

      <!-- 失败状态 -->
      <div v-else-if="failed" class="flex flex-col items-center justify-center py-32 gap-4">
        <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10" />
          <line x1="15" y1="9" x2="9" y2="15" />
          <line x1="9" y1="9" x2="15" y2="15" />
        </svg>
        <p class="text-gray-400 text-lg">{{ failedMessage }}</p>
        <button @click="router.push({ name: 'interview' })" class="mt-4 px-6 py-2 bg-[#1f1e1d] border border-[#faf9f5]/10 rounded-lg text-sm hover:bg-[#2b2a27] transition-colors">
          返回面试
        </button>
      </div>

      <!-- 报告内容 -->
      <template v-if="!loading && !failed && report">

        <!-- A. 报告头部 -->
        <section class="mb-10">
          <div class="flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
            <div>
              <h1 class="text-3xl font-bold mb-3">{{ positionName }}</h1>
              <div class="flex flex-wrap gap-x-6 gap-y-2 text-sm text-gray-400">
                <span class="flex items-center gap-1.5">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                  面试时长：{{ duration }}
                </span>
                <span class="flex items-center gap-1.5">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                  {{ formattedDate }}
                </span>
                <span class="flex items-center gap-1.5">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="8.5" cy="7" r="4"/></svg>
                  面试轮次：{{ turnCount }} 轮
                </span>
              </div>
            </div>
            <!-- 分数环 -->
            <div class="flex flex-col items-center gap-2 shrink-0">
              <div class="relative w-24 h-24">
                <svg viewBox="0 0 100 100" class="w-full h-full -rotate-90">
                  <circle cx="50" cy="50" r="42" fill="none" stroke="#2b2a27" stroke-width="8" />
                  <circle cx="50" cy="50" r="42" fill="none" :stroke="scoreColor" stroke-width="8"
                    stroke-linecap="round" :stroke-dasharray="scoreCircle" />
                </svg>
                <div class="absolute inset-0 flex items-center justify-center">
                  <span class="text-2xl font-bold" :style="{ color: scoreColor }">{{ report.overallScore }}</span>
                </div>
              </div>
              <span class="text-xs px-3 py-1 rounded-full font-medium" :style="{ backgroundColor: scoreColor + '20', color: scoreColor }">
                {{ report.ratingLevel || ratingLevelFallback }}
              </span>
            </div>
          </div>
        </section>

        <!-- B. 面试官评价 -->
        <section class="mb-8">
          <h2 class="section-title">面试官评价</h2>
          <div class="card p-6">
            <p class="text-[#faf9f5]/90 leading-relaxed mb-6">{{ report.overallComment }}</p>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-6" v-if="report.knowledgeAssessment">
              <div>
                <h3 class="text-sm font-semibold text-[#6ef17d] mb-3 flex items-center gap-2">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                  亮点
                </h3>
                <ul class="space-y-2">
                  <li v-for="(s, i) in report.knowledgeAssessment.strengths" :key="i" class="text-sm text-gray-300 flex items-start gap-2">
                    <span class="w-1.5 h-1.5 rounded-full bg-[#6ef17d] mt-1.5 shrink-0"></span>
                    {{ s }}
                  </li>
                </ul>
              </div>
              <div>
                <h3 class="text-sm font-semibold text-[#f59e0b] mb-3 flex items-center gap-2">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                  待改进
                </h3>
                <ul class="space-y-2">
                  <li v-for="(w, i) in report.knowledgeAssessment.weaknesses" :key="i" class="text-sm text-gray-300 flex items-start gap-2">
                    <span class="w-1.5 h-1.5 rounded-full bg-[#f59e0b] mt-1.5 shrink-0"></span>
                    {{ w }}
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </section>

        <!-- C. 综合能力分析（雷达图） -->
        <section class="mb-8" v-if="report.abilityScores">
          <h2 class="section-title">综合能力分析</h2>
          <div class="card p-6">
            <div class="grid grid-cols-1 gap-8 xl:grid-cols-[minmax(0,1.15fr)_minmax(320px,0.85fr)] xl:items-stretch">
              <div class="flex w-full min-h-[320px] items-center justify-center xl:min-h-0">
                <div
                  ref="radarChartRef"
                  class="aspect-square w-full max-w-[min(100%,22rem)] sm:max-w-[min(100%,26rem)] xl:max-w-[min(100%,36rem)]"
                />
              </div>
              <div class="space-y-4">
                <article
                  v-for="item in abilityAnalysisItems"
                  :key="item.key"
                  class="rounded-xl border border-[#faf9f5]/6 bg-[#141413] px-4 py-3"
                >
                  <div class="flex items-center justify-between gap-4">
                    <h3 class="text-sm font-semibold text-[#faf9f5]">{{ item.label }}</h3>
                    <span class="text-sm font-bold" :style="{ color: abilityScoreColor(item.rawScore) }">
                      {{ item.displayScore }}/10
                    </span>
                  </div>
                  <p class="mt-2 text-sm leading-6 text-[#faf9f5]/65">{{ item.comment }}</p>
                </article>
              </div>
            </div>
          </div>
        </section>

        <!-- D. 技术技能评分 -->
        <section class="mb-8" v-if="report.knowledgeAssessment?.topicDetails?.length">
          <h2 class="section-title">技术技能评分</h2>
          <div class="card p-6 space-y-5">
            <div v-for="(topic, i) in report.knowledgeAssessment.topicDetails" :key="i">
              <div class="flex items-center justify-between mb-1.5">
                <span class="text-sm font-medium text-[#faf9f5]/90">{{ topic.topic }}</span>
                <span class="text-sm font-bold" :style="{ color: topicScoreColor(topic.score) }">{{ topic.score }}/10</span>
              </div>
              <div class="w-full h-2 bg-[#2b2a27] rounded-full overflow-hidden">
                <div class="h-full rounded-full transition-all duration-700" :style="{ width: (topic.score * 10) + '%', background: topicBarGradient(topic.score) }"></div>
              </div>
              <p class="text-xs text-gray-500 mt-1">{{ topic.comment }}</p>
            </div>
          </div>
        </section>

        <!-- E. 面试题目解析记录 -->
        <section class="mb-8" v-if="report.questionAnalysis?.length">
          <h2 class="section-title">面试题目解析记录</h2>
          <div class="space-y-4">
            <div v-for="(q, i) in report.questionAnalysis" :key="i" class="card overflow-hidden">
              <!-- 题目头部 -->
              <button
                @click="toggleQuestion(i)"
                class="w-full flex items-center justify-between p-5 text-left hover:bg-[#faf9f5]/2 transition-colors"
              >
                <div class="flex items-center gap-3">
                  <span class="w-7 h-7 rounded-lg bg-[#6ef17d]/15 text-[#6ef17d] flex items-center justify-center text-sm font-bold shrink-0">
                    {{ q.questionIndex }}
                  </span>
                  <span class="text-sm font-medium text-[#faf9f5]/90 line-clamp-1">{{ q.questionContent }}</span>
                </div>
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                  class="text-gray-500 transition-transform duration-200 shrink-0" :class="{ 'rotate-180': expandedQuestions.has(i) }">
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
              </button>

              <!-- 展开内容 -->
              <div v-show="expandedQuestions.has(i)" class="border-t border-[#faf9f5]/5 p-5 space-y-5">
                <!-- 题目原题 -->
                <div>
                  <h4 class="qa-label">题目原题</h4>
                  <p class="qa-text">{{ q.questionContent }}</p>
                </div>

                <!-- 追问内容 -->
                <div v-if="q.followUpContents?.length">
                  <h4 class="qa-label">追问内容</h4>
                  <ul class="space-y-1.5">
                    <li v-for="(f, fi) in q.followUpContents" :key="fi" class="qa-text flex items-start gap-2">
                      <span class="text-[#6ef17d] shrink-0">{{ fi + 1 }}.</span>
                      {{ f }}
                    </li>
                  </ul>
                </div>

                <!-- 面试者回答 -->
                <div>
                  <h4 class="qa-label">面试者回答</h4>
                  <p class="qa-text bg-[#141413] rounded-lg p-3">{{ q.candidateAnswer }}</p>
                </div>

                <!-- 优点 / 待改进 -->
                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div v-if="q.strengths?.length">
                    <h4 class="qa-label text-[#6ef17d]">回答优点</h4>
                    <ul class="space-y-1.5">
                      <li v-for="(s, si) in q.strengths" :key="si" class="text-sm text-gray-300 flex items-start gap-2">
                        <span class="w-1.5 h-1.5 rounded-full bg-[#6ef17d] mt-1.5 shrink-0"></span>
                        {{ s }}
                      </li>
                    </ul>
                  </div>
                  <div v-if="q.improvements?.length">
                    <h4 class="qa-label text-[#f59e0b]">待改进</h4>
                    <ul class="space-y-1.5">
                      <li v-for="(imp, ii) in q.improvements" :key="ii" class="text-sm text-gray-300 flex items-start gap-2">
                        <span class="w-1.5 h-1.5 rounded-full bg-[#f59e0b] mt-1.5 shrink-0"></span>
                        {{ imp }}
                      </li>
                    </ul>
                  </div>
                </div>

                <!-- 面试官点评 -->
                <div v-if="q.interviewerComment">
                  <h4 class="qa-label">面试官点评</h4>
                  <p class="qa-text">{{ q.interviewerComment }}</p>
                </div>

                <!-- 解题思路和参考答案（可折叠） -->
                <div v-if="q.solutionApproach || q.referenceAnswer" class="border-t border-[#faf9f5]/5 pt-4">
                  <button @click="toggleAnswer(i)" class="text-sm text-[#6ef17d] hover:text-[#5edb6b] transition-colors flex items-center gap-1.5">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/>
                    </svg>
                    {{ expandedAnswers.has(i) ? '收起参考答案' : '查看解题思路与参考答案' }}
                  </button>
                  <div v-show="expandedAnswers.has(i)" class="mt-3 space-y-3">
                    <div v-if="q.solutionApproach">
                      <h4 class="qa-label">解题思路</h4>
                      <p class="qa-text bg-[#141413] rounded-lg p-3">{{ q.solutionApproach }}</p>
                    </div>
                    <div v-if="q.referenceAnswer">
                      <h4 class="qa-label">参考答案</h4>
                      <p class="qa-text bg-[#141413] rounded-lg p-3 whitespace-pre-wrap">{{ q.referenceAnswer }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- F. 学习规划与建议 -->
        <section class="mb-8" v-if="report.recommendations">
          <h2 class="section-title">学习规划与建议</h2>
          <div class="card p-6">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div v-if="report.recommendations.learning?.length">
                <h3 class="text-sm font-semibold text-[#6ef17d] mb-3 flex items-center gap-2">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                  学习提升方向
                </h3>
                <ul class="space-y-2">
                  <li v-for="(l, i) in report.recommendations.learning" :key="i" class="text-sm text-gray-300 flex items-start gap-2">
                    <span class="text-[#6ef17d] font-bold shrink-0">{{ i + 1 }}.</span>
                    {{ l }}
                  </li>
                </ul>
              </div>
              <div v-if="report.recommendations.emotional?.length">
                <h3 class="text-sm font-semibold text-[#a78bfa] mb-3 flex items-center gap-2">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                  情绪管理建议
                </h3>
                <ul class="space-y-2">
                  <li v-for="(e, i) in report.recommendations.emotional" :key="i" class="text-sm text-gray-300 flex items-start gap-2">
                    <span class="text-[#a78bfa] font-bold shrink-0">{{ i + 1 }}.</span>
                    {{ e }}
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </section>

        <!-- G. 情绪评估 -->
        <section class="mb-8" v-if="report.emotionAssessment">
          <h2 class="section-title">情绪状态评估</h2>
          <div class="card p-6">
            <p class="text-sm text-[#faf9f5]/80 leading-relaxed mb-4">{{ report.emotionAssessment.summary }}</p>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div v-if="report.emotionAssessment.positives?.length">
                <h3 class="text-xs font-semibold text-[#6ef17d] mb-2">积极表现</h3>
                <ul class="space-y-1.5">
                  <li v-for="(p, i) in report.emotionAssessment.positives" :key="i" class="text-sm text-gray-300 flex items-start gap-2">
                    <span class="w-1.5 h-1.5 rounded-full bg-[#6ef17d] mt-1.5 shrink-0"></span>
                    {{ p }}
                  </li>
                </ul>
              </div>
              <div v-if="report.emotionAssessment.issues?.length">
                <h3 class="text-xs font-semibold text-[#f59e0b] mb-2">需关注</h3>
                <ul class="space-y-1.5">
                  <li v-for="(issue, i) in report.emotionAssessment.issues" :key="i" class="text-sm text-gray-300 flex items-start gap-2">
                    <span class="w-1.5 h-1.5 rounded-full bg-[#f59e0b] mt-1.5 shrink-0"></span>
                    {{ issue }}
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </section>

        <!-- 底部返回 -->
        <div class="flex justify-center pt-4 pb-8">
          <button @click="router.push({ name: 'interview' })" class="px-8 py-3 bg-[#1f1e1d] border border-[#faf9f5]/10 rounded-xl text-sm font-medium hover:bg-[#2b2a27] transition-colors">
            返回面试
          </button>
        </div>

      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { RadarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, RadarComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import request from '../utils/request'
import {
  MOCK_INTERVIEW_SESSION_ID,
  mockInterviewReport,
  mockInterviewSessionSummary
} from '../mocks/interviewReportMock'

echarts.use([RadarChart, TitleComponent, TooltipComponent, RadarComponent, CanvasRenderer])

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const loadingText = ref('正在加载评估报告...')
const failed = ref(false)
const failedMessage = ref('')

const positionName = ref('')
const startTime = ref('')
const endTime = ref('')
const turnCount = ref(0)

interface Report {
  overallScore: number
  overallComment: string
  ratingLevel: string
  abilityScores: {
    expressionAbility: number
    adaptability: number
    responseSpeed: number
    logicAbility: number
    professionalKnowledge: number
    technicalDepth: number
  } | null
  knowledgeAssessment: {
    strengths: string[]
    weaknesses: string[]
    topicDetails: { topic: string; rating: string; score: number; comment: string }[]
  } | null
  emotionAssessment: {
    summary: string
    positives: string[]
    issues: string[]
  } | null
  questionAnalysis: {
    questionIndex: number
    questionContent: string
    followUpContents: string[]
    candidateAnswer: string
    strengths: string[]
    improvements: string[]
    interviewerComment: string
    solutionApproach: string
    referenceAnswer: string
  }[] | null
  recommendations: {
    learning: string[]
    emotional: string[]
  } | null
}

const report = ref<Report | null>(null)

const expandedQuestions = ref<Set<number>>(new Set())
const expandedAnswers = ref<Set<number>>(new Set())

const toggleQuestion = (idx: number) => {
  const s = new Set(expandedQuestions.value)
  if (s.has(idx)) s.delete(idx)
  else s.add(idx)
  expandedQuestions.value = s
}

const toggleAnswer = (idx: number) => {
  const s = new Set(expandedAnswers.value)
  if (s.has(idx)) s.delete(idx)
  else s.add(idx)
  expandedAnswers.value = s
}

const duration = computed(() => {
  if (!startTime.value || !endTime.value) return '--'
  const start = new Date(startTime.value).getTime()
  const end = new Date(endTime.value).getTime()
  const mins = Math.round((end - start) / 60000)
  if (mins < 1) return '不足1分钟'
  if (mins < 60) return `${mins}分钟`
  return `${Math.floor(mins / 60)}小时${mins % 60}分钟`
})

const formattedDate = computed(() => {
  if (!startTime.value) return '--'
  const d = new Date(startTime.value)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
})

const scoreColor = computed(() => {
  const s = report.value?.overallScore ?? 0
  if (s >= 90) return '#6ef17d'
  if (s >= 70) return '#6ef17d'
  if (s >= 50) return '#f59e0b'
  if (s >= 30) return '#f97316'
  return '#ef4444'
})

const scoreCircle = computed(() => {
  const s = report.value?.overallScore ?? 0
  const circumference = 2 * Math.PI * 42
  const filled = (s / 100) * circumference
  return `${filled} ${circumference}`
})

const ratingLevelFallback = computed(() => {
  const s = report.value?.overallScore ?? 0
  if (s >= 90) return '优秀'
  if (s >= 70) return '良好'
  if (s >= 50) return '合格'
  if (s >= 30) return '待提升'
  return '不合格'
})

const topicScoreColor = (score: number) => {
  if (score >= 8) return '#6ef17d'
  if (score >= 5) return '#f59e0b'
  return '#ef4444'
}

const topicBarGradient = (score: number) => {
  if (score >= 8) return 'linear-gradient(90deg, #6ef17d, #5edb6b)'
  if (score >= 5) return 'linear-gradient(90deg, #f59e0b, #eab308)'
  return 'linear-gradient(90deg, #ef4444, #f97316)'
}

const abilityScoreColor = (score100: number) => {
  if (score100 >= 80) return '#6ef17d'
  if (score100 >= 60) return '#facc15'
  if (score100 >= 40) return '#fb923c'
  return '#f87171'
}

const buildAbilityComment = (label: string, score100: number) => {
  const score10 = Math.max(1, Math.round(score100 / 10))
  if (score10 >= 8) {
    return `${label}表现较强，回答较完整，已经能较稳定地支撑面试交流。`
  }
  if (score10 >= 6) {
    return `${label}整体尚可，但在细节展开和说服力上还有提升空间。`
  }
  if (score10 >= 4) {
    return `${label}偏弱，回答容易停留在表层，需要增强条理性和具体性。`
  }
  return `${label}明显不足，当前在该维度还难以支撑高质量面试表现。`
}

const abilityAnalysisItems = computed(() => {
  const scores = report.value?.abilityScores
  if (!scores) return []
  const items = [
    { key: 'expressionAbility', label: '表达能力', rawScore: scores.expressionAbility },
    { key: 'adaptability', label: '应变能力', rawScore: scores.adaptability },
    { key: 'responseSpeed', label: '应答能力', rawScore: scores.responseSpeed },
    { key: 'logicAbility', label: '逻辑能力', rawScore: scores.logicAbility },
    { key: 'professionalKnowledge', label: '岗位契合度', rawScore: scores.professionalKnowledge },
    { key: 'technicalDepth', label: '技术深度', rawScore: scores.technicalDepth }
  ]

  return items.map((item) => ({
    ...item,
    displayScore: Math.max(1, Math.round(item.rawScore / 10)),
    comment: buildAbilityComment(item.label, item.rawScore)
  }))
})

// Radar Chart
const radarChartRef = ref<HTMLDivElement | null>(null)
let chartInstance: echarts.ECharts | null = null

const initRadarChart = () => {
  if (!radarChartRef.value || !report.value?.abilityScores) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(radarChartRef.value)

  const scores = report.value.abilityScores
  chartInstance.setOption({
    radar: {
      center: ['50%', '50%'],
      radius: '78%',
      indicator: [
        { name: '表达能力', max: 100 },
        { name: '应变能力', max: 100 },
        { name: '应答能力', max: 100 },
        { name: '逻辑能力', max: 100 },
        { name: '专业知识', max: 100 },
        { name: '技术深度', max: 100 }
      ],
      shape: 'polygon',
      splitNumber: 4,
      axisName: { color: '#9ca3af', fontSize: 13 },
      splitLine: { lineStyle: { color: 'rgba(250,249,245,0.08)' } },
      splitArea: { areaStyle: { color: ['rgba(110,241,125,0.02)', 'rgba(110,241,125,0.04)', 'rgba(110,241,125,0.02)', 'rgba(110,241,125,0.04)'] } },
      axisLine: { lineStyle: { color: 'rgba(250,249,245,0.1)' } }
    },
    series: [{
      type: 'radar',
      data: [{
        value: [
          scores.expressionAbility,
          scores.adaptability,
          scores.responseSpeed,
          scores.logicAbility,
          scores.professionalKnowledge,
          scores.technicalDepth
        ],
        areaStyle: { color: 'rgba(110,241,125,0.15)' },
        lineStyle: { color: '#6ef17d', width: 2 },
        itemStyle: { color: '#6ef17d' },
        symbol: 'circle',
        symbolSize: 7
      }]
    }]
  })
  nextTick(() => {
    chartInstance?.resize()
  })
}

const handleResize = () => chartInstance?.resize()

// Polling for evaluation report
let pollTimer: ReturnType<typeof setTimeout> | null = null

const fetchEvaluation = async () => {
  const sessionId = route.params.sessionId as string
  if (!sessionId) {
    failed.value = true
    failedMessage.value = '无效的会话ID'
    loading.value = false
    return
  }

  if (Number(sessionId) === MOCK_INTERVIEW_SESSION_ID) {
    positionName.value = mockInterviewSessionSummary.positionName
    startTime.value = mockInterviewSessionSummary.startedAt
    endTime.value = mockInterviewSessionSummary.endedAt ?? ''
    report.value = mockInterviewReport
    loading.value = false
    await nextTick()
    initRadarChart()
    return
  }

  try {
    const res = await request.get(`/api/interview/sessions/${sessionId}/evaluation`) as any
    if (res.code !== 200) {
      failed.value = true
      failedMessage.value = res.msg || '获取报告失败'
      loading.value = false
      return
    }

    const data = res.data
    positionName.value = data.positionName || '未知岗位'
    startTime.value = data.startTime || ''
    endTime.value = data.endTime || ''

    if (data.status === 'READY' && data.report) {
      report.value = data.report
      loading.value = false
      await nextTick()
      initRadarChart()
      return
    }

    if (data.status === 'GENERATING') {
      loadingText.value = '评估报告生成中，请稍候...'
      pollTimer = setTimeout(fetchEvaluation, 3000)
      return
    }

    if (data.status === 'FAILED') {
      failed.value = true
      failedMessage.value = data.message || '评估报告生成失败'
      loading.value = false
      return
    }

    if (data.status === 'INSUFFICIENT_DATA') {
      failed.value = true
      failedMessage.value = data.message || '对话轮次过少，无法生成完整评估报告'
      loading.value = false
      return
    }

    if (data.status === 'NOT_STARTED') {
      failed.value = true
      failedMessage.value = '该面试尚未结束，无法查看评估报告'
      loading.value = false
      return
    }
  } catch (err: any) {
    failed.value = true
    failedMessage.value = err?.message || '获取评估报告失败'
    loading.value = false
  }
}

const fetchSessionDetail = async () => {
  const sessionId = route.params.sessionId as string
  if (!sessionId) return

  if (Number(sessionId) === MOCK_INTERVIEW_SESSION_ID) {
    turnCount.value = mockInterviewSessionSummary.currentTurnIndex || 0
    return
  }

  try {
    const res = await request.get(`/api/interview/sessions/${sessionId}`) as any
    if (res.code === 200 && res.data) {
      turnCount.value = res.data.currentTurnIndex || 0
    }
  } catch (_) {
    // non-critical
  }
}

watch(() => report.value?.abilityScores, () => {
  if (report.value?.abilityScores) {
    nextTick(initRadarChart)
  }
})

onMounted(() => {
  fetchEvaluation()
  fetchSessionDetail()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (pollTimer) clearTimeout(pollTimer)
  chartInstance?.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.section-title {
  font-size: 1.125rem;
  font-weight: 700;
  color: #faf9f5;
  margin-bottom: 0.75rem;
  padding-left: 0.75rem;
  border-left: 3px solid #6ef17d;
}

.card {
  background-color: #1f1e1d;
  border-radius: 1rem;
  border: 1px solid rgba(250, 249, 245, 0.06);
}

.qa-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: rgba(250, 249, 245, 0.5);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 0.375rem;
}

.qa-text {
  font-size: 0.875rem;
  color: rgba(250, 249, 245, 0.8);
  line-height: 1.6;
}
</style>
