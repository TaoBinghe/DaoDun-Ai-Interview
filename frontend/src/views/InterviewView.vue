<template>
  <div class="flex flex-col items-center min-h-[calc(100vh-64px)] text-gray-400 relative pb-24 pt-8">
    <!-- 代码编辑器：弹窗模式 -->
    <div
      v-if="showCodeEditor && sessionId"
      class="fixed inset-0 z-50 bg-[#141413]/80 backdrop-blur-sm flex items-start justify-center pt-16 pb-28 px-4 md:pt-24 md:pb-32 md:px-8"
      @click.self="showCodeEditor = false"
    >
      <!-- 底部预留空间，避免被 Dock 遮挡 -->
      <div class="w-full max-w-6xl h-[72vh] rounded-2xl border border-[#faf9f5]/15 shadow-2xl overflow-hidden">
        <CodeEditorView
          :sessionId="sessionId"
          :currentQuestion="currentQuestionForEditor"
          :interviewMode="interviewMode"
          :videoRef="videoRef"
          :currentEmotion="currentEmotion"
          @submit-code="handleSubmitCode"
          @go-back="showCodeEditor = false"
        />
      </div>
    </div>

    <!-- 面试模式（代码编辑器隐藏时显示） -->
    <template v-if="!showCodeEditor">
    <!-- 顶部岗位切换 -->
    <div class="mb-12 flex justify-center w-full">
      <div
        v-if="positions.length"
        class="relative flex p-1 bg-[#1f1e1d] rounded-xl border border-[#faf9f5]/5"
      >
        <!-- 滑动背景 -->
        <div
          class="absolute inset-y-1 transition-all duration-300 ease-[cubic-bezier(0.23,1,0.32,1)] bg-[#2b2a27] rounded-lg shadow-sm"
          :style="activeTabStyle"
        ></div>

        <button
          v-for="(p, index) in positions"
          :key="p.id"
          :ref="el => setTabRef(el, index)"
          type="button"
          @click="handlePositionChange(p.id)"
          :class="[
            'relative z-10 px-6 py-2 text-sm font-medium transition-colors duration-200 cursor-pointer outline-none whitespace-nowrap',
            positionId == p.id ? 'text-[#faf9f5]' : 'text-gray-400 hover:text-[#faf9f5]/80'
          ]"
        >
          {{ p.name }}
        </button>
      </div>
    </div>

    <div class="flex-1 flex flex-col items-center justify-center w-full max-w-6xl px-4">
      <!-- 面试核心区域：大框 -->
      <!-- 语音面试模式 -->
      <div v-if="positionId && interviewMode === 'voice'" class="w-full flex flex-col gap-8 mb-8">
        <!-- 上半部分：左右两块画面 -->
        <div class="w-full grid grid-cols-1 md:grid-cols-2 gap-8">
          <!-- 左侧：AI 面试官形象 -->
          <div class="aspect-16/10 bg-[#1f1e1d] rounded-2xl border border-[#faf9f5]/10 flex flex-col items-center justify-center relative overflow-hidden group">
            <div class="w-32 h-32 rounded-full bg-[#2b2a27] flex items-center justify-center mb-4 transition-transform duration-500 group-hover:scale-110">
              <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="#6ef17d" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 8V4H8" />
                <rect width="16" height="12" x="4" y="8" rx="2" />
                <path d="M2 14h2" />
                <path d="M20 14h2" />
                <path d="M15 13v2" />
                <path d="M9 13v2" />
              </svg>
            </div>
            <span class="text-[#faf9f5]/60 text-sm font-medium">AI 面试官</span>
            <!-- 声波动画装饰 -->
            <div class="absolute bottom-4 flex gap-1 items-end h-8">
              <div v-for="i in 5" :key="i" class="w-1 bg-[#6ef17d]/40 rounded-full animate-pulse" :style="{ height: [40, 70, 50, 90, 60][i-1] + '%' }"></div>
            </div>
          </div>

          <!-- 右侧：视频画面 -->
          <div class="flex flex-col gap-4">
            <div class="aspect-16/10 bg-[#1f1e1d] rounded-2xl border border-[#faf9f5]/10 relative overflow-hidden">
              <!-- 情绪识别显示条 -->
              <div
                v-if="currentEmotion?.emotion && hasVideo"
                class="absolute top-0 left-0 right-0 z-10 bg-linear-to-b from-[#141413]/80 to-transparent backdrop-blur-sm px-4 py-3"
              >
                <div class="flex items-center justify-between mb-2">
                  <div class="flex items-center gap-2">
                    <span class="text-2xl">{{ emotionEmoji }}</span>
                    <span class="text-sm font-medium text-[#faf9f5]">{{ emotionText }}</span>
                  </div>
                </div>
                <!-- 置信度进度条 -->
                <div class="w-full bg-[#2b2a27] rounded-full h-2 overflow-hidden">
                  <div
                    class="h-full rounded-full transition-all duration-300"
                    :class="emotionColorClass"
                    :style="{ width: `${(currentEmotion.confidence || 0) * 100}%` }"
                  ></div>
                </div>
              </div>
              <!-- 无人脸提示 -->
              <div
                v-if="!currentEmotion?.hasFace && currentEmotion?.emotion"
                class="absolute top-0 left-0 right-0 z-10 bg-yellow-900/60 backdrop-blur-sm px-4 py-2 text-center text-xs text-yellow-200"
              >
                未检测到人脸
              </div>
              <video
                ref="videoRef"
                autoplay
                playsinline
                muted
                class="w-full h-full object-cover mirror"
              ></video>
              <!-- 无画面时的提示 -->
              <div v-if="!hasVideo" class="absolute inset-0 flex flex-col items-center justify-center text-gray-500 bg-[#1f1e1d]">
                <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="mb-2">
                  <path d="M23 7l-7 5 7 5V7z" />
                  <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
                </svg>
                <span class="text-xs">正在连接摄像头...</span>
                <div v-if="cameraError" class="mt-2 text-[11px] text-red-400 px-3 text-center leading-snug">
                  {{ cameraError }}
                </div>
                <button
                  v-if="cameraError"
                  type="button"
                  @click="retryVideo"
                  class="mt-3 py-2 px-4 text-[11px] rounded-lg bg-[#141413]/70 border border-[#faf9f5]/15 text-[#faf9f5] hover:bg-[#141413] transition-colors"
                >
                  重试
                </button>
              </div>
              <!-- 隐藏的 canvas 用于截屏 -->
              <canvas ref="captureCanvasRef" style="display: none;"></canvas>
            </div>
          </div>
        </div>

        <!-- 下半部分：左下字幕 + 右下输入/按钮（同一水平线） -->
        <div class="w-full grid grid-cols-1 md:grid-cols-2 gap-8 items-end">
          <div class="flex items-end">
            <transition name="fade">
              <div
                v-if="aiSubtitles"
                class="w-full rounded-xl border border-[#faf9f5]/10 bg-[#141413]/70 backdrop-blur px-4 py-3"
              >
                <p class="text-sm md:text-base text-[#faf9f5] leading-relaxed font-medium">
                  {{ aiSubtitles }}
                </p>
              </div>
            </transition>
          </div>

          <div class="flex justify-start md:justify-center gap-3 flex-wrap items-center">
            <!-- 开始面试：白色按钮 -->
            <button
              v-if="!sessionId"
              @click="startInterview"
              :disabled="isLoading"
              class="w-full md:w-auto md:min-w-[140px] py-4 px-6 rounded-xl flex items-center justify-center gap-3 transition-all duration-200 font-bold text-[#141413] bg-[#faf9f5] hover:bg-white active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {{ isLoading ? '正在准备...' : '开始面试' }}
            </button>
            <!-- 点击说话 + 结束面试：同一行 -->
            <template v-if="sessionId">
              <button
                @click="toggleVoice"
                :class="[
                  'py-4 px-6 rounded-xl flex items-center justify-center gap-3 transition-all duration-200 font-bold text-black active:scale-[0.98] md:w-56',
                  isSpeaking ? 'bg-[#5edb6b]' : 'bg-[#6ef17d] hover:bg-[#5edb6b]'
                ]"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z" />
                  <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
                  <line x1="12" x2="12" y1="19" y2="22" />
                </svg>
                {{ isSpeaking ? '停止说话' : '点击说话' }}
              </button>
              <button
                @click="endInterview"
                :disabled="isCompleting"
                class="py-4 px-6 rounded-xl flex items-center justify-center gap-3 transition-all duration-200 font-bold text-[#faf9f5] bg-[#1f1e1d] border border-[#faf9f5]/20 hover:bg-[#2b2a27] active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed md:min-w-[140px]"
              >
                结束面试
              </button>
            </template>
          </div>
        </div>
      </div>

      <!-- 文本面试模式 -->
      <div v-if="positionId && interviewMode === 'text'" class="w-full flex flex-col gap-6 mb-8">
        <!-- 如果没有开始面试，显示开始按钮 -->
        <div v-if="!sessionId" class="flex justify-center">
          <button
            @click="startInterview"
            :disabled="isLoading"
            class="w-full md:w-auto md:min-w-[140px] py-4 px-6 rounded-xl flex items-center justify-center gap-3 transition-all duration-200 font-bold text-[#141413] bg-[#faf9f5] hover:bg-white active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ isLoading ? '正在准备...' : '开始面试' }}
          </button>
        </div>

        <!-- 文本面试内容区域（只在开始面试后显示） -->
        <template v-if="sessionId">
          <!-- 聊天消息容器 -->
          <div
            ref="messagesContainerRef"
            data-text-messages
            class="w-full h-[500px] bg-[#1f1e1d] rounded-2xl border border-[#faf9f5]/10 overflow-y-auto flex flex-col gap-4 p-6"
          >
            <!-- 如果没有消息，显示欢迎信息 -->
            <div v-if="textMessages.length === 0" class="flex items-center justify-center h-full text-gray-500 text-center">
              <div>
                <p class="text-lg font-medium mb-2">开始对话</p>
                <p class="text-sm text-gray-400">在下方输入你的回答，与AI进行对话</p>
              </div>
            </div>

            <!-- 消息列表 -->
            <div v-for="(msg, index) in textMessages" :key="index" :class="['flex', msg.role === 'user' ? 'justify-end' : 'justify-start']">
              <div
                :class="[
                  'max-w-xs px-4 py-2 rounded-lg',
                  msg.role === 'user'
                    ? 'bg-[#6ef17d] text-[#141413] rounded-br-none'
                    : 'bg-[#2b2a27] text-[#faf9f5] rounded-bl-none'
                ]"
              >
                <p class="break-words text-sm">{{ msg.content }}</p>
              </div>
            </div>

            <!-- AI加载指示器 -->
            <div v-if="isTextLoading" class="flex justify-start">
              <div class="bg-[#2b2a27] text-[#faf9f5] px-4 py-2 rounded-lg rounded-bl-none">
                <div class="flex gap-1 items-center">
                  <div class="w-2 h-2 bg-[#6ef17d] rounded-full animate-bounce" style="animation-delay: 0ms"></div>
                  <div class="w-2 h-2 bg-[#6ef17d] rounded-full animate-bounce" style="animation-delay: 150ms"></div>
                  <div class="w-2 h-2 bg-[#6ef17d] rounded-full animate-bounce" style="animation-delay: 300ms"></div>
                </div>
              </div>
            </div>
          </div>

          <!-- 文本输入框 + 发送按钮 -->
          <div class="w-full flex gap-3">
            <input
              v-model="textInputValue"
              @keyup.enter="sendTextMessage"
              type="text"
              placeholder="输入你的回答..."
              class="flex-1 px-4 py-3 rounded-xl bg-[#2b2a27] border border-[#faf9f5]/15 text-[#faf9f5] placeholder-gray-500 outline-none focus:border-[#6ef17d] focus:border-opacity-50 transition-colors"
              :disabled="isTextLoading"
            />
            <button
              @click="sendTextMessage"
              :disabled="isTextLoading || !textInputValue.trim()"
              class="px-6 py-3 rounded-xl bg-[#6ef17d] text-[#141413] font-bold hover:bg-[#5edb6b] active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed transition-all"
            >
              发送
            </button>
            <button
              @click="endInterview"
              :disabled="isCompleting"
              class="px-6 py-3 rounded-xl flex items-center justify-center gap-3 transition-all duration-200 font-bold text-[#faf9f5] bg-[#1f1e1d] border border-[#faf9f5]/20 hover:bg-[#2b2a27] active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed md:min-w-[140px]"
            >
              结束面试
            </button>
          </div>
        </template>
      </div>

      <div v-if="!positionId" class="flex flex-col items-center">
        <h1 class="text-3xl font-medium text-[#faf9f5] mb-4">AI 面试</h1>
        <p>请从上方选择一个岗位开始面试。</p>
      </div>

      <!-- 错误提示 -->
      <div v-if="error" class="mt-8 text-center text-red-500">
        {{ error }}
      </div>

    </div>
    </template>
    
    <!-- 底部功能 Dock（代码编辑弹窗时也显示） -->
    <Dock :items="dockItems" v-if="positionId" class="fixed bottom-6 left-1/2 -translate-x-1/2 z-[60]" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, h, watch, nextTick, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import Dock from '../component/Dock/Dock.vue'
