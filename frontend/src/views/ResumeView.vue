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
        <!-- 头部 -->
        <header class="parsed-header">
          <h3 class="parsed-name">{{ parsedPreview.name || '未命名' }}</h3>
          <p v-if="parsedPreview.headline" class="parsed-headline">{{ parsedPreview.headline }}</p>
          <div
            v-if="parsedPreview.phone || parsedPreview.email || parsedPreview.location"
            class="parsed-contact-row"
          >
            <span v-if="parsedPreview.phone">{{ parsedPreview.phone }}</span>
            <span v-if="parsedPreview.email">{{ parsedPreview.email }}</span>
            <span v-if="parsedPreview.location">{{ parsedPreview.location }}</span>
          </div>
          <p v-if="parsedPreview.intention" class="parsed-intention">
            <span class="parsed-intention-label">求职意向</span>
            <span>{{ parsedPreview.intention }}</span>
          </p>
        </header>

        <!-- 教育经历 -->
        <section v-if="parsedPreview.education.length" class="parsed-block">
          <h4 class="parsed-block-title">教育经历</h4>
          <ul class="parsed-list">
            <li v-for="(edu, i) in parsedPreview.education" :key="i" class="parsed-item">
              <p class="parsed-item-title">{{ [edu.school, edu.degree, edu.major].filter(Boolean).join(' · ') }}</p>
              <p v-if="edu.period" class="parsed-item-sub">{{ edu.period }}</p>
            </li>
          </ul>
        </section>

        <!-- 工作经历 -->
        <section v-if="parsedPreview.experience.length" class="parsed-block">
          <h4 class="parsed-block-title">工作经历</h4>
          <ul class="parsed-list">
            <li v-for="(exp, i) in parsedPreview.experience" :key="i" class="parsed-item">
              <p class="parsed-item-title">{{ [exp.company, exp.title].filter(Boolean).join(' — ') }}</p>
              <p v-if="exp.period" class="parsed-item-sub">{{ exp.period }}</p>
              <ul v-if="exp.highlights.length" class="parsed-highlights">
                <li v-for="(h, hi) in exp.highlights" :key="hi">{{ h }}</li>
              </ul>
            </li>
          </ul>
        </section>

        <!-- 项目经历 -->
        <section v-if="parsedPreview.projects.length" class="parsed-block">
          <h4 class="parsed-block-title">项目经历</h4>
          <ul class="parsed-list">
            <li v-for="(proj, i) in parsedPreview.projects" :key="i" class="parsed-item">
              <p class="parsed-item-title">{{ proj.name }}</p>
              <p v-if="proj.stack" class="parsed-item-sub">{{ proj.stack }}</p>
              <p v-if="proj.description" class="parsed-item-desc">{{ proj.description }}</p>
            </li>
          </ul>
        </section>

        <!-- 技能标签 -->
        <section v-if="parsedPreview.skills.length" class="parsed-block">
          <h4 class="parsed-block-title">技能标签</h4>
          <div class="parsed-skills">
            <span v-for="(sk, i) in parsedPreview.skills" :key="i" class="parsed-skill-tag">{{ sk }}</span>
          </div>
        </section>

        <!-- 自我评价 -->
        <section v-if="parsedPreview.summary" class="parsed-block">
          <h4 class="parsed-block-title">自我评价</h4>
          <p class="parsed-summary">{{ parsedPreview.summary }}</p>
        </section>

        <!-- 空数据占位 -->
        <div v-if="isPreviewEmpty" class="parsed-empty">
          <p class="parsed-empty-text">简历解析后暂无结构化数据，请确认 PDF 内容是否可读。</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteResumeById, fetchMyResumes, type ResumeItem, uploadResume } from '../api/resume'
import { RESUME_PARSED_PREVIEW_MOCK, type ResumeParsedPreview } from '../mocks/resumeParsedPreviewMock'

const resumes = ref<ResumeItem[]>([])
const isLoadingList = ref(false)
const isUploading = ref(false)
const deletingId = ref<number | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const selectedFileName = ref('')
const showParsedPreview = ref(false)
const lastUploadedItem = ref<ResumeItem | null>(null)

function buildParsedPreview(item: ResumeItem | null): ResumeParsedPreview {
  if (!item) return RESUME_PARSED_PREVIEW_MOCK
  return {
    name: item.fileName.replace(/\.pdf$/i, ''),
    headline: [item.education?.[0], item.previewText?.slice(0, 40)].filter(Boolean).join(' · ') || '待解析',
    phone: '',
    email: '',
    location: '',
    intention: '',
    education: (item.education ?? []).map((e) => ({
      school: e,
      degree: '',
      major: '',
      period: ''
    })),
    experience: [],
    projects: (item.projects ?? []).map((p) => ({
      name: p,
      stack: '',
      description: ''
    })),
    skills: item.skills ?? [],
    summary: item.previewText ?? ''
  }
}

