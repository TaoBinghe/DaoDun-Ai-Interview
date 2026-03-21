<template>
  <el-card shadow="never" class="!rounded-2xl !border-[#ffffff14] !bg-[#1d1e1d]">
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold text-[#f1f1ec]">每日面试查询</h2>
        <p class="mt-1 text-xs text-[#9f9f99]">参考 AI Line Edits 信息密度，展示近期面试活跃度。</p>
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
        <p class="text-xs text-[#9f9f99]">总面试次数</p>
        <p class="mt-2 text-2xl font-semibold text-[#f1f1ec]">{{ interviewStats.total }}</p>
      </div>
      <div class="rounded-xl border border-[#ffffff14] bg-[#111211] p-4">
        <p class="text-xs text-[#9f9f99]">本月最活跃日</p>
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

    <div class="mt-4 overflow-x-auto">
      <div class="min-w-[900px] rounded-xl border border-[#ffffff14] bg-[#111211] p-4">
        <div class="mb-2 flex items-center justify-between text-xs text-[#9f9f99]">
          <span>每日活跃网格</span>
          <span>{{ interviewRangeLabel }}</span>
        </div>
        <div class="grid grid-rows-7 grid-flow-col auto-cols-max gap-1">
          <div
            v-for="cell in heatmapCells"
            :key="cell.date"
            class="h-3 w-3 rounded-[3px] border border-[#ffffff10]"
            :class="heatColorClass(cell.count)"
            :title="`${cell.date}：${cell.count}次`"
          />
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type RangeType = 'all' | '30d' | '7d'
interface HeatmapCell {
  date: string
  count: number
}

const interviewRange = ref<RangeType>('all')
const rangeOptions = [
  { label: 'All', value: 'all' as const },
  { label: '最近30天', value: '30d' as const },
  { label: '最近7天', value: '7d' as const }
]
const heatmapSeed = ref<HeatmapCell[]>([])

const interviewRangeLabel = computed(() => {
  if (interviewRange.value === '7d') return '最近 7 天'
  if (interviewRange.value === '30d') return '最近 30 天'
  return '最近 12 个月'
})

const heatmapCells = computed(() => {
  const base = heatmapSeed.value
  if (interviewRange.value === '7d') return base.slice(-7)
  if (interviewRange.value === '30d') return base.slice(-30)
  return base
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

function generateHeatmapSeed() {
  const days = 365
  const result: HeatmapCell[] = []
  const now = new Date()
  for (let i = days - 1; i >= 0; i -= 1) {
    const date = new Date(now)
    date.setDate(now.getDate() - i)
    const day = date.getDay()
    const offset = (date.getDate() + date.getMonth() * 3) % 6
    const count = day === 0 ? 0 : Math.max(0, offset - (day === 6 ? 2 : 1))
    result.push({ date: date.toISOString().slice(0, 10), count })
  }
  heatmapSeed.value = result
}

function heatColorClass(count: number) {
  if (count <= 0) return 'bg-[#1f2220]'
  if (count === 1) return 'bg-[#1f5a36]'
  if (count === 2) return 'bg-[#20884d]'
  if (count === 3) return 'bg-[#2bb666]'
  return 'bg-[#5fe086]'
}

onMounted(() => {
  generateHeatmapSeed()
})
</script>