import CodeEditorView from './CodeEditorView.vue'
import { useUserStore } from '../stores/user'
import { unlockAudioForPlayback, playBase64Audio, speakTextFallback, PcmRecorder } from '../utils/audioUtils'
import { VoiceWebSocketClient } from '../services/voiceWebSocket'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

// 岗位相关
const positionId = computed(() => route.query.positionId)
const positionName = ref('')
const positions = ref<any[]>([])
const tabRefs = ref<HTMLElement[]>([])
const activeTabStyle = ref({
  left: '0px',
  width: '0px',
  opacity: 0
})

// 会话相关
const sessionId = ref<number | null>(null)
const isLoading = ref(false)
const isCompleting = ref(false)
const error = ref('')

// 交互相关
const aiSubtitles = ref('')

// 视频相关
const videoRef = ref<HTMLVideoElement | null>(null)
const captureCanvasRef = ref<HTMLCanvasElement | null>(null)
const hasVideo = ref(false)
const cameraError = ref('')
const isVideoInitializing = ref(false)
const isSpeaking = ref(false)

// 面试模式相关
const interviewMode = ref<'voice' | 'text'>('voice')
const textMessages = ref<Array<{ role: 'user' | 'assistant'; content: string; timestamp: number }>>([])
const textInputValue = ref('')
const isTextLoading = ref(false)

