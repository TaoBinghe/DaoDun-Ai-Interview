<template>
  <div class="flex flex-col items-center min-h-[calc(100vh-64px)] text-gray-400 relative pb-24 pt-8">
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
      <div v-if="positionId" class="w-full flex flex-col gap-8 mb-8">
        <!-- 语音面试模式 -->
        <template v-if="interviewMode === 'voice'">
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
                </div>
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
        </template>

        <!-- 文字面试模式（尺寸固定，通过底部 Dock 切换进入） -->
        <template v-if="interviewMode === 'text'">
          <div class="w-full max-w-4xl mx-auto flex flex-col gap-6 shrink-0">
            <TextInterviewChat
              :messages="textMessages"
              :loading="isTextWaiting"
              :disabled="!sessionId || isTextWaiting"
              @send="handleTextSend"
            />
            <div class="flex justify-center gap-3 flex-wrap items-center">
              <button
                v-if="!sessionId"
                type="button"
                @click="startInterview"
                :disabled="isLoading"
                class="w-full sm:w-auto sm:min-w-[160px] py-4 px-8 rounded-xl flex items-center justify-center gap-3 transition-all duration-200 font-bold text-[#141413] bg-[#faf9f5] hover:bg-white active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {{ isLoading ? '正在准备...' : '开始面试' }}
              </button>
              <button
                v-if="sessionId"
                type="button"
                @click="endInterview"
                :disabled="isCompleting"
                class="w-full sm:w-auto sm:min-w-[160px] py-4 px-8 rounded-xl flex items-center justify-center gap-3 transition-all duration-200 font-bold text-[#faf9f5] bg-[#1f1e1d] border border-[#faf9f5]/20 hover:bg-[#2b2a27] active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed"
              >
                结束面试
              </button>
            </div>
          </div>
        </template>

        <!-- 代码编写模式 -->
        <template v-if="interviewMode === 'coding'">
          <!-- flex + min-h-0：标题栏与编辑器分区高度，避免 h-full 与标题栏叠加导致底部按钮被裁切 -->
          <div
            class="w-full max-w-6xl mx-auto flex flex-col h-[min(680px,calc(100vh-10rem))] min-h-[480px] rounded-3xl overflow-hidden border border-[#faf9f5]/10 shadow-2xl bg-[#141413] relative"
          >
            <!-- macOS 风格窗口标题栏 -->
            <div class="h-11 shrink-0 bg-[#1f1e1d] flex items-center px-4 border-b border-[#faf9f5]/10">
              <div class="flex gap-2">
                <div class="w-3 h-3 rounded-full bg-[#ff5f57]"></div>
                <div class="w-3 h-3 rounded-full bg-[#ffbc2e]"></div>
                <div class="w-3 h-3 rounded-full bg-[#28c840]"></div>
              </div>
              <div class="flex-1 text-center text-xs text-gray-400 font-medium">算法题 - 代码编写</div>
            </div>

            <div class="flex-1 min-h-0 overflow-hidden">
              <CodeEditorView
                :session-id="sessionId || 0"
                :current-question="currentAlgorithmQuestion"
                :interview-mode="lastInterviewMode"
                @submit-code="handleCodeSubmit"
                @go-back="exitCodingMode"
              />
            </div>
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
    
    <!-- 底部功能 Dock -->
    <Dock :items="dockItems" v-if="positionId" class="fixed bottom-6 left-1/2 -translate-x-1/2" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, h, watch, nextTick, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import Dock from '../component/Dock/Dock.vue'
import TextInterviewChat from '../components/TextInterviewChat.vue'
import CodeEditorView from '../views/CodeEditorView.vue'
import { useUserStore } from '../stores/user'
import { unlockAudioForPlayback, playBase64Audio, speakTextFallback, PcmRecorder } from '../utils/audioUtils'
import { VoiceWebSocketClient } from '../services/voiceWebSocket'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

// 面试模式（语音/文字/代码）
const interviewMode = ref<'voice' | 'text' | 'coding'>('voice')
const lastInterviewMode = ref<'voice' | 'text'>('voice')

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

// 交互相关（语音）
const aiSubtitles = ref('')

// 交互相关（文字）
const textMessages = ref<Array<{role: 'user' | 'interviewer', content: string, timestamp: number}>>([])
const isTextWaiting = ref(false)

// 算法题正文由后端 LLM 生成，经 WebSocket subtitle.codingProblemContent 或 HTTP turns 返回写入
const currentAlgorithmQuestion = ref('')
/** Dock「代码编写」右上角红点：有新题干且当前不在代码页时为 true */
const codingDockNotify = ref(false)

