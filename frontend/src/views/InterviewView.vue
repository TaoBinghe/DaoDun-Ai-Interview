<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDark } from '@vueuse/core'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import VoiceControls from '../components/VoiceControls.vue'
import SubtitleDisplay from '../components/SubtitleDisplay.vue'
import { PcmRecorder, playBase64Audio, speakTextFallback, unlockAudioForPlayback } from '../utils/audioUtils'
import { VoiceWebSocketClient, type VoiceServerMessage } from '../services/voiceWebSocket'

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
const isLoading = ref(false)
const isEnding = ref(false)
const chatBody = ref<HTMLElement | null>(null)
const voiceMode = ref<'voice'>('voice')
const subtitleText = ref('')
const isVoiceConnecting = ref(false)
const isRecording = ref(false)
const pendingInterviewerText = ref('')
const currentVoiceTurnId = ref<string | null>(null)
const recordingStartedAt = ref<number | null>(null)
const cameraVideoRef = ref<HTMLVideoElement | null>(null)
const captureCanvasRef = ref<HTMLCanvasElement | null>(null)
const cameraStream = ref<MediaStream | null>(null)
const cameraError = ref('')
const hasFaceDetected = ref(false)
const emotionStatus = ref('idle')
const currentEmotion = ref('')
const currentEmotionConfidence = ref<number | null>(null)
const welcomeRequestedSessionId = ref<number | null>(null)
let pendingAudioFallbackTimer: ReturnType<typeof setTimeout> | null = null
let emotionFrameTimer: ReturnType<typeof setInterval> | null = null

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

const audioRecorder = new PcmRecorder()
const voiceWs = new VoiceWebSocketClient()
const emotionLabelMap: Record<string, string> = {
  anger: '愤怒',
  disgust: '厌恶',
  happy: '高兴',
  neutral: '中性',
  sad: '悲伤',
  surprise: '惊讶'
}

const appendTurnIfNeeded = (role: 'USER' | 'INTERVIEWER', content: string) => {
  const normalized = content.trim()
  if (!normalized) return false
  const lastTurn = turns.value[turns.value.length - 1]
  if (
    lastTurn &&
    lastTurn.role === role &&
    typeof lastTurn.content === 'string' &&
    lastTurn.content.trim() === normalized
  ) {
    return false
  }
  turns.value.push({
    role,
    content: normalized,
    createTime: new Date().toISOString()
  })
  return true
}

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
  unlockAudioForPlayback()
  try {
    const payload: any = { positionId: selectedPositionId.value }
    if (selectedResumeId.value) {
      payload.resumeId = selectedResumeId.value
    }
    const res: any = await request.post('/api/interview/sessions', payload)
    sessionId.value = res.data.sessionId
    welcomeRequestedSessionId.value = null
    turns.value = res.data.turns || []
    boundResumeName.value = res.data.resumeFileName || selectedResume.value?.fileName || ''
    currentStep.value = 3
    await setupVoiceChannel()
    await startCameraStream()
    ElMessage.success('面试开始，祝你表现顺利！')
    scrollToBottom()
  } catch (e) {
    isLoading.value = false
    throw e
  }
}

const setupVoiceChannel = async () => {
  unlockAudioForPlayback()
  if (voiceWs.isOpen()) {
    isVoiceConnecting.value = false
    return true
  }
  try {
    isVoiceConnecting.value = true
    await voiceWs.connect(onVoiceMessage, () => {
      isRecording.value = false
      isVoiceConnecting.value = false
      isLoading.value = false
      pendingInterviewerText.value = ''
      stopEmotionSampling()
    })
    isVoiceConnecting.value = false
    return true
  } catch (e: any) {
    isVoiceConnecting.value = false
    ElMessage.warning(e?.message || '语音通道连接失败，请检查后端语音服务')
    return false
  }
}