// 代码编辑器相关
const showCodeEditor = ref(false)
const currentQuestionForEditor = ref('')

// 情绪识别相关
const currentEmotion = ref({
  emotion: '',
  confidence: 0,
  hasFace: false,
  lastUpdate: 0
})
const emotionCaptureInterval = ref<number | null>(null)

// 情绪到 Emoji 的映射表
const emotionEmojiMap: Record<string, { emoji: string; chinese: string; color: string }> = {
  'happy': { emoji: '😊', chinese: '高兴', color: 'bg-yellow-500' },
  'sad': { emoji: '😢', chinese: '悲伤', color: 'bg-blue-500' },
  'neutral': { emoji: '😐', chinese: '中立', color: 'bg-gray-500' },
  'angry': { emoji: '😠', chinese: '愤怒', color: 'bg-red-500' },
  'surprise': { emoji: '😲', chinese: '惊讶', color: 'bg-purple-500' },
  'disgust': { emoji: '🤢', chinese: '厌恶', color: 'bg-green-500' }
}

// 计算属性：获取当前情绪的 Emoji 和颜色
const emotionEmoji = computed(() => {
  const info = emotionEmojiMap[currentEmotion.value.emotion] || emotionEmojiMap['neutral']!
  return info.emoji
})

// 显示中文情绪名：优先使用映射表；如果后端已传中文，则直接展示原始值
const emotionText = computed(() => {
  const raw = currentEmotion.value.emotion
  if (!raw) return ''
  const info = emotionEmojiMap[raw]
  if (info?.chinese) return info.chinese
  // 保底：若 raw 本身已经是中文，就不再映射/翻译
  if (/[\u4e00-\u9fff]/.test(raw)) return raw
  return raw
})