// 视频相关
const videoRef = ref<HTMLVideoElement | null>(null)
const hasVideo = ref(false)
const isSpeaking = ref(false)

// 语音 WebSocket（开场白 + 点击说话）
const voiceClient = ref<InstanceType<typeof VoiceWebSocketClient> | null>(null)
const pcmRecorder = ref<InstanceType<typeof PcmRecorder> | null>(null)

const startVideo = async () => {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false })
    if (videoRef.value) {
      videoRef.value.srcObject = stream
      hasVideo.value = true
    }
  } catch (err) {
    console.error('Error accessing camera:', err)
    hasVideo.value = false
  }
}

const stopVideo = () => {
  if (videoRef.value && videoRef.value.srcObject) {
    const stream = videoRef.value.srcObject as MediaStream
    stream.getTracks().forEach(track => track.stop())
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
  voiceClient.value?.disconnect()
  voiceClient.value = null
  sessionId.value = null
  aiSubtitles.value = ''
  textMessages.value = []
  currentAlgorithmQuestion.value = ''
  codingDockNotify.value = false
  isTextWaiting.value = false
  error.value = ''
  await router.push({ name: 'interview', query: { positionId: String(id) } })
  updateActiveTabStyle()
}

const startInterview = async () => {
  if (!positionId.value) return
  await createSession()
}

const createSession = async () => {
  if (!positionId.value) return
  
  isLoading.value = true
  error.value = ''
  textMessages.value = []
  currentAlgorithmQuestion.value = ''
  codingDockNotify.value = false

  try {
    const res = await request.post('/api/interview/sessions', {
      positionId: Number(positionId.value)
    }) as any
    
    if (res.code === 200 && res.data?.sessionId) {
      sessionId.value = res.data.sessionId
      if (interviewMode.value === 'voice') {
        unlockAudioForPlayback()
      }

      // 通过 WebSocket 获取开场白 + 后续点击说话的语音回复（onVoiceMessage 见下方定义）
      const client = new VoiceWebSocketClient()
      voiceClient.value = client
      try {
        await client.connect(onVoiceMessage)
        client.sendPlayWelcome(sessionId.value!, interviewMode.value === 'text')
      } catch (wsErr: any) {
        console.warn('WebSocket 开场白失败，改用 HTTP + 浏览器朗读:', wsErr)
        voiceClient.value = null
        const welcomeRes = await request.get(`/api/interview/welcome/${sessionId.value}`) as any
        if (welcomeRes.code === 200) {
          const welcomeText = welcomeRes.data?.content || '你好，很高兴能面试你。请先做一个简单的自我介绍吧。'
          if (interviewMode.value === 'voice') {
            aiSubtitles.value = welcomeText
            nextTick(() => speakTextFallback(welcomeText))
          } else if (interviewMode.value === 'text') {
            textMessages.value.push({
              role: 'interviewer',
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
    // HTTP 401 由 request 拦截器统一 forceRelogin，此处不再重复跳转
  } finally {
    isLoading.value = false
  }
}

const onVoiceMessage = (msg: any) => {
  // 算法题干可与字幕分开发送：先处理 codingProblemContent
  if (msg.type === 'subtitle') {
    if (typeof msg.codingProblemContent === 'string' && msg.codingProblemContent.trim()) {
      currentAlgorithmQuestion.value = msg.codingProblemContent.trim()
      if (interviewMode.value !== 'coding') {
        codingDockNotify.value = true
      }
    }
  }

  // 语音模式下的字幕处理
  if (msg.type === 'subtitle' && msg.content) {
    if (interviewMode.value === 'voice') {
      aiSubtitles.value = msg.content
    } else if (interviewMode.value === 'text') {
      // 文字模式：以 subtitle 作为面试官回复（isFinal 缺省视为 true）
      if (msg.isFinal !== false) {
        textMessages.value.push({
          role: 'interviewer',
          content: msg.content,
          timestamp: Date.now()
        })
        isTextWaiting.value = false
      }
    }
  }
  
  // 文字面试不播放面试官语音（也不触发浏览器朗读兜底）
  if (interviewMode.value === 'voice' && msg.type === 'interviewer_audio' && msg.data) {
    playBase64Audio(msg.data, msg.mimeType || 'audio/mpeg').catch((e) => {
      console.warn('播放面试官音频失败，改用浏览器朗读:', e)
      if (aiSubtitles.value) speakTextFallback(aiSubtitles.value)
    })
  }
  
  if (msg.type === 'error' && msg.content) {
    ElMessage.warning(msg.content)
    if (interviewMode.value === 'text') {
      isTextWaiting.value = false
    }
  }
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
  voiceClient.value?.disconnect()
  voiceClient.value = null
  try {
    await request.patch(`/api/interview/sessions/${sessionId.value}/complete`, {})
    ElMessage.success('面试已结束')
    sessionId.value = null
    aiSubtitles.value = ''
    textMessages.value = []
    currentAlgorithmQuestion.value = ''
    codingDockNotify.value = false
  } catch (err: any) {
    error.value = err?.message || '结束面试失败，请重试'
    ElMessage.error(error.value)
  } finally {
    isCompleting.value = false
  }
}

const handleTextSend = async (content: string) => {
  if (!sessionId.value || !content.trim()) return

  textMessages.value.push({
    role: 'user',
    content: content.trim(),
    timestamp: Date.now()
  })

  isTextWaiting.value = true
  const clientTurnId = `text_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`

  try {
    if (voiceClient.value?.isOpen()) {
      voiceClient.value.sendTextAnswer({
        sessionId: sessionId.value,
        content: content.trim(),
        clientTurnId
      })
      // 等待 WebSocket subtitle / error 再结束 loading
      return
    }

    const res = await request.post(`/api/interview/sessions/${sessionId.value}/turns`, {
      content: content.trim(),
      clientTurnId
    }) as any

    if (res.code === 200 && res.data?.interviewerTurn) {
      const it = res.data.interviewerTurn
      if (typeof it.codingProblemContent === 'string' && it.codingProblemContent.trim()) {
        currentAlgorithmQuestion.value = it.codingProblemContent.trim()
        if (interviewMode.value !== 'coding') {
          codingDockNotify.value = true
        }
      }
      textMessages.value.push({
        role: 'interviewer',
        content: it.content || '',
        timestamp: Date.now()
      })
    }
    isTextWaiting.value = false
  } catch (err: any) {
    console.error('发送文字答案失败:', err)
    ElMessage.error('发送失败，请重试')
    textMessages.value.pop()
    isTextWaiting.value = false
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
  voiceClient.value?.disconnect()
  voiceClient.value = null
  stopVideo()
})

const dockActiveRing = '!border-[#6ef17d] ring-2 ring-[#6ef17d]/35'

const exitCodingMode = () => {
  interviewMode.value = lastInterviewMode.value
}

const handleCodeSubmit = (code: string) => {
  if (!sessionId.value) return

  ElMessage.success('代码已提交，正在评估中...')
  
  // TODO: 后续可通过 WebSocket 或 API 发送代码给后端进行评测
  // 暂时仅显示提示，不添加到文字对话框中
}

const dockItems = computed(() => [
  {
    label: '代码编写',
    notify: codingDockNotify.value,
    icon: () => h('svg', { xmlns: 'http://www.w3.org/2000/svg', width: '24', height: '24', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [
      h('polyline', { points: '16 18 22 12 16 6' }),
      h('polyline', { points: '8 6 2 12 8 18' })
    ]),
    className: interviewMode.value === 'coding' ? dockActiveRing : '',
    onClick: () => {
      if (!sessionId.value) {
        ElMessage.warning('请先开始面试后再使用代码编写功能')
        return
      }
      lastInterviewMode.value = interviewMode.value === 'coding' ? 'voice' : interviewMode.value
      interviewMode.value = 'coding'
      codingDockNotify.value = false
    }
  },
  {
    label: '语音面试',
    className: interviewMode.value === 'voice' ? dockActiveRing : '',
    icon: () => h('svg', { xmlns: 'http://www.w3.org/2000/svg', width: '24', height: '24', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [
      h('path', { d: 'M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z' }),
      h('path', { d: 'M19 10v2a7 7 0 0 1-14 0v-2' }),
      h('line', { x1: '12', x2: '12', y1: '19', y2: '22' })
    ]),
    onClick: () => {
      interviewMode.value = 'voice'
      if (sessionId.value) {
        ElMessage.info('当前为语音赛道，请使用「点击说话」作答')
      }
    }
  },
  {
    label: '文字面试',
    className: interviewMode.value === 'text' ? dockActiveRing : '',
    icon: () => h('svg', { xmlns: 'http://www.w3.org/2000/svg', width: '24', height: '24', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [
      h('path', { d: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z' })
    ]),
    onClick: () => {
      interviewMode.value = 'text'
      if (!sessionId.value) {
        ElMessage.info('请点击「开始面试」创建会话后再输入文字')
      }
    }
  }
])
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
