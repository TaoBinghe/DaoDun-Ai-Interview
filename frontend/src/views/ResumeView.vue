<template>
  <div class="theme-page min-h-[calc(100vh-64px)] px-4 py-8">
    <div class="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <section class="resume-card rounded-2xl p-6">
        <h1 class="text-5xl font-semibold leading-tight tracking-tight">导入简历</h1>
        <p class="mt-2 text-sm theme-text-muted">
          支持 PDF 文件，最大 5MB，最多 15 页。
        </p>

        <div class="mt-4">
          <el-button
            class="resume-preview-btn !h-9 !rounded-xl !px-4 !font-medium"
            @click="showParsedPreview = true"
          >
            查看结构化预览
          </el-button>
          <p class="mt-2 text-xs theme-text-faint">
            以下为前端演示：模拟简历经解析后的结构化展示，便于对照实际上传后的效果。
          </p>
        </div>

        <div
          class="resume-dropzone mt-6 flex cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl px-6 py-8 text-center transition-colors"
          @click="triggerFilePick"
          @dragover.prevent
          @drop.prevent="handleDrop"
        >
          <p class="text-2xl font-semibold">点击选择 PDF 简历</p>
          <p class="text-sm theme-text-faint">上传后将自动解析简历，按需和岗位匹配</p>
          <input
            ref="fileInputRef"
            type="file"
            class="hidden"
            accept="application/pdf,.pdf"
            @change="handleFileChange"
          />
        </div>

        <div class="mt-4 flex items-center justify-between gap-4">
          <p class="min-w-0 truncate text-sm theme-text-soft">
            当前文件：{{ selectedFileName || '未选择文件' }}
          </p>
          <el-button
            :loading="isUploading"
            class="theme-el-btn-primary !h-10 !rounded-full !border-0 !px-6 !font-semibold"
            @click="triggerFilePick"
          >
            {{ isUploading ? '上传中...' : '重新选择文件' }}
          </el-button>
        </div>
      </section>

      <section class="resume-card rounded-2xl p-6">
        <div class="mb-4 flex items-center justify-between gap-4">
          <h2 class="text-lg font-medium theme-title">我的简历</h2>
          <el-button
            class="resume-secondary-btn !h-9 !rounded-xl !px-4 !font-medium"
            :loading="isLoadingList"
            @click="loadResumes"
          >
            刷新
          </el-button>
        </div>

        <div v-if="isLoadingList" class="py-8 text-center text-sm theme-text-muted">
          正在加载简历列表...
        </div>
        <div v-else-if="resumes.length === 0" class="resume-empty rounded-xl px-4 py-6 text-center text-sm theme-text-muted">
          你还没有上传简历，先上传一份试试。
        </div>
        <div v-else class="flex flex-col gap-3">
          <article
            v-for="item in resumes"
            :key="item.resumeId"
            class="resume-item flex flex-col gap-4 rounded-xl p-4 md:flex-row md:items-center md:justify-between"
          >
            <div class="min-w-0">
              <p class="truncate text-sm font-medium theme-title">{{ item.fileName }}</p>
              <p class="mt-1 text-xs theme-text-muted">
                上传时间：{{ formatDateTime(item.uploadedAt) }}
              </p>
              <p class="mt-1 text-xs theme-text-muted">
                页数：{{ item.pageCount ?? '-' }} ｜ 字符数：{{ item.charCount ?? '-' }}
              </p>
            </div>
            <el-button
              type="danger"
              plain
              class="resume-danger-btn !h-9 !rounded-xl !px-4"
              :loading="deletingId === item.resumeId"
              @click="handleDelete(item.resumeId)"
            >
              删除
            </el-button>
          </article>
        </div>
      </section>
    </div>

    <el-dialog
      v-model="showParsedPreview"
      title="结构化简历预览"
      class="resume-parsed-dialog"
      width="min(92vw, 640px)"
      destroy-on-close
      append-to-body
    >
      <div class="parsed-preview-scroll max-h-[min(72vh,560px)] overflow-y-auto pr-1">
        <header class="parsed-hero border-b border-[var(--app-border)] pb-4 mb-5">
          <h3 class="text-xl font-semibold theme-title">{{ parsedMock.name }}</h3>
          <p class="mt-1 text-sm theme-text-muted">{{ parsedMock.headline }}</p>
          <ul class="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs theme-text-soft">
            <li>{{ parsedMock.phone }}</li>
            <li>{{ parsedMock.email }}</li>
            <li>{{ parsedMock.location }}</li>
          </ul>
          <p class="mt-3 text-sm theme-text-soft">
            <span class="font-medium theme-title">求职意向：</span>{{ parsedMock.intention }}
          </p>
        </header>

        <section class="parsed-block mb-5">
          <h4 class="parsed-block-title">教育经历</h4>
          <ul class="mt-2 space-y-3">
            <li
              v-for="(edu, i) in parsedMock.education"
              :key="i"
              class="parsed-item rounded-xl p-3"
            >
              <p class="text-sm font-medium theme-title">{{ edu.school }} · {{ edu.degree }} · {{ edu.major }}</p>
              <p class="mt-1 text-xs theme-text-muted">{{ edu.period }}</p>
            </li>
          </ul>
        </section>

        <section class="parsed-block mb-5">
          <h4 class="parsed-block-title">工作经历</h4>
          <ul class="mt-2 space-y-4">
            <li
              v-for="(exp, i) in parsedMock.experience"
              :key="i"
              class="parsed-item rounded-xl p-3"
            >
              <p class="text-sm font-medium theme-title">{{ exp.company }} — {{ exp.title }}</p>
              <p class="mt-0.5 text-xs theme-text-muted">{{ exp.period }}</p>
              <ul class="mt-2 list-disc space-y-1 pl-4 text-sm theme-text-soft">
                <li v-for="(h, hi) in exp.highlights" :key="hi">{{ h }}</li>
              </ul>
            </li>
          </ul>
        </section>

        <section class="parsed-block mb-5">
          <h4 class="parsed-block-title">项目经历</h4>
          <ul class="mt-2 space-y-3">
            <li
              v-for="(proj, i) in parsedMock.projects"
              :key="i"
              class="parsed-item rounded-xl p-3"
            >
              <p class="text-sm font-medium theme-title">{{ proj.name }}</p>
              <p class="mt-1 text-xs theme-text-muted">{{ proj.stack }}</p>
              <p class="mt-2 text-sm theme-text-soft leading-relaxed">{{ proj.description }}</p>
            </li>
          </ul>
        </section>

        <section class="parsed-block mb-5">
          <h4 class="parsed-block-title">技能标签</h4>
          <div class="mt-2 flex flex-wrap gap-2">
            <span
              v-for="(sk, i) in parsedMock.skills"
              :key="i"
              class="parsed-skill-tag rounded-lg px-2.5 py-1 text-xs"
            >
              {{ sk }}
            </span>
          </div>
        </section>

        <section class="parsed-block">
          <h4 class="parsed-block-title">自我评价</h4>
          <p class="mt-2 text-sm theme-text-soft leading-relaxed whitespace-pre-wrap">
            {{ parsedMock.summary }}
          </p>
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteResumeById, fetchMyResumes, type ResumeItem, uploadResume } from '../api/resume'
import { RESUME_PARSED_PREVIEW_MOCK } from '../mocks/resumeParsedPreviewMock'