const emotionColorClass = computed(() => {
  const info = emotionEmojiMap[currentEmotion.value.emotion] || emotionEmojiMap['neutral']!
  return info.color
})

// 文本模式下的消息容器自动滚动到底部
const messagesContainerRef = ref<HTMLDivElement | null>(null)
const scrollToBottom = () => {
  if (messagesContainerRef.value) {
    messagesContainerRef.value.scrollTop = messagesContainerRef.value.scrollHeight
  }
}

// 监听消息列表变化，自动滚动到底部
watch(
  () => textMessages.value.length,
  () => {
    nextTick(() => {
      scrollToBottom()
    })
  }
)

// 语音 WebSocket（开场白 + 点击说话）
const voiceClient = ref<InstanceType<typeof VoiceWebSocketClient> | null>(null)
const pcmRecorder = ref<InstanceType<typeof PcmRecorder> | null>(null)

const getCameraErrorMessage = (err: any): string => {
  const name = err?.name || ''
  // 常见浏览器错误名：NotAllowedError / NotFoundError / NotReadableError / OverconstrainedError / SecurityError
  if (name === 'NotAllowedError') return '未获得摄像头权限。请允许权限后再重试。'
  if (name === 'NotFoundError') return '未检测到可用摄像头设备。请检查硬件/连接。'
  if (name === 'NotReadableError') return '摄像头被占用或不可读。请关闭其他占用软件后再试。'
  if (name === 'OverconstrainedError') return '摄像头参数不匹配。请更换摄像头或重试。'
  if (name === 'SecurityError') return '当前环境不安全（通常需要 HTTPS）。请在安全上下文下打开页面。'
  const msg = err?.message ? String(err.message) : ''
  return msg ? `无法访问摄像头：${msg}` : '无法访问摄像头，请检查权限/设置后重试。'
}

