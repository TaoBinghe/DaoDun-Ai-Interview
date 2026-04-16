<template>
  <div class="flex flex-col gap-6">
    <el-card shadow="never" class="!rounded-2xl !border-[#ffffff14] !bg-[#1d1e1d]">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-xl font-semibold text-[#f1f1ec]">面试历史</h2>
          <p class="mt-1 text-xs text-[#9f9f99]">查看历次 AI 面试记录与评估报告（报告在会话结束后异步生成）。</p>
        </div>
        <el-button
          class="!h-9 !rounded-xl !border-[#ffffff1f] !bg-[#2a2b2a] !px-4 !text-[#f1f1ec] hover:!bg-[#343533]"
          :loading="sessionsLoading"
          @click="loadSessions"
        >
          刷新
        </el-button>
      </div>

      <div v-if="sessionsError" class="mb-4 rounded-xl border border-[#fecaca] bg-[#fef2f2] px-4 py-3 text-sm text-[#b91c1c]">
        {{ sessionsError }}
      </div>

      <div v-if="sessionsLoading && !sessions.length" class="py-8 text-center text-sm text-[#b9b8b3]">
        正在加载面试记录...
      </div>
      <div v-else-if="!displayedSessions.length" class="rounded-xl border border-[#ffffff14] bg-[#111211] px-4 py-8 text-center text-sm text-[#b9b8b3]">
        <template v-if="!sessions.length">
          暂无面试记录。前往「AI 面试」开始一场面试，结束后报告将出现在此处。
        </template>
        <template v-else-if="hasEndedSessions">
          报告生成中，请稍后点击「刷新」查看。
        </template>
        <template v-else>
          暂无已结束的面试记录。进行中的会话请从「AI 面试」进入。
        </template>
      </div>
      <div v-else class="overflow-x-auto">
        <table class="w-full min-w-[600px] text-left text-sm">
          <thead>
            <tr class="border-b border-[#ffffff14] text-xs text-[#9f9f99]">
              <th class="pb-3 pr-4 font-medium">岗位</th>
              <th class="pb-3 pr-4 font-medium">开始时间</th>
              <th class="pb-3 pr-4 font-medium">结束时间</th>
              <th class="pb-3 pr-4 font-medium">轮次</th>
              <th class="pb-3 pr-4 font-medium">报告</th>
              <th class="pb-3 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in displayedSessions"
              :key="row.sessionId"
              class="border-b border-[#ffffff0d] text-[#e8e8e3]"
            >
              <td class="py-3 pr-4 align-middle">{{ row.positionName }}</td>
              <td class="py-3 pr-4 align-middle text-[#b9b8b3]">{{ formatDateTime(row.startedAt) }}</td>
              <td class="py-3 pr-4 align-middle text-[#b9b8b3]">{{ formatDateTime(String(row.endedAt)) }}</td>
              <td class="py-3 pr-4 align-middle text-[#b9b8b3]">{{ row.currentTurnIndex || 0 }} 轮</td>
              <td class="py-3 pr-4 align-middle">
                <span class="text-xs text-[#b9b8b3]">{{ evaluationStatusLabel(row.evaluationStatus) }}</span>
              </td>
              <td class="py-3 text-right align-middle">
                <div class="flex items-center justify-end gap-3">
                  <el-button
                    v-if="canOpenReport(row.evaluationStatus)"
                    type="primary"
                    link
                    class="!text-[#6ef17d]"
                    @click="goReport(row.sessionId)"
                  >
                    查看报告
                  </el-button>
                  <span v-if="!canOpenReport(row.evaluationStatus)" class="text-xs text-[#6b6b66]">—</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </el-card>

    <el-card shadow="never" class="!rounded-2xl !border-[#ffffff14] !bg-[#1d1e1d]">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-xl font-semibold text-[#f1f1ec]">面试活跃度</h2>
          <p class="mt-1 text-xs text-[#9f9f99]">按面试开始日期统计（与上方列表同源）。</p>
        </div>
        <div class="inline-flex rounded-xl border border-[#ffffff14] bg-[#111211] p-1">
          <button
            v-for="item in rangeOptions"
            :key="item.value"
            type="button"
            class="rounded-lg px-3 py-1 text-xs transition-colors"
            :class="interviewRange === item.value ? 'bg-[#2f3130] text-[#f1f1ec]' : 'text-[#9f9f99] hover:text-[#f1f1ec]'"
            @click="interviewRange = item.value"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
        <div class="rounded-xl border border-[#ffffff14] bg-[#111211] p-4">
          <p class="text-xs text-[#9f9f99]">区间内面试次数</p>
          <p class="mt-2 text-2xl font-semibold text-[#f1f1ec]">{{ interviewStats.total }}</p>
        </div>
        <div class="rounded-xl border border-[#ffffff14] bg-[#111211] p-4">
          <p class="text-xs text-[#9f9f99]">最活跃日</p>
          <p class="mt-2 text-sm font-medium text-[#f1f1ec]">{{ interviewStats.mostActiveDay }}</p>
        </div>
        <div class="rounded-xl border border-[#ffffff14] bg-[#111211] p-4">
          <p class="text-xs text-[#9f9f99]">最长连续天数</p>
          <p class="mt-2 text-2xl font-semibold text-[#f1f1ec]">{{ interviewStats.longestStreak }}d</p>
        </div>
        <div class="rounded-xl border border-[#ffffff14] bg-[#111211] p-4">
          <p class="text-xs text-[#9f9f99]">当前连续天数</p>
          <p class="mt-2 text-2xl font-semibold text-[#f1f1ec]">{{ interviewStats.currentStreak }}d</p>
        </div>
      </div>

      <div class="mt-4 w-full min-w-0">
        <div class="w-full min-w-0 rounded-xl border border-[#ffffff14] bg-[#111211] p-4">
          <div class="mb-2 flex items-center justify-between text-xs text-[#9f9f99]">
            <span>每日活跃网格</span>
            <span>{{ interviewRangeLabel }}</span>
          </div>
          <div
            class="grid h-[108px] w-full min-w-0 grid-flow-col gap-1"
            style="grid-template-rows: repeat(7, minmax(0, 1fr)); grid-auto-columns: minmax(0, 1fr)"
          >
            <div
              v-for="cell in heatmapCells"
              :key="cell.date"
              class="min-h-0 min-w-0 rounded-[3px] border border-[#ffffff10]"
              :class="heatColorClass(cell.count)"
              :title="`${cell.date}：${cell.count}次`"
            />
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../utils/request'
import { mockInterviewSessionSummary } from '../../mocks/interviewReportMock'

type RangeType = 'all' | '30d' | '7d'

interface SessionSummary {
  sessionId: number
  positionId: number
  positionName: string
  status: string
  currentTurnIndex: number
  startedAt: string
  endedAt: string | null
  createTime?: string
  evaluationStatus: string
}

interface HeatmapCell {
  date: string
  count: number
}

const router = useRouter()

const sessions = ref<SessionSummary[]>([])
const sessionsLoading = ref(false)
const sessionsError = ref('')

const interviewRange = ref<RangeType>('all')
const rangeOptions = [
  { label: '全部', value: 'all' as const },
  { label: '最近30天', value: '30d' as const },
  { label: '最近7天', value: '7d' as const }
]

const interviewRangeLabel = computed(() => {
  if (interviewRange.value === '7d') return '最近 7 天'
  if (interviewRange.value === '30d') return '最近 30 天'
  return '最近 12 个月'
})

function sessionHasEndedAt(s: SessionSummary) {
  const end = s.endedAt
  return end != null && String(end).trim() !== ''
}

/** 是否存在至少一条已结束（有结束时间）的会话（用于空状态提示） */
const hasEndedSessions = computed(() => sessions.value.some(sessionHasEndedAt))

/** 已结束且报告非「生成中」的会话（生成中在前端不展示） */
const displayedSessions = computed(() =>
  [mockInterviewSessionSummary, ...sessions.value]
    .filter(
      (s) => sessionHasEndedAt(s) && s.evaluationStatus !== 'GENERATING'
    )
    .filter((session, index, list) => list.findIndex((item) => item.sessionId === session.sessionId) === index)
)

/** 按开始日期 yyyy-MM-dd 统计次数 */
const countsByDate = computed(() => {
  const map = new Map<string, number>()
  for (const s of [mockInterviewSessionSummary, ...sessions.value]) {
    if (!s.startedAt) continue
    const day = s.startedAt.slice(0, 10)
    map.set(day, (map.get(day) ?? 0) + 1)
  }
  return map
})

const heatmapCells = computed(() => {
  const days = 365
  const result: HeatmapCell[] = []
  const now = new Date()
  for (let i = days - 1; i >= 0; i -= 1) {
    const date = new Date(now)
    date.setDate(now.getDate() - i)
    const key = date.toISOString().slice(0, 10)
    const count = countsByDate.value.get(key) ?? 0
    result.push({ date: key, count })
  }
  let slice = result
  if (interviewRange.value === '7d') slice = result.slice(-7)
  else if (interviewRange.value === '30d') slice = result.slice(-30)
  return slice
})

const interviewStats = computed(() => {
  const cells = heatmapCells.value
  const total = cells.reduce((sum, item) => sum + item.count, 0)
  let mostActiveDay = '-'
  let max = -1
  for (const cell of cells) {
    if (cell.count > max) {
      max = cell.count
      mostActiveDay = cell.date
    }
  }
  let currentStreak = 0
  for (let i = cells.length - 1; i >= 0; i -= 1) {
    const cell = cells[i]
    if (cell && cell.count > 0) currentStreak += 1
    else break
  }
  let longestStreak = 0
  let running = 0
  for (const cell of cells) {
    if (cell.count > 0) {
      running += 1
      longestStreak = Math.max(longestStreak, running)
    } else {
      running = 0
    }
  }
  return { total, mostActiveDay, currentStreak, longestStreak }
})

function heatColorClass(count: number) {
  if (count <= 0) return 'bg-[#1f2220]'
  if (count === 1) return 'bg-[#1f5a36]'
  if (count === 2) return 'bg-[#20884d]'
  if (count === 3) return 'bg-[#2bb666]'
  return 'bg-[#5fe086]'
}

function formatDateTime(iso: string) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('zh-CN', { hour12: false })
}

function evaluationStatusLabel(s: string) {
  const map: Record<string, string> = {
    NOT_STARTED: '未生成',
    GENERATING: '生成中',
    READY: '已就绪',
    FAILED: '生成失败',
    INSUFFICIENT_DATA: '信息不足'
  }
  return map[s] ?? s ?? '—'
}

function canOpenReport(evaluationStatus: string) {
  return ['READY', 'FAILED', 'INSUFFICIENT_DATA'].includes(evaluationStatus)
}

function goReport(sessionId: number) {
  router.push({ name: 'interview-report', params: { sessionId: String(sessionId) } })
}

async function loadSessions() {
  sessionsLoading.value = true
  sessionsError.value = ''
  try {
    const res = (await request.get('/api/interview/sessions')) as { code: number; data?: SessionSummary[]; msg?: string }
    if (res.code === 200 && Array.isArray(res.data)) {
      sessions.value = res.data
    } else {
      sessionsError.value = res.msg || '加载失败'
    }
  } catch (e: unknown) {
    sessionsError.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    sessionsLoading.value = false
  }
}

onMounted(() => {
  loadSessions()
})
</script>