const onVoiceMessage = async (msg: VoiceServerMessage) => {
  if (msg.type === 'connected') {
    isVoiceConnecting.value = false
    if (sessionId.value && welcomeRequestedSessionId.value !== sessionId.value) {
      try {
        isLoading.value = true
        welcomeRequestedSessionId.value = sessionId.value
        voiceWs.sendPlayWelcome(sessionId.value)
      } catch {
        isLoading.value = false
        welcomeRequestedSessionId.value = null
      }
    }
    return
  }
  if (msg.type === 'error') {
    isLoading.value = false
    pendingInterviewerText.value = ''
    ElMessage.error(msg.content || '语音服务异常')
    return
  }
  if (msg.type === 'user_transcript' && msg.content) {
    isLoading.value = true
    appendTurnIfNeeded('USER', msg.content)
    scrollToBottom()
    return
  }
  if (msg.type === 'interviewer_text_delta' && msg.content) {
    // 语音主通道下不再渲染面试官文本增量
    return
  }
  if (msg.type === 'interviewer_text' && msg.content) {
    // 语音主通道下不再渲染面试官文本气泡；仅用于兜底播报
    if (pendingAudioFallbackTimer) {
      clearTimeout(pendingAudioFallbackTimer)
      pendingAudioFallbackTimer = null
    }
    subtitleText.value = msg.content
    pendingAudioFallbackTimer = setTimeout(() => {
      if (voiceMode.value === 'voice') {
        speakTextFallback(msg.content || '')
      }
      pendingAudioFallbackTimer = null
    }, 1200)
    return
  }
  if (msg.type === 'subtitle') {
    subtitleText.value = msg.content || ''
    isLoading.value = false
    if (pendingAudioFallbackTimer) {
      clearTimeout(pendingAudioFallbackTimer)
      pendingAudioFallbackTimer = null
    }
    pendingAudioFallbackTimer = setTimeout(() => {
      if (voiceMode.value === 'voice' && subtitleText.value) {
        speakTextFallback(subtitleText.value)
      }
      pendingAudioFallbackTimer = null
    }, 1500)
    return
  }
  if (msg.type === 'interviewer_audio' && msg.data) {
    if (pendingAudioFallbackTimer) {
      clearTimeout(pendingAudioFallbackTimer)
      pendingAudioFallbackTimer = null
    }
    pendingInterviewerText.value = ''
    isLoading.value = false
    try {
      await playBase64Audio(msg.data, msg.mimeType || 'audio/wav')
    } catch (e: any) {
      ElMessage.warning(e?.message || '语音播放失败，请点击页面后重试')
    }
    return
  }
  if (msg.type === 'emotion_status') {
    emotionStatus.value = msg.status || 'ok'
    hasFaceDetected.value = !!msg.hasFace
    currentEmotion.value = msg.emotion || ''
    currentEmotionConfidence.value = typeof msg.confidence === 'number' ? msg.confidence : null
  }
}

const stopEmotionSampling = () => {
  if (emotionFrameTimer) {
    clearInterval(emotionFrameTimer)
    emotionFrameTimer = null
  }
}

const stopCameraStream = () => {
  stopEmotionSampling()
  if (cameraStream.value) {
    cameraStream.value.getTracks().forEach((track) => track.stop())
    cameraStream.value = null
  }
  if (cameraVideoRef.value) {
    cameraVideoRef.value.srcObject = null
  }
}

const sendEmotionFrame = () => {
  if (!sessionId.value || !voiceWs.isOpen()) return
  const video = cameraVideoRef.value
  const canvas = captureCanvasRef.value
  if (!video || !canvas) return
  if (video.readyState < 2 || video.videoWidth === 0 || video.videoHeight === 0) return

  const width = 192
  const height = Math.max(108, Math.round((video.videoHeight / video.videoWidth) * width))
  canvas.width = width
  canvas.height = height

  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.drawImage(video, 0, 0, width, height)

  try {
    const dataUrl = canvas.toDataURL('image/jpeg', 0.55)
    const imageBase64 = dataUrl.includes(',') ? dataUrl.split(',', 2)[1] : dataUrl
    voiceWs.sendEmotionFrame({
      sessionId: sessionId.value,
      imageBase64,
      capturedAt: Date.now()
    })
  } catch (e) {
    console.warn('emotion frame send failed', e)
  }
}

const startEmotionSampling = () => {
  if (emotionFrameTimer) return
  emotionFrameTimer = setInterval(() => {
    sendEmotionFrame()
  }, 1000)
}

const startCameraStream = async () => {
  if (cameraStream.value) return true
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    cameraError.value = '当前浏览器不支持摄像头访问'
    emotionStatus.value = 'unsupported'
    return false
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false })
    cameraStream.value = stream
    cameraError.value = ''
    emotionStatus.value = 'ok'
    await nextTick()
    if (cameraVideoRef.value) {
      cameraVideoRef.value.srcObject = stream
      await cameraVideoRef.value.play().catch(() => {})
    }
    startEmotionSampling()
    return true
  } catch (e: any) {
    cameraError.value = e?.message || '无法访问摄像头，请检查权限'
    emotionStatus.value = 'error'
    return false
  }
}

const startVoiceAnswer = async () => {
  if (!sessionId.value) return
  try {
    const connected = await setupVoiceChannel()
    if (!connected || !voiceWs.isOpen()) {
      ElMessage.warning('语音通道尚未就绪，请稍后重试')
      return
    }
    pendingInterviewerText.value = ''
    currentVoiceTurnId.value = crypto.randomUUID()
    await audioRecorder.start((chunk) => {
      if (!sessionId.value || !voiceWs.isOpen()) return
      try {
        voiceWs.sendAudioChunk({
          sessionId: sessionId.value,
          data: chunk.base64,
          format: chunk.format ?? 'pcm',
          finalChunk: false,
          clientTurnId: currentVoiceTurnId.value || undefined
        })
      } catch (e) {
        console.warn('voice chunk send failed', e)
      }
    })
    isRecording.value = true
    recordingStartedAt.value = Date.now()
  } catch (e: any) {
    ElMessage.error(e?.message || '无法启动录音，请检查麦克风权限')
  }
}