const startVideo = async (opts?: { userInitiated?: boolean }) => {
  if (isVideoInitializing.value) return
  isVideoInitializing.value = true
  cameraError.value = ''

  // 若之前已经拿到过流，先释放，避免“设备占用”
  stopVideo()

  try {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('当前浏览器不支持摄像头访问')
    }

    // 自动重试/用户重试次数上限，避免无限循环
    const maxRetries = opts?.userInitiated ? 1 : 2
    let attempt = 0

    while (true) {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: {
            // 优先前置摄像头（移动端），桌面端也相对更友好
            facingMode: 'user'
          },
          audio: false
        })

        if (videoRef.value) {
          videoRef.value.srcObject = stream
          videoRef.value.muted = true
          // 有些浏览器即使 autoplay 也需要 play() 来真正开始渲染
          await videoRef.value.play().catch(() => {})
          hasVideo.value = true
          cameraError.value = ''
          return
        }

        // 理论上不会到这里，但兜底处理
        throw new Error('video 元素尚未就绪')
      } catch (err: any) {
        console.error('Error accessing camera:', err)
        hasVideo.value = false
        cameraError.value = getCameraErrorMessage(err)

        const name = err?.name || ''
        // 对权限拒绝/安全上下文等不可恢复错误，不做自动重试
        const shouldAutoRetry = !['NotAllowedError', 'SecurityError'].includes(name)
        if (!shouldAutoRetry) return

        if (attempt >= maxRetries) return
        attempt += 1
        const delayMs = 800 * attempt
        await new Promise((r) => setTimeout(r, delayMs))
        // 继续下一次 while 尝试
      }
    }
  } finally {
    isVideoInitializing.value = false
  }
}

const retryVideo = async () => {
  await startVideo({ userInitiated: true })
}

const stopVideo = () => {
  if (videoRef.value && videoRef.value.srcObject) {
    const stream = videoRef.value.srcObject as MediaStream
    stream.getTracks().forEach(track => track.stop())
  }
  hasVideo.value = false
  cameraError.value = ''
}

const captureVideoFrame = (): string | null => {
  if (!videoRef.value || !captureCanvasRef.value) return null
  if (videoRef.value.readyState !== HTMLMediaElement.HAVE_ENOUGH_DATA) return null

  try {
    const canvas = captureCanvasRef.value
    const ctx = canvas.getContext('2d')
    if (!ctx) return null

    // 设置 canvas 尺寸与视频一致
    canvas.width = videoRef.value.videoWidth
    canvas.height = videoRef.value.videoHeight

    // 绘制视频帧到 canvas
    ctx.drawImage(videoRef.value, 0, 0)

    // 转换为 base64
    return canvas.toDataURL('image/jpeg', 0.8)
  } catch (err) {
    console.error('Error capturing video frame:', err)
    return null
  }
}

const startEmotionCapture = () => {
  if (emotionCaptureInterval.value !== null) return

  emotionCaptureInterval.value = window.setInterval(() => {
    if (!hasVideo.value || !sessionId.value || !voiceClient.value?.isOpen()) return

    const frameBase64 = captureVideoFrame()
    if (!frameBase64) return

    try {
      voiceClient.value?.sendEmotionFrame({
        sessionId: sessionId.value,
        imageBase64: frameBase64,
        capturedAt: Date.now()
      })
    } catch (err) {
      console.error('Error sending emotion frame:', err)
    }
  }, 1000)
}

const stopEmotionCapture = () => {
  if (emotionCaptureInterval.value !== null) {
    clearInterval(emotionCaptureInterval.value)
    emotionCaptureInterval.value = null
  }
  currentEmotion.value = {
    emotion: '',
    confidence: 0,
    hasFace: false,
    lastUpdate: 0
  }
}

const setTabRef = (el: any, index: number) => {
  if (el) {
    tabRefs.value[index] = el as HTMLElement
  }
}

const updateActiveTabStyle = () => {
  nextTick(() => {
    const activeIndex = positions.value.findIndex(p => p.id == positionId.value)
    if (activeIndex !== -1 && tabRefs.value[activeIndex]) {
      const el = tabRefs.value[activeIndex]
      activeTabStyle.value = {
        left: `${el.offsetLeft}px`,
        width: `${el.offsetWidth}px`,
        opacity: 1
      }
    } else {
      activeTabStyle.value = {
        left: '0px',
        width: '0px',
        opacity: 0
      }
    }
  })
}

const fetchPositions = async () => {
  try {
    const res = await request.get('/api/position/list') as any
    if (res.code === 200) {
      positions.value = res.data || []
      // 默认进入面试页时：若未指定岗位，则默认选择 Java 岗位
      if (!route.query.positionId && positions.value.length) {
        const java = positions.value.find(
          (item: any) =>
            typeof item?.name === 'string' &&
            (item.name.toLowerCase().includes('java') || item.name.includes('Java') || item.name.includes('后端'))
        )
        const fallback = positions.value[0]
        const target = java ?? fallback
        if (target?.id != null) {
          await router.replace({ name: 'interview', query: { positionId: String(target.id) } })
        }
      }

      const p = positions.value.find((item: any) => item.id == route.query.positionId)
      positionName.value = p?.name ?? ''
      updateActiveTabStyle()
    }
  } catch (e) {
    console.error(e)
  }
}

