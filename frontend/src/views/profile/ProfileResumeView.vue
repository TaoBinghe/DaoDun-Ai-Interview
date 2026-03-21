<template>
  <el-card shadow="never" class="!rounded-2xl !border-[#ffffff14] !bg-[#1d1e1d]">
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <h2 class="text-xl font-semibold text-[#f1f1ec]">历史简历结果查询</h2>
      <div class="flex items-center gap-2">
        <el-input v-model="resumeKeyword" placeholder="按文件名搜索" clearable class="!w-52" />
        <el-button
          class="!h-9 !rounded-xl !border-[#ffffff1f] !bg-[#2a2b2a] !px-4 !text-[#f1f1ec] hover:!bg-[#343533]"
          :loading="isLoadingResumes"
          @click="loadResumes"
        >
          刷新
        </el-button>
      </div>
    </div>

    <div v-if="resumeError" class="mb-4 rounded-xl border border-[#fecaca] bg-[#fef2f2] px-4 py-3 text-sm text-[#b91c1c]">
      {{ resumeError }}
    </div>
    <div v-if="isLoadingResumes" class="py-8 text-center text-sm text-[#b9b8b3]">正在加载历史简历...</div>
    <div v-else-if="filteredResumes.length === 0" class="rounded-xl border border-[#ffffff14] bg-[#111211] px-4 py-6 text-center text-sm text-[#b9b8b3]">
      暂无匹配的简历结果。
    </div>
    <div v-else class="flex flex-col gap-3">
      <article
        v-for="item in filteredResumes"
        :key="item.resumeId"
        class="flex flex-col gap-4 rounded-xl border border-[#ffffff14] bg-[#111211] p-4 md:flex-row md:items-center md:justify-between"
      >
        <div class="min-w-0">
          <p class="truncate text-sm font-medium text-[#f1f1ec]">{{ item.fileName }}</p>
          <p class="mt-1 text-xs text-[#b9b8b3]">上传时间：{{ formatDateTime(item.uploadedAt) }}</p>
          <p class="mt-1 text-xs text-[#b9b8b3]">页数：{{ item.pageCount ?? '-' }} ｜ 字符数：{{ item.charCount ?? '-' }}</p>
        </div>
        <span class="inline-flex w-fit rounded-full border border-[#6ef17d40] bg-[#6ef17d1c] px-3 py-1 text-xs text-[#85f892]">
          解析完成
        </span>
      </article>
    </div>
  </el-card>
</template>

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
  } catch (error: any) {
    resumes.value = []
    resumeError.value = error?.message || '加载简历列表失败'
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
