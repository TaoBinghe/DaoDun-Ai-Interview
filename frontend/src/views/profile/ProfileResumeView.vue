<template>
  <el-card shadow="never" class="theme-el-card !rounded-2xl">
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <h2 class="theme-title text-xl font-semibold">历史简历结果查询</h2>
      <div class="flex items-center gap-2">
        <el-input v-model="resumeKeyword" placeholder="按文件名搜索" clearable class="!w-52" />
        <el-button
          class="theme-el-btn-secondary h-9! rounded-xl! px-4!"
          :loading="isLoadingResumes"
          @click="loadResumes"
        >
          刷新
        </el-button>
      </div>
    </div>

    <div v-if="resumeError" class="resume-error mb-4 rounded-xl px-4 py-3 text-sm">
      {{ resumeError }}
    </div>
    <div v-if="isLoadingResumes" class="py-8 text-center text-sm theme-text-muted">正在加载历史简历...</div>
    <div v-else-if="filteredResumes.length === 0" class="resume-empty rounded-xl px-4 py-6 text-center text-sm theme-text-muted">
      暂无匹配的简历结果。
    </div>
    <div v-else class="flex flex-col gap-3">
      <article
        v-for="item in filteredResumes"
        :key="item.resumeId"
        class="resume-item flex flex-col gap-4 rounded-xl p-4 md:flex-row md:items-center md:justify-between"
      >
        <div class="min-w-0">
          <p class="truncate text-sm font-medium theme-title">{{ item.fileName }}</p>
          <p class="mt-1 text-xs theme-text-muted">上传时间：{{ formatDateTime(item.uploadedAt) }}</p>
          <p class="mt-1 text-xs theme-text-muted">页数：{{ item.pageCount ?? '-' }} ｜ 字符数：{{ item.charCount ?? '-' }}</p>
        </div>
        <span class="theme-chip-accent inline-flex w-fit rounded-full px-3 py-1 text-xs">
          解析完成
        </span>
      </article>
    </div>
  </el-card>
</template>

<style scoped>
.resume-error {
  border: 1px solid color-mix(in srgb, var(--app-danger) 28%, transparent);
  background: var(--app-danger-soft);
  color: var(--app-danger);
}

.resume-empty,
.resume-item {
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchMyResumes, type ResumeItem } from '../../api/resume'

const resumes = ref<ResumeItem[]>([])
const isLoadingResumes = ref(false)
const resumeError = ref('')
const resumeKeyword = ref('')

const filteredResumes = computed(() => {
  const keyword = resumeKeyword.value.trim().toLowerCase()
  if (!keyword) return resumes.value
  return resumes.value.filter((item) => (item.fileName || '').toLowerCase().includes(keyword))
})

async function loadResumes() {
  isLoadingResumes.value = true
  resumeError.value = ''
  try {
    const res = await fetchMyResumes()
    resumes.value = res.data ?? []
  } catch (error: unknown) {
    resumes.value = []
    resumeError.value = error instanceof Error ? error.message : '加载简历列表失败'
  } finally {
    isLoadingResumes.value = false
  }
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

onMounted(() => {
  void loadResumes()
})
</script>