const handlePositionChange = async (id: number) => {
  if (pcmRecorder.value) {
    pcmRecorder.value.stop()
    pcmRecorder.value = null
  }
  isSpeaking.value = false
  stopEmotionCapture()
  voiceClient.value?.disconnect()
  voiceClient.value = null
  sessionId.value = null
  aiSubtitles.value = ''
  error.value = ''
  await router.push({ name: 'interview', query: { positionId: String(id) } })
  updateActiveTabStyle()
}

const startInterview = async () => {
  if (!positionId.value) return
  // 只在语音模式下才需要访问摄像头
  if (interviewMode.value === 'voice' && !hasVideo.value) {
    await retryVideo()
  }
  await createSession()
}

const createSession = async () => {
  if (!positionId.value) return
  
  isLoading.value = true
  error.value = ''
  
  try {
    const res = await request.post('/api/interview/sessions', {
      positionId: Number(positionId.value)
    }) as any
    
    if (res.code === 200 && res.data?.sessionId) {
      sessionId.value = res.data.sessionId
      
      // 根据当前模式选择初始化策略
      if (interviewMode.value === 'voice') {
        // 语音模式：启动摄像头、情绪识别、WebSocket和语音
        unlockAudioForPlayback()
        startEmotionCapture()

        // 通过 WebSocket 获取开场白 + 后续点击说话的语音回复（onVoiceMessage 见下方定义）
        const client = new VoiceWebSocketClient()
        voiceClient.value = client
        try {
          await client.connect(onVoiceMessage)
          client.sendPlayWelcome(sessionId.value!)
        } catch (wsErr: any) {
          console.warn('WebSocket 开场白失败，改用 HTTP + 浏览器朗读:', wsErr)
          voiceClient.value = null
          const welcomeRes = await request.get(`/api/interview/welcome/${sessionId.value}`) as any
          if (welcomeRes.code === 200) {
            const welcomeText = welcomeRes.data?.content || '你好，很高兴能面试你。请先做一个简单的自我介绍吧。'
            aiSubtitles.value = welcomeText
            nextTick(() => speakTextFallback(welcomeText))
          }
        }
      } else if (interviewMode.value === 'text') {
        // 文本模式：只连接WebSocket，获取开场白显示在聊天框
        const client = new VoiceWebSocketClient()
        voiceClient.value = client
        try {
          await client.connect(onVoiceMessage)
          // 在文本模式下，也需要发送play_welcome来获取开场白
          client.sendPlayWelcome(sessionId.value!)
        } catch (wsErr: any) {
          console.warn('WebSocket 连接失败，改用 HTTP:', wsErr)
          voiceClient.value = null
          const welcomeRes = await request.get(`/api/interview/welcome/${sessionId.value}`) as any
          if (welcomeRes.code === 200) {
            const welcomeText = welcomeRes.data?.content || '你好，很高兴能面试你。请先做一个简单的自我介绍吧。'
            // 在文本模式下，将开场白添加到聊天消息列表
            textMessages.value.push({
              role: 'assistant',
              content: welcomeText,
              timestamp: Date.now()
            })
          }
        }
      }
    } else {
      error.value = res.msg || '创建会话失败'
    }
  } catch (err: any) {
    console.error('Error creating session:', err)
    error.value = err?.message || '创建会话失败，请重试'
    if (err?.response?.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      router.push('/login')
    }
  } finally {
    isLoading.value = false
  }
}

const onVoiceMessage = (msg: any) => {
  if (msg.type === 'subtitle' && msg.content) {
    aiSubtitles.value = msg.content
    // 如果当前在文本模式，也添加到聊天列表
    if (interviewMode.value === 'text') {
      textMessages.value.push({
        role: 'assistant',
        content: msg.content,
        timestamp: Date.now()
      })
      isTextLoading.value = false
      // 自动滚动到底部
      nextTick(() => {
        scrollToBottom()
      })
    }
  }
  if (msg.type === 'interviewer_audio' && msg.data) {
    playBase64Audio(msg.data, msg.mimeType || 'audio/mpeg').catch((e) => {
      console.warn('播放面试官音频失败，改用浏览器朗读:', e)
      if (aiSubtitles.value) speakTextFallback(aiSubtitles.value)
    })
  }
  if (msg.type === 'emotion_status') {
    currentEmotion.value = {
      emotion: msg.emotion || '',
      confidence: msg.confidence || 0,
      hasFace: msg.hasFace ?? false,
      lastUpdate: Date.now()
    }
  }
  if (msg.type === 'error' && msg.content) ElMessage.warning(msg.content)
}