const parsedPreview = computed(() => buildParsedPreview(lastUploadedItem.value))

const isPreviewEmpty = computed(() => {
  const p = parsedPreview.value
  return !p.name && !p.headline && !p.phone && !p.email && !p.location &&
    !p.intention && !p.education.length && !p.experience.length &&
    !p.projects.length && !p.skills.length && !p.summary
})

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
    const res = await uploadResume(file)
    ElMessage.success('简历上传成功')
    if (res?.data) {
      lastUploadedItem.value = res.data
      showParsedPreview.value = true
    }
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

</style>

<style>
/* 结构化预览弹窗 —— 非 scoped，因 el-dialog append-to-body 后依赖全局样式 */
.resume-parsed-dialog .parsed-preview-scroll {
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--app-accent) 28%, transparent) transparent;
}

.resume-parsed-dialog .parsed-preview-scroll::-webkit-scrollbar {
  width: 5px;
}
.resume-parsed-dialog .parsed-preview-scroll::-webkit-scrollbar-track {
  background: transparent;
}
.resume-parsed-dialog .parsed-preview-scroll::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--app-accent) 28%, transparent);
  border-radius: 9999px;
}

/* 头部 */
.resume-parsed-dialog .parsed-header {
  padding-bottom: 1.25rem;
  margin-bottom: 1.5rem;
  border-bottom: 1px solid var(--app-border);
}
.resume-parsed-dialog .parsed-name {
  font-size: 1.375rem;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.01em;
  color: var(--app-text);
}
.resume-parsed-dialog .parsed-headline {
  margin-top: 0.25rem;
  font-size: 0.875rem;
  line-height: 1.5;
  color: var(--app-text-muted);
}
.resume-parsed-dialog .parsed-contact-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1.25rem;
  margin-top: 0.75rem;
  font-size: 0.8125rem;
  color: var(--app-text-soft);
}
.resume-parsed-dialog .parsed-intention {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  margin-top: 0.625rem;
  font-size: 0.875rem;
  line-height: 1.5;
  color: var(--app-text-soft);
}
.resume-parsed-dialog .parsed-intention-label {
  font-weight: 600;
  font-size: 0.8125rem;
  color: var(--app-text);
  flex-shrink: 0;
}

/* 区块 */
.resume-parsed-dialog .parsed-block {
  margin-bottom: 1.5rem;
}
.resume-parsed-dialog .parsed-block:last-child {
  margin-bottom: 0;
}
.resume-parsed-dialog .parsed-block-title {
  font-size: 0.8125rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--app-text-muted);
  margin-bottom: 0.625rem;
  padding-bottom: 0.375rem;
  border-bottom: 1px solid color-mix(in srgb, var(--app-border) 50%, transparent);
}

/* 列表 */
.resume-parsed-dialog .parsed-list {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
}
.resume-parsed-dialog .parsed-item {
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
  border-radius: 0.75rem;
  padding: 0.75rem 1rem;
}
.resume-parsed-dialog .parsed-item-title {
  font-size: 0.875rem;
  font-weight: 600;
  line-height: 1.5;
  color: var(--app-text);
}
.resume-parsed-dialog .parsed-item-sub {
  margin-top: 0.125rem;
  font-size: 0.75rem;
  color: var(--app-text-muted);
}
.resume-parsed-dialog .parsed-item-desc {
  margin-top: 0.5rem;
  font-size: 0.8125rem;
  line-height: 1.65;
  color: var(--app-text-soft);
}

/* 亮点 */
.resume-parsed-dialog .parsed-highlights {
  margin-top: 0.5rem;
  padding-left: 1.125rem;
  list-style: disc;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.8125rem;
  line-height: 1.6;
  color: var(--app-text-soft);
}
.resume-parsed-dialog .parsed-highlights li::marker {
  color: var(--app-text-muted);
}

/* 技能 */
.resume-parsed-dialog .parsed-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.resume-parsed-dialog .parsed-skill-tag {
  display: inline-block;
  font-size: 0.75rem;
  font-weight: 500;
  line-height: 1.4;
  padding: 0.25rem 0.75rem;
  border-radius: 0.5rem;
  background: color-mix(in srgb, var(--app-accent) 10%, var(--app-surface-soft));
  border: 1px solid color-mix(in srgb, var(--app-accent) 20%, transparent);
  color: var(--app-text-soft);
}

/* 自我评价 */
.resume-parsed-dialog .parsed-summary {
  font-size: 0.8125rem;
  line-height: 1.75;
  color: var(--app-text-soft);
  white-space: pre-wrap;
}

/* 空状态 */
.resume-parsed-dialog .parsed-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
}
.resume-parsed-dialog .parsed-empty-text {
  font-size: 0.8125rem;
  color: var(--app-text-faint);
}
</style>