const resumes = ref<ResumeItem[]>([])
const isLoadingList = ref(false)
const isUploading = ref(false)
const deletingId = ref<number | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const selectedFileName = ref('')
const showParsedPreview = ref(false)
const parsedMock = RESUME_PARSED_PREVIEW_MOCK

const allowedExtensions = ['pdf']

async function loadResumes() {
  isLoadingList.value = true
  try {
    const res = await fetchMyResumes()
    resumes.value = res.data ?? []
  } finally {
    isLoadingList.value = false
  }
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
  } catch (error: unknown) {
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

<style scoped>
.resume-card {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  box-shadow: var(--app-shadow);
}

.resume-dropzone {
  background: var(--app-surface-soft);
  border: 1px dashed var(--app-border-strong);
}

.resume-dropzone:hover {
  border-color: color-mix(in srgb, var(--app-accent) 35%, transparent);
}

.resume-secondary-btn {
  background: var(--app-primary);
  color: var(--app-primary-contrast);
  border: 0;
}

.resume-empty,
.resume-item {
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
}

.resume-danger-btn {
  background: transparent !important;
  color: var(--app-danger) !important;
  border-color: color-mix(in srgb, var(--app-danger) 28%, transparent) !important;
}

.resume-danger-btn:hover {
  background: var(--app-danger-soft) !important;
}

.resume-preview-btn {
  background: var(--app-surface-strong);
  color: var(--app-text);
  border: 1px solid var(--app-border-strong);
}

.resume-preview-btn:hover {
  border-color: color-mix(in srgb, var(--app-accent) 35%, transparent);
  color: var(--app-text);
}

.parsed-block-title {
  font-size: 0.8125rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--app-text-muted) 88%, transparent);
}

.parsed-item {
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
}

.parsed-skill-tag {
  background: color-mix(in srgb, var(--app-accent) 12%, var(--app-surface-soft));
  border: 1px solid color-mix(in srgb, var(--app-accent) 22%, transparent);
  color: var(--app-text-soft);
}
</style>