const ensureVoiceClient = async (): Promise<boolean> => {
  if (voiceClient.value?.isOpen()) return true
  const client = new VoiceWebSocketClient()
  voiceClient.value = client
  try {
    await client.connect(onVoiceMessage)
    return true
  } catch (e) {
    console.error('WebSocket 连接失败:', e)
    voiceClient.value = null
    return false
  }
}

const toggleVoice = async () => {
  if (!sessionId.value) return
  if (isSpeaking.value) {
    // 停止说话：停止录音并提交给后端做 STT + LLM + TTS
    if (pcmRecorder.value) {
      pcmRecorder.value.stop()
      pcmRecorder.value = null
    }
    voiceClient.value?.commitAudio(sessionId.value, undefined, 'pcm')
    isSpeaking.value = false
    return
  }
  // 开始说话：确保 WebSocket 连接，启动 PCM 录音并实时发送
  const ok = await ensureVoiceClient()
  if (!ok) {
    ElMessage.warning('语音连接失败，请重试或先重新开始面试')
    return
  }
  const recorder = new PcmRecorder()
  pcmRecorder.value = recorder
  try {
    await recorder.start((chunk) => {
      voiceClient.value?.sendAudioChunk({
        sessionId: sessionId.value!,
        data: chunk.base64,
        format: 'pcm'
      })
    })
    isSpeaking.value = true
  } catch (e: any) {
    console.error('启动录音失败:', e)
    pcmRecorder.value = null
    ElMessage.warning(e?.message || '无法访问麦克风，请检查权限')
  }
}

const endInterview = async () => {
  if (!sessionId.value || isCompleting.value) return
  isCompleting.value = true
  error.value = ''
  if (pcmRecorder.value) {
    pcmRecorder.value.stop()
    pcmRecorder.value = null
  }
  isSpeaking.value = false
  stopEmotionCapture()
  voiceClient.value?.disconnect()
  voiceClient.value = null
  try {
    await request.patch(`/api/interview/sessions/${sessionId.value}/complete`, {})
    ElMessage.success('面试已结束')
    sessionId.value = null
    aiSubtitles.value = ''
  } catch (err: any) {
    error.value = err?.message || '结束面试失败，请重试'
    ElMessage.error(error.value)
  } finally {
    isCompleting.value = false
  }
}

const switchInterviewMode = async (mode: 'voice' | 'text') => {
  if (interviewMode.value === mode) return
  
  // 清理当前模式的资源
  if (interviewMode.value === 'voice') {
    // 切到文本面试时：彻底停止语音相关，避免并发提交/继续处理语音事件
    if (pcmRecorder.value) {
      // 不提交最后一段语音，避免“语音仍参与对话”
      pcmRecorder.value.stop()
      pcmRecorder.value = null
    }
    isSpeaking.value = false
    voiceClient.value?.disconnect()
    voiceClient.value = null
    stopEmotionCapture()
    stopVideo()
  } else if (interviewMode.value === 'text') {
    textMessages.value = []
    textInputValue.value = ''
  }
  
  // 切换模式
  interviewMode.value = mode
  
  // 初始化新模式
  if (mode === 'voice') {
    await startVideo()
    startEmotionCapture()
    // 文本切回语音时，若之前断开过 websocket，需要重新连接
    if (sessionId.value && !voiceClient.value?.isOpen()) {
      await ensureVoiceClient()
    }
  }
  // 文本模式无需额外初始化，WebSocket保持开放
}

const sendTextMessage = async () => {
  const text = textInputValue.value.trim()
  if (!text || !sessionId.value || isTextLoading.value) return
  
  // 添加用户消息到列表
  textMessages.value.push({
    role: 'user',
    content: text,
    timestamp: Date.now()
  })
  textInputValue.value = ''
  isTextLoading.value = true
  
  try {
    // 优先尝试使用WebSocket
    if (voiceClient.value?.isOpen()) {
      voiceClient.value.sendTextAnswer({
        sessionId: sessionId.value!,
        content: text
      })
      console.log('文本消息已通过WebSocket发送:', text)
    } else {
      // WebSocket不可用，尝试重新连接
      const ok = await ensureVoiceClient()
      if (ok) {
        voiceClient.value!.sendTextAnswer({
          sessionId: sessionId.value!,
          content: text
        })
        console.log('文本消息已通过新连接的WebSocket发送:', text)
      } else {
        // WebSocket连接失败，改用HTTP备用方案
        console.warn('WebSocket不可用，使用HTTP备用方案')
        try {
          const res = await request.post(`/api/interview/text-answer/${sessionId.value}`, {
            content: text
          }) as any
          
          if (res.code === 200) {
            console.log('文本消息已通过HTTP发送')
            // HTTP方案下，还需要获取AI的回复
            // 这里可能需要后端提供额外的API或WebSocket重连机制
          } else {
            ElMessage.error('发送消息失败：' + (res.msg || '未知错误'))
            isTextLoading.value = false
          }
        } catch (httpErr: any) {
          console.error('HTTP发送失败:', httpErr)
          ElMessage.error('发送消息失败，请检查网络连接')
          isTextLoading.value = false
        }
      }
    }
  } catch (err: any) {
    console.error('发送文本消息异常:', err)
    ElMessage.error('发送消息失败：' + (err?.message || '未知错误'))
    isTextLoading.value = false
  }
}

