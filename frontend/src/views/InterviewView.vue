<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDark } from '@vueuse/core'
import { ArrowLeft, Promotion } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

interface PositionItem {
  id: number
  name: string
  description?: string
}

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

const sessionId = ref<number | null>(null)
const positions = ref<PositionItem[]>([])
const resumes = ref<ResumeItem[]>([])
const turns = ref<any[]>([])
const userInput = ref('')
const isLoading = ref(false)
const isEnding = ref(false)
const chatBody = ref<HTMLElement | null>(null)

const currentStep = ref(1)
const selectedPositionId = ref<number | null>(null)
const selectedResumeId = ref<number | null>(null)
const boundResumeName = ref('')

const selectedPosition = computed(() =>
  positions.value.find((item) => item.id === selectedPositionId.value)
)
const selectedResume = computed(() =>
  resumes.value.find((item) => item.resumeId === selectedResumeId.value)
)

onMounted(async () => {
  await Promise.all([fetchPositions(), fetchResumes()])
})

const fetchPositions = async () => {
  const res: any = await request.get('/api/position/list')
  positions.value = res.data || []
}

const fetchResumes = async () => {
  const res: any = await request.get('/api/resume/me')
  resumes.value = res.data || []
}

const pickPosition = (id: number) => {
  selectedPositionId.value = id
}

const goStepTwo = () => {
  if (!selectedPositionId.value) {
    ElMessage.warning('请先选择岗位')
    return
  }
  currentStep.value = 2
}

const backToStepOne = () => {
  currentStep.value = 1
}

const chooseResume = (resumeId: number) => {
  selectedResumeId.value = resumeId
}

const startInterview = async () => {
  if (!selectedPositionId.value) {
    ElMessage.warning('请先选择岗位')
    return
  }
  if (selectedResume.value) {
    const skillPreview = (selectedResume.value.skills || []).slice(0, 4).join(' / ') || '未识别到明显技能关键词'
    const projectPreview = (selectedResume.value.projects || []).slice(0, 2).join('；') || '未识别到项目段落'
    const educationPreview = (selectedResume.value.education || []).slice(0, 2).join('；') || '未识别到教育信息'
    const previewText = selectedResume.value.previewText || ''
    try {
      await ElMessageBox.confirm(
        `简历：${selectedResume.value.fileName}

页数：${selectedResume.value.pageCount || 0} 页，字数：${selectedResume.value.charCount || 0}

技能提取：${skillPreview}
项目提取：${projectPreview}
教育提取：${educationPreview}

预览片段：
${previewText}

确认后，AI 将按以上解析结果和原始文本进行面试。`,
        '面试前简历确认',
        {
          confirmButtonText: '确认并开始',
          cancelButtonText: '返回修改',
          type: 'info'
        }
      )
    } catch {
      return
    }
  }
  isLoading.value = true
  try {
    const payload: any = { positionId: selectedPositionId.value }
    if (selectedResumeId.value) {
      payload.resumeId = selectedResumeId.value
    }
    const res: any = await request.post('/api/interview/sessions', payload)
    sessionId.value = res.data.sessionId
    turns.value = res.data.turns || []
    boundResumeName.value = res.data.resumeFileName || selectedResume.value?.fileName || ''
    currentStep.value = 3
    ElMessage.success('面试开始，祝你表现顺利！')
    scrollToBottom()
  } finally {
    isLoading.value = false
  }
}

const sendMessage = async () => {
  if (!userInput.value.trim() || isLoading.value || !sessionId.value) return
  const content = userInput.value.trim()
  userInput.value = ''
  isLoading.value = true

  const clientTurnId = crypto.randomUUID()
  const tempUserTurn = {
    role: 'USER',
    content,
    createTime: new Date().toISOString()
  }
  turns.value.push(tempUserTurn)
  scrollToBottom()

  try {
    const res: any = await request.post(
      `/api/interview/sessions/${sessionId.value}/turns`,
      { content, clientTurnId },
      { timeout: 60000 }
    )
    const index = turns.value.indexOf(tempUserTurn)
    if (index !== -1) turns.value.splice(index, 1)
    turns.value.push(res.data.userTurn)
    turns.value.push(res.data.interviewerTurn)
    scrollToBottom()
  } finally {
    isLoading.value = false
  }
}

