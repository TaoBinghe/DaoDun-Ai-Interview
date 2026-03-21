<template>
  <div class="min-h-[calc(100vh-64px)] bg-[#0f100f] px-4 py-8 text-[#faf9f5]">
    <div class="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <section class="rounded-2xl border border-[#faf9f5]/8 bg-[#1e1e1d] p-6">
        <h1 class="text-5xl font-semibold leading-tight tracking-tight">导入简历</h1>
        <p class="mt-2 text-sm text-[#b9b8b3]">
          支持 PDF 文件，最大 5MB，最多 15 页。
        </p>

        <div
          class="mt-6 flex cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl border border-[#ffffff12] bg-[#0b0c0b] px-6 py-8 text-center transition-colors hover:border-[#ffffff26]"
          @click="triggerFilePick"
          @dragover.prevent
          @drop.prevent="handleDrop"
        >
          <p class="text-2xl font-semibold">点击选择 PDF 简历</p>
          <p class="text-sm text-[#9f9f99]">上传后将自动解析简历，按需和岗位匹配</p>
          <input
            ref="fileInputRef"
            type="file"
            class="hidden"
            accept="application/pdf,.pdf"
            @change="handleFileChange"
          />
        </div>

        <div class="mt-4 flex items-center justify-between gap-4">
          <p class="min-w-0 truncate text-sm text-[#c0c0bc]">
            当前文件：{{ selectedFileName || '未选择文件' }}
          </p>
          <el-button
            :loading="isUploading"
            class="!h-10 !rounded-full !border-0 !bg-[#6ef17d] !px-6 !font-semibold !text-black hover:!bg-[#5edb6b]"
            @click="triggerFilePick"
          >
            {{ isUploading ? '上传中...' : '重新选择文件' }}
          </el-button>
        </div>
      </section>

      <section class="rounded-2xl border border-[#faf9f5]/8 bg-[#1e1e1d] p-6">
        <div class="mb-4 flex items-center justify-between gap-4">
          <h2 class="text-lg font-medium text-[#f1f1ec]">我的简历</h2>
          <el-button
            class="!h-9 !rounded-xl !border-0 !bg-[#faf9f5] !px-4 !font-medium !text-black hover:!opacity-90"
            :loading="isLoadingList"
            @click="loadResumes"
          >
            刷新
          </el-button>
        </div>

        <div v-if="isLoadingList" class="py-8 text-center text-sm text-[#b9b8b3]">
          正在加载简历列表...
        </div>
        <div v-else-if="resumes.length === 0" class="rounded-xl border border-[#faf9f5]/10 bg-[#111211] px-4 py-6 text-center text-sm text-[#b9b8b3]">
          你还没有上传简历，先上传一份试试。
        </div>
        <div v-else class="flex flex-col gap-3">
          <article
            v-for="item in resumes"
            :key="item.resumeId"
            class="flex flex-col gap-4 rounded-xl border border-[#faf9f5]/10 bg-[#111211] p-4 md:flex-row md:items-center md:justify-between"
          >
            <div class="min-w-0">
              <p class="truncate text-sm font-medium text-[#f1f1ec]">{{ item.fileName }}</p>
              <p class="mt-1 text-xs text-[#b9b8b3]">
                上传时间：{{ formatDateTime(item.uploadedAt) }}
              </p>
              <p class="mt-1 text-xs text-[#b9b8b3]">
                页数：{{ item.pageCount ?? '-' }} ｜ 字符数：{{ item.charCount ?? '-' }}
              </p>
            </div>
            <el-button
              type="danger"
              plain
              class="!h-9 !rounded-xl !px-4 !border-[#ff8b8b4d] !bg-transparent !text-[#ff8b8b] hover:!bg-[#ff8b8b12]"
              :loading="deletingId === item.resumeId"
              @click="handleDelete(item.resumeId)"
            >
              删除
            </el-button>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteResumeById, fetchMyResumes, type ResumeItem, uploadResume } from '../api/resume'

const resumes = ref<ResumeItem[]>([])
const isLoadingList = ref(false)
const isUploading = ref(false)
const deletingId = ref<number | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const selectedFileName = ref('')

const allowedExtensions = ['pdf']

function loadResumes() {
  isLoadingList.value = true
  return fetchMyResumes()
    .then((res) => {
      resumes.value = res.data ?? []
    })
    .finally(() => {
      isLoadingList.value = false
    })
}

function triggerFilePick() {
  if (isUploading.value) return
  fileInputRef.value?.click()
}

function getExtension(fileName: string) {
  const index = fileName.lastIndexOf('.')
  if (index < 0) return ''
  return fileName.slice(index + 1).toLowerCase()
}

function validateFile(file: File) {
  const extension = getExtension(file.name)
  if (!allowedExtensions.includes(extension)) {
    ElMessage.warning('仅支持 PDF 文件（.pdf）')
    return false
  }
  return true
}

function resetInput() {
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

async function doUpload(file: File) {
  if (!validateFile(file) || isUploading.value) return
  selectedFileName.value = file.name
  isUploading.value = true
  try {
    await uploadResume(file)
    ElMessage.success('简历上传成功')
    await loadResumes()
  } finally {
    isUploading.value = false
    resetInput()
  }
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  void doUpload(file)
}

function handleDrop(event: DragEvent) {
  const file = event.dataTransfer?.files?.[0]
  if (!file) return
  void doUpload(file)
}

async function handleDelete(id: number) {
  if (deletingId.value !== null) return
  try {
    await ElMessageBox.confirm('删除后无法恢复，确认删除这份简历吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    deletingId.value = id
    await deleteResumeById(id)
    ElMessage.success('简历已删除')
    await loadResumes()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败，请稍后重试')
    }
  } finally {
    deletingId.value = null
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