watch(
  () => route.query.positionId,
  () => {
    const p = positions.value.find((item: any) => item.id == route.query.positionId)
    positionName.value = p?.name ?? ''
    updateActiveTabStyle()
  }
)

onMounted(() => {
  fetchPositions()
  if (userStore && !userStore.user) {
    userStore.fetchUser()
  }
  startVideo()
})

onUnmounted(() => {
  if (pcmRecorder.value) {
    pcmRecorder.value.stop()
    pcmRecorder.value = null
  }
  stopEmotionCapture()
  voiceClient.value?.disconnect()
  voiceClient.value = null
  stopVideo()
})

const handleSubmitCode = (code: string) => {
  // 代码提交转为文本消息，通过现有的消息发送流程
  if (!code.trim() || !sessionId.value) return
  
  // 添加用户消息到列表
  textMessages.value.push({
    role: 'user',
    content: `\`\`\`\n${code}\n\`\`\``,
    timestamp: Date.now()
  })
  
  isTextLoading.value = true
  
  // 通过WebSocket发送代码
  if (voiceClient.value?.isOpen()) {
    voiceClient.value.sendTextAnswer({
      sessionId: sessionId.value!,
      content: code
    })
  } else {
    // 尝试重新连接后发送
    ensureVoiceClient().then((ok) => {
      if (ok) {
        voiceClient.value!.sendTextAnswer({
          sessionId: sessionId.value!,
          content: code
        })
      } else {
        // 使用HTTP备用方案
        request.post(`/api/interview/text-answer/${sessionId.value}`, {
          content: code
        }).catch((err) => {
          console.error('代码提交失败:', err)
          ElMessage.error('代码提交失败，请重试')
          isTextLoading.value = false
        })
      }
    })
  }
  
  // 返回面试视图
  showCodeEditor.value = false
}

const handleOpenCodeEditor = (question: string) => {
  const q = (question || '').trim()
  if (q && q !== '// 打开代码编辑器...') {
    currentQuestionForEditor.value = q
    showCodeEditor.value = true
    return
  }

  // 兜底：从最近一条 AI 发言提取题干（语音用字幕，文本用聊天最后一条 assistant）
  let source = ''
  if (interviewMode.value === 'voice') {
    source = (aiSubtitles.value || '').trim()
  } else {
    for (let i = textMessages.value.length - 1; i >= 0; i--) {
      const m = textMessages.value[i]
      if (m && m.role === 'assistant') {
        source = (m.content || '').trim()
        break
      }
    }
  }

  // 常见格式：过渡语 + 空行 + 题目，把最后一段当题干
  if (source.includes('\n\n')) {
    source = source.split('\n\n').pop()!.trim()
  }
  currentQuestionForEditor.value = source || '（未获取到题目，请等待 AI 出题后再打开代码编辑）'
  showCodeEditor.value = true
}

const dockItems = [
  {
    label: '语音面试',
    icon: () => h('svg', { xmlns: 'http://www.w3.org/2000/svg', width: '24', height: '24', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [
      h('path', { d: 'M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z' }),
      h('path', { d: 'M19 10v2a7 7 0 0 1-14 0v-2' }),
      h('line', { x1: '12', x2: '12', y1: '19', y2: '22' })
    ]),
    onClick: () => switchInterviewMode('voice')
  },
  {
    label: '文本面试',
    icon: () => h('svg', { xmlns: 'http://www.w3.org/2000/svg', width: '24', height: '24', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [
      h('path', { d: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z' })
    ]),
    onClick: () => switchInterviewMode('text')
  },
  {
    label: '代码编辑',
    icon: () => h('svg', { xmlns: 'http://www.w3.org/2000/svg', width: '24', height: '24', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [
      h('path', { d: 'M9.5 2H4a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9.5' }),
      h('path', { d: 'M9 6h6v6H9z' }),
      h('circle', { cx: '18', cy: '9', r: '3' })
    ]),
    onClick: () => handleOpenCodeEditor(currentQuestionForEditor.value || '// 打开代码编辑器...')
  }
]

</script>

<style scoped>
.mirror {
  transform: scaleX(-1);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.5s ease, transform 0.5s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