const handleComplete = async () => {
  if (!sessionId.value) return
  try {
    await ElMessageBox.confirm('确定要结束本次模拟面试吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    isEnding.value = true
    await request.patch(`/api/interview/sessions/${sessionId.value}/complete`)
    ElMessage.success('面试已顺利完成！')
    router.push('/')
  } catch {
    // 用户取消
  } finally {
    isEnding.value = false
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatBody.value) {
      chatBody.value.scrollTop = chatBody.value.scrollHeight
    }
  })
}

const formatTime = (timeStr: string) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}
</script>

<template>
  <div class="interview-page" :class="{ 'is-dark': isDark }">
    <header class="interview-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" circle @click="router.push('/')" />
        <h3 v-if="sessionId">正在进行模拟面试</h3>
        <h3 v-else>模拟面试准备</h3>
      </div>
      <div class="header-right" v-if="sessionId">
        <el-tag v-if="boundResumeName" type="success">已绑定简历：{{ boundResumeName }}</el-tag>
        <el-button type="danger" plain @click="handleComplete" :loading="isEnding">结束面试</el-button>
      </div>
    </header>

    <main class="interview-main">
      <div v-if="!sessionId" class="selection-container">
        <el-steps :active="currentStep" simple class="stepper">
          <el-step title="选择岗位" />
          <el-step title="选择简历（可跳过）" />
          <el-step title="开始面试" />
        </el-steps>

        <div v-if="currentStep === 1">
          <div class="selection-title">Step 1：选择面试岗位</div>
          <el-empty description="暂无岗位数据" v-if="positions.length === 0" />
          <div class="position-grid">
            <el-card
              v-for="p in positions"
              :key="p.id"
              class="position-card"
              :class="{ selected: selectedPositionId === p.id }"
              shadow="hover"
              @click="pickPosition(p.id)"
            >
              <div class="pos-info">
                <h4>{{ p.name }}</h4>
                <p>{{ p.description || '暂无详细描述' }}</p>
              </div>
            </el-card>
          </div>
          <div class="actions">
            <el-button type="primary" @click="goStepTwo">下一步：选择简历</el-button>
          </div>
        </div>

        <div v-else>
          <div class="selection-title">Step 2：选择简历（可跳过）</div>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="绑定简历后，AI 会优先围绕你的项目经历和技术栈进行追问。"
          />
          <div v-if="resumes.length === 0" class="empty-resume">
            <el-empty description="你还没有上传简历，先去简历管理页上传也可以直接跳过。">
              <el-button @click="router.push('/resume')">去上传简历</el-button>
            </el-empty>
          </div>
          <div v-else class="resume-grid">
            <el-card
              v-for="item in resumes"
              :key="item.resumeId"
              class="resume-card"
              :class="{ selected: selectedResumeId === item.resumeId }"
              shadow="hover"
              @click="chooseResume(item.resumeId)"
            >
              <div class="resume-name">{{ item.fileName }}</div>
              <div class="resume-sub">
                {{ item.pageCount || 0 }} 页 · 字数 {{ item.charCount }} · {{ item.uploadedAt?.replace('T', ' ') }}
              </div>
              <p>{{ item.previewText }}</p>
            </el-card>
          </div>
          <div class="actions">
            <el-button @click="backToStepOne">上一步</el-button>
            <el-button @click="selectedResumeId = null">跳过简历</el-button>
            <el-button type="primary" :loading="isLoading" @click="startInterview">
              开始面试
            </el-button>
          </div>
        </div>
      </div>

      <div v-else class="chat-container">
        <div class="chat-body" ref="chatBody">
          <div
            v-for="(turn, index) in turns"
            :key="index"
            :class="['message-row', turn.role === 'INTERVIEWER' ? 'left' : 'right']"
          >
            <el-avatar :size="40" class="avatar">
              {{ turn.role === 'INTERVIEWER' ? 'AI' : '我' }}
            </el-avatar>
            <div class="message-content">
              <div class="message-bubble">
                <p class="bubble-text">{{ turn.content }}</p>
              </div>
              <span class="message-time">{{ formatTime(turn.createTime) }}</span>
            </div>
          </div>

          <div v-if="isLoading" class="message-row left">
            <el-avatar :size="40" class="avatar">AI</el-avatar>
            <div class="message-content">
              <div class="message-bubble typing">
                <span>.</span><span>.</span><span>.</span>
              </div>
            </div>
          </div>
        </div>

        <footer class="chat-footer">
          <div class="input-wrapper">
            <el-input
              v-model="userInput"
              type="textarea"
              :rows="2"
              placeholder="输入你的回答... (Ctrl+Enter 发送)"
              resize="none"
              :disabled="isLoading"
              @keydown.ctrl.enter="sendMessage"
            />
            <el-button type="primary" :icon="Promotion" class="send-btn" :loading="isLoading" @click="sendMessage">
              发送
            </el-button>
          </div>
          <div class="footer-tip">
            AI 面试官会结合岗位和（可选）简历内容动态追问，请认真作答。
          </div>
        </footer>
      </div>
    </main>
  </div>