const stopVoiceAnswer = async () => {
  if (!sessionId.value) return
  if (!isRecording.value) return
  audioRecorder.stop()
  isRecording.value = false
  const durationMs = recordingStartedAt.value ? Date.now() - recordingStartedAt.value : 0
  recordingStartedAt.value = null
  if (durationMs > 0 && durationMs < 800) {
    ElMessage.warning('录音时间过短，请至少说 1 秒再停止')
    return
  }
  try {
    const connected = await setupVoiceChannel()
    if (!connected || !voiceWs.isOpen()) {
      ElMessage.error('语音通道未连接，请稍后重试')
      return
    }
    isLoading.value = true
    pendingInterviewerText.value = ''
    voiceWs.commitAudio(sessionId.value, currentVoiceTurnId.value || crypto.randomUUID(), 'pcm')
    currentVoiceTurnId.value = null
  } catch (e: any) {
    isLoading.value = false
    ElMessage.error(e?.message || '语音发送失败，请重试')
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
    voiceWs.disconnect()
    audioRecorder.stop()
    stopCameraStream()
    welcomeRequestedSessionId.value = null
    router.push('/')
  } catch {
    // 用户取消
  } finally {
    isEnding.value = false
  }
}

onUnmounted(() => {
  if (pendingAudioFallbackTimer) {
    clearTimeout(pendingAudioFallbackTimer)
    pendingAudioFallbackTimer = null
  }
  voiceWs.disconnect()
  audioRecorder.stop()
  stopCameraStream()
  welcomeRequestedSessionId.value = null
})

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

const emotionDisplay = computed(() => {
  if (!currentEmotion.value) return '等待识别'
  return emotionLabelMap[currentEmotion.value] || currentEmotion.value
})
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
        <section class="camera-panel">
          <video ref="cameraVideoRef" class="camera-preview" autoplay playsinline muted />
          <canvas ref="captureCanvasRef" class="capture-canvas" />
          <div class="camera-meta">
            <span v-if="cameraError" class="camera-error">{{ cameraError }}</span>
            <span v-else>摄像头已开启，用于实时情绪识别</span>
          </div>
        </section>
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

          <div v-if="pendingInterviewerText" class="message-row left">
            <el-avatar :size="40" class="avatar">AI</el-avatar>
            <div class="message-content">
              <div class="message-bubble">
                <p class="bubble-text">{{ pendingInterviewerText }}</p>
              </div>
              <span class="message-time">生成中...</span>
            </div>
          </div>

          <div v-if="isLoading && !pendingInterviewerText" class="message-row left">
            <el-avatar :size="40" class="avatar">AI</el-avatar>
            <div class="message-content">
              <div class="message-bubble typing">
                <span>.</span><span>.</span><span>.</span>
              </div>
            </div>
          </div>
        </div>

        <footer class="chat-footer">
          <div class="emotion-status">
            <el-tag size="large" :type="hasFaceDetected ? 'success' : 'warning'">
              {{ hasFaceDetected ? '检测到人脸' : '未检测到人脸' }}
            </el-tag>
            <span class="emotion-text">当前情绪：{{ emotionDisplay }}</span>
            <span class="emotion-text" v-if="currentEmotionConfidence !== null">
              置信度：{{ (currentEmotionConfidence * 100).toFixed(1) }}%
            </span>
            <span class="emotion-text" v-if="emotionStatus === 'error'">情绪服务暂不可用</span>
          </div>
          <div class="input-wrapper">
            <VoiceControls
              :recording="isRecording"
              :connecting="isVoiceConnecting"
              @start="startVoiceAnswer"
              @stop="stopVoiceAnswer"
            />
          </div>
          <SubtitleDisplay :text="subtitleText" />
          <div class="footer-tip">
            仅保留语音面试模式：点击开始后直接语音作答，AI 仅通过语音与字幕反馈。
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

.camera-panel {
  padding: 12px 20px 0;
}

.camera-preview {
  width: 220px;
  height: 124px;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  object-fit: cover;
  background: #111;
}

.capture-canvas {
  display: none;
}

.camera-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.camera-error {
  color: #ef4444;
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

.emotion-status {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.emotion-text {
  font-size: 13px;
  color: #606266;
}

.is-dark .emotion-text {
  color: #9ca3af;
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
