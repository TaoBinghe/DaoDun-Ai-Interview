<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDark } from '@vueuse/core'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Document, UploadFilled } from '@element-plus/icons-vue'
import request from '../utils/request'

interface ResumeItem {
  resumeId: number
  fileName: string
  charCount: number
  pageCount: number
  previewText: string
  projects: string[]
  skills: string[]
  education: string[]
  uploadedAt: string
}

const router = useRouter()
const isDark = useDark()

const resumes = ref<ResumeItem[]>([])
const loading = ref(false)
const uploadProgress = ref(0)
const uploading = ref(false)
const dragActive = ref(false)
const selectedFileName = ref('')
const expandedIds = ref<Set<number>>(new Set())
const fileInputRef = ref<HTMLInputElement | null>(null)

const sortedResumes = computed(() => resumes.value)

onMounted(() => {
  fetchResumes()
})

const fetchResumes = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/api/resume/me')
    resumes.value = res.data || []
  } finally {
    loading.value = false
  }
}

const openFilePicker = () => {
  fileInputRef.value?.click()
}

const onFileSelected = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  await uploadResume(file)
  target.value = ''
}

const onDragOver = (event: DragEvent) => {
  event.preventDefault()
  dragActive.value = true
}

const onDragLeave = () => {
  dragActive.value = false
}

const onDrop = async (event: DragEvent) => {
  event.preventDefault()
  dragActive.value = false
  const file = event.dataTransfer?.files?.[0]
  if (!file) return
  await uploadResume(file)
}

const validatePdf = (file: File): boolean => {
  const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
  if (!isPdf) {
    ElMessage.warning('仅支持上传 PDF 简历')
    return false
  }
  const max = 5 * 1024 * 1024
  if (file.size > max) {
    ElMessage.warning('文件不能超过 5MB')
    return false
  }
  return true
}

const uploadResume = async (file: File) => {
  if (!validatePdf(file) || uploading.value) return
  selectedFileName.value = file.name
  uploadProgress.value = 0
  uploading.value = true

  const formData = new FormData()
  formData.append('file', file)

  try {
    const res: any = await request.post('/api/resume/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
      onUploadProgress: (e: any) => {
        if (!e.total) return
        uploadProgress.value = Math.round((e.loaded / e.total) * 100)
      }
    })
    resumes.value.unshift(res.data)
    ElMessage.success('简历上传并解析成功')
  } finally {
    uploading.value = false
    selectedFileName.value = ''
    uploadProgress.value = 0
  }
}

const removeResume = async (id: number) => {
  await ElMessageBox.confirm('删除后将无法恢复，是否继续？', '删除简历确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await request.delete(`/api/resume/${id}`)
  resumes.value = resumes.value.filter((item) => item.resumeId !== id)
  ElMessage.success('已删除该简历')
}

const toggleExpand = (id: number) => {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id)
  } else {
    expandedIds.value.add(id)
  }
  expandedIds.value = new Set(expandedIds.value)
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}
</script>