</template>

<style scoped>
.interview-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
  color: #303133;
}

.is-dark.interview-page {
  background-color: #121212;
  color: #e5eaf3;
}

.interview-header {
  height: 60px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  z-index: 10;
}

.is-dark .interview-header {
  background: #1e1e1e;
  border-bottom: 1px solid #333;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-left h3 {
  margin: 0;
  font-size: 1.1rem;
}

.interview-main {
  flex: 1;
  overflow: hidden;
  display: flex;
  justify-content: center;
  padding: 20px;
}

.selection-container {
  width: 100%;
  max-width: 980px;
  overflow-y: auto;
}

.stepper {
  margin-bottom: 16px;
  border-radius: 10px;
}

.selection-title {
  margin: 10px 0 14px;
  font-size: 18px;
  font-weight: 600;
}

.position-grid,
.resume-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  margin-top: 14px;
}

.position-card,
.resume-card {
  cursor: pointer;
  border-radius: 12px;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.position-card.selected,
.resume-card.selected {
  border-color: #10b981;
  box-shadow: 0 0 0 1px #10b981 inset;
}

.position-card:hover,
.resume-card:hover {
  transform: translateY(-3px);
}

.pos-info h4 {
  margin: 0 0 10px;
  color: #10b981;
}

.pos-info p {
  font-size: 0.9rem;
  color: #606266;
  line-height: 1.5;
}

.resume-name {
  font-weight: 600;
}

.resume-sub {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}

.resume-card p {
  margin: 10px 0 0;
  font-size: 13px;
  color: #606266;
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.is-dark .pos-info p,
.is-dark .resume-card p {
  color: #9ca3af;
}

.actions {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.empty-resume {
  margin-top: 20px;
}

.chat-container {
  width: 100%;
  max-width: 1000px;
  height: 100%;
  background: #fff;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.is-dark .chat-container {
  background: #1e1e1e;
}

.chat-body {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  scroll-behavior: smooth;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message-row {
  display: flex;
  gap: 12px;
  max-width: 85%;
}

.message-row.left {
  align-self: flex-start;
}

.message-row.right {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.avatar {
  background-color: #10b981;
  flex-shrink: 0;
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-row.right .message-content {
  align-items: flex-end;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 0.95rem;
  line-height: 1.6;
  word-break: break-word;
}

.bubble-text {
  white-space: pre-wrap;
  margin: 0;
}

.left .message-bubble {
  background-color: #f4f4f5;
  border-bottom-left-radius: 2px;
}

.right .message-bubble {
  background-color: #10b981;
  color: #fff;
  border-bottom-right-radius: 2px;
}

.is-dark .left .message-bubble {
  background-color: #2c2c2c;
  color: #e5eaf3;
}

.message-time {
  font-size: 0.75rem;
  color: #909399;
}

.typing span {
  animation: blink 1.4s infinite both;
  font-size: 24px;
  line-height: 0;
  display: inline-block;
}

.typing span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  0% {
    opacity: 0.2;
  }
  20% {
    opacity: 1;
  }
  100% {
    opacity: 0.2;
  }
}

.chat-footer {
  padding: 20px;
  border-top: 1px solid #ebeef5;
}

.is-dark .chat-footer {
  border-top: 1px solid #333;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.send-btn {
  height: 52px;
  width: 90px;
}

.footer-tip {
  margin-top: 8px;
  font-size: 0.75rem;
  color: #909399;
  text-align: center;
}
</style>