<template>
  <div class="resume-page" :class="{ 'is-dark': isDark }">
    <header class="page-header">
      <div class="left">
        <el-button :icon="ArrowLeft" circle @click="router.push('/')" />
        <h2>简历管理</h2>
      </div>
      <el-tag type="success" effect="dark">AI 将基于此简历面试</el-tag>
    </header>

    <main class="page-main" v-loading="loading">
      <section
        class="upload-zone"
        :class="{ active: dragActive }"
        @dragover="onDragOver"
        @dragleave="onDragLeave"
        @drop="onDrop"
        @click="openFilePicker"
      >
        <input
          ref="fileInputRef"
          class="hidden-input"
          type="file"
          accept=".pdf,application/pdf"
          @change="onFileSelected"
        />
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <h3>拖拽 PDF 简历到此处，或点击上传</h3>
        <p>支持单个 PDF，最大 5MB。上传后将自动解析文本供 AI 面试官读取。</p>

        <div v-if="uploading" class="upload-progress">
          <div class="name">{{ selectedFileName }}</div>
          <el-progress :percentage="uploadProgress" :stroke-width="10" striped striped-flow />
        </div>
      </section>

      <section class="resume-list">
        <div class="section-title">我的简历</div>

        <el-empty
          v-if="sortedResumes.length === 0"
          description="还没有上传简历，先上传一份让 AI 针对你的经历进行面试。"
        />

        <div v-else class="cards">
          <el-card v-for="item in sortedResumes" :key="item.resumeId" class="resume-card" shadow="hover">
            <div class="card-head">
              <div class="file-meta">
                <el-icon><Document /></el-icon>
                <div>
                  <div class="name">{{ item.fileName }}</div>
                  <div class="sub">
                    {{ item.pageCount }} 页 · 字数 {{ item.charCount }} · 上传于 {{ formatTime(item.uploadedAt) }}
                  </div>
                </div>
              </div>
              <el-button type="danger" plain :icon="Delete" @click="removeResume(item.resumeId)">
                删除
              </el-button>
            </div>

            <div class="preview">
              <div class="label">文本预览</div>
              <p :class="{ expanded: expandedIds.has(item.resumeId) }">{{ item.previewText }}</p>
              <el-button type="primary" link @click="toggleExpand(item.resumeId)">
                {{ expandedIds.has(item.resumeId) ? '收起' : '查看全文提示片段' }}
              </el-button>
              <div class="chips" v-if="item.skills?.length || item.projects?.length || item.education?.length">
                <el-tag
                  v-for="(s, idx) in item.skills?.slice(0, 4)"
                  :key="`s-${idx}`"
                  size="small"
                  type="success"
                >
                  技能: {{ s }}
                </el-tag>
                <el-tag
                  v-for="(p, idx) in item.projects?.slice(0, 2)"
                  :key="`p-${idx}`"
                  size="small"
                  type="warning"
                >
                  项目: {{ p }}
                </el-tag>
                <el-tag
                  v-for="(e, idx) in item.education?.slice(0, 2)"
                  :key="`e-${idx}`"
                  size="small"
                >
                  教育: {{ e }}
                </el-tag>
              </div>
            </div>
          </el-card>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.resume-page {
  min-height: 100vh;
  background: #f5f7fa;
  color: #303133;
}

.is-dark.resume-page {
  background: #121212;
  color: #e5eaf3;
}

.page-header {
  height: 64px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}

.is-dark .page-header {
  background: #1e1e1e;
}

.left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.left h2 {
  margin: 0;
  font-size: 20px;
}

.page-main {
  max-width: 1100px;
  margin: 0 auto;
  padding: 28px 20px 40px;
}

.upload-zone {
  border: 2px dashed #a7f3d0;
  border-radius: 16px;
  background: #ecfdf5;
  padding: 36px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.upload-zone:hover,
.upload-zone.active {
  border-color: #10b981;
  transform: translateY(-1px);
}

.is-dark .upload-zone {
  background: #11261f;
}

.upload-icon {
  font-size: 40px;
  color: #10b981;
}

.upload-zone h3 {
  margin: 12px 0 8px;
}

.upload-zone p {
  margin: 0;
  color: #6b7280;
}

.is-dark .upload-zone p {
  color: #9ca3af;
}

.upload-progress {
  max-width: 520px;
  margin: 16px auto 0;
}

.upload-progress .name {
  margin-bottom: 8px;
  text-align: left;
  font-size: 13px;
}

.hidden-input {
  display: none;
}

.resume-list {
  margin-top: 26px;
}

.section-title {
  font-size: 18px;
  margin-bottom: 14px;
  font-weight: 600;
}

.cards {
  display: grid;
  gap: 14px;
}

.resume-card {
  border-radius: 12px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.file-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-meta .name {
  font-weight: 600;
}

.file-meta .sub {
  margin-top: 2px;
  font-size: 13px;
  color: #909399;
}

.preview {
  margin-top: 12px;
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}

.is-dark .preview {
  border-top-color: #333;
}

.preview .label {
  font-size: 13px;
  color: #909399;
}

.preview p {
  margin: 8px 0 0;
  white-space: pre-wrap;
  line-height: 1.6;
  max-height: 76px;
  overflow: hidden;
}

.preview p.expanded {
  max-height: none;
}

.chips {
  margin-top: 10px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
