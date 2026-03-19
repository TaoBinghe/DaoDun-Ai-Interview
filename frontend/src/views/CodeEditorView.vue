<template>
  <div class="w-full h-full bg-[#141413] flex flex-col relative">
    <!-- Header -->
    <div class="flex items-center justify-between px-6 py-5 bg-[#141413]">
      <div class="flex items-center gap-3">
        <button
          @click="handleGoBack"
          class="p-2 hover:bg-[#2b2a27] rounded-lg transition-colors"
          title="返回面试"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-[#faf9f5]">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
        </button>
        <span class="text-[#faf9f5] font-medium">代码编辑</span>
      </div>
      <div class="text-sm text-gray-400">
        <span>{{ languageDisplayName }}</span>
      </div>
    </div>

    <!-- Main Content -->
    <div class="flex-1 flex overflow-hidden relative">
      <!-- Left Panel: Question (30%) -->
      <div class="w-3/10 border-r border-[#faf9f5]/10 overflow-y-auto bg-[#1f1e1d]">
        <div class="p-6 h-full flex flex-col">
          <h3 class="text-[#faf9f5] font-semibold mb-4">算法题</h3>
          <div class="text-sm text-gray-300 leading-relaxed whitespace-pre-wrap break-words flex-1">
            {{ currentQuestion }}
          </div>
          <div class="mt-6 pt-4 border-t border-[#faf9f5]/10">
            <p class="text-xs text-gray-500">提示：在右侧编写代码答案</p>
          </div>
        </div>
      </div>

      <!-- Right Panel: Code Editor (70%) -->
      <div class="flex-1 flex flex-col overflow-hidden">
        <!-- Code Editor Container -->
        <div class="flex-1 flex overflow-hidden relative bg-[#141413]">
          <!-- Line Numbers -->
          <div class="bg-[#141413] px-3 py-4 overflow-hidden select-none text-right">
            <div v-for="(_, index) in codeLines" :key="index" class="text-gray-600 text-xs h-6 leading-6 font-mono">
              {{ index + 1 }}
            </div>
          </div>

          <!-- Code Input Area -->
          <div class="flex-1 relative overflow-hidden">
            <!-- Highlight Layer (可见的彩色代码) -->
            <pre
              ref="highlightPreRef"
              class="absolute inset-0 p-4 m-0 text-sm font-mono leading-6 pointer-events-none overflow-auto whitespace-pre"
            ><code v-html="highlightedCode" :class="`language-${selectedLanguage}`"></code></pre>

            <!-- Textarea Overlay -->
            <textarea
              ref="textareaRef"
              v-model="code"
              @keydown="handleKeyDown"
              @input="updateHighlight"
              @scroll="syncScroll"
              placeholder="// 请在这里输入你的代码..."
              class="relative w-full h-full p-4 m-0 text-sm font-mono leading-6 bg-transparent text-transparent outline-none resize-none overflow-auto"
              style="caret-color: #6ef17d; tab-size: 2;"
            ></textarea>
          </div>
        </div>

        <!-- Language Selector & Buttons -->
        <div class="border-t border-[#faf9f5]/10 bg-[#141413] px-6 py-4 flex items-center justify-between gap-4">
          <select
            v-model="selectedLanguage"
            class="px-3 py-2 bg-[#2b2a27] border border-[#faf9f5]/15 rounded-lg text-sm text-[#faf9f5] outline-none hover:border-[#faf9f5]/25 focus:border-[#6ef17d]"
          >
            <option value="java">Java</option>
            <option value="python">Python</option>
            <option value="javascript">JavaScript</option>
            <option value="typescript">TypeScript</option>
            <option value="sql">SQL</option>
          </select>

          <div class="flex gap-3">
            <button
              @click="copyCode"
              class="px-4 py-2 bg-[#2b2a27] border border-[#faf9f5]/15 rounded-lg text-sm text-[#faf9f5] hover:bg-[#3a3937] transition-colors flex items-center gap-2"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>
                <rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
              </svg>
              复制
            </button>
            <button
              @click="handleGoBack"
              class="px-4 py-2 bg-[#2b2a27] border border-[#faf9f5]/15 rounded-lg text-sm text-[#faf9f5] hover:bg-[#3a3937] transition-colors"
            >
              返回面试
            </button>
            <button
              @click="submitCode"
              :disabled="isSubmitting || !code.trim()"
              class="px-6 py-2 bg-[#6ef17d] rounded-lg text-sm text-[#141413] font-bold hover:bg-[#5edb6b] active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed transition-all"
            >
              {{ isSubmitting ? '提交中...' : '提交代码' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Video Frame (语音模式) -->
      <div v-if="interviewMode === 'voice' && videoRef" class="absolute bottom-6 left-6 w-20 h-32 bg-[#1f1e1d] rounded-lg border border-[#faf9f5]/10 overflow-hidden shadow-lg">
        <video
          ref="inlineVideoRef"
          autoplay
          playsinline
          muted
          class="w-full h-full object-cover mirror"
        ></video>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { highlightCode, type SupportedLanguage, getLanguageDisplayName } from '../utils/codeHighlight'

interface Props {
  sessionId: number
  currentQuestion: string
  interviewMode: 'text' | 'voice'
  videoRef?: HTMLVideoElement | null
  currentEmotion?: {
    emotion: string
    confidence: number
    hasFace: boolean
    lastUpdate: number
  }
}

interface Emits {
  (e: 'submit-code', code: string): void
  (e: 'go-back'): void
}

const props = withDefaults(defineProps<Props>(), {
  videoRef: null
})

const emit = defineEmits<Emits>()

const code = ref<string>('')
const selectedLanguage = ref<SupportedLanguage>('java')
const isSubmitting = ref(false)
const textareaRef = ref<HTMLTextAreaElement>()
const highlightPreRef = ref<HTMLElement>()
const inlineVideoRef = ref<HTMLVideoElement>()

const languageDisplayName = computed(() => getLanguageDisplayName(selectedLanguage.value))

const codeLines = computed(() => {
  return code.value.split('\n')
})

const highlightedCode = computed(() => {
  return highlightCode(code.value, selectedLanguage.value)
})

const handleKeyDown = (event: KeyboardEvent) => {
  if (event.key === 'Tab') {
    event.preventDefault()
    const textarea = textareaRef.value
    if (textarea) {
      const start = textarea.selectionStart
      const end = textarea.selectionEnd
      const newCode = code.value.substring(0, start) + '\t' + code.value.substring(end)
      code.value = newCode
      nextTick(() => {
        textarea.selectionStart = textarea.selectionEnd = start + 1
      })
    }
  }
}

const updateHighlight = () => {
  // 高亮会自动通过 computed 更新
}

const syncScroll = () => {
  const ta = textareaRef.value
  const pre = highlightPreRef.value
  if (!ta || !pre) return
  pre.scrollTop = ta.scrollTop
  pre.scrollLeft = ta.scrollLeft
}

const copyCode = async () => {
  try {
    await navigator.clipboard.writeText(code.value)
    // 可以添加 toast 提示 "已复制"
  } catch (err) {
    console.error('复制失败:', err)
  }
}

const submitCode = () => {
  if (!code.value.trim()) return
  isSubmitting.value = true
  emit('submit-code', code.value)
  // 实际提交由父组件处理，之后会重置状态
  setTimeout(() => {
    isSubmitting.value = false
  }, 500)
}

const handleGoBack = () => {
  emit('go-back')
}

// 同步视频流到内联视频元素
onMounted(() => {
  if (props.videoRef && inlineVideoRef.value && props.interviewMode === 'voice') {
    // 如果 videoRef 是 HTMLVideoElement，直接使用其流
    if (props.videoRef instanceof HTMLVideoElement && props.videoRef.srcObject) {
      inlineVideoRef.value.srcObject = props.videoRef.srcObject
    }
  }
})

// 监听外部 videoRef 变化
watch(
  () => props.videoRef,
  (newVideoRef) => {
    if (newVideoRef && inlineVideoRef.value && props.interviewMode === 'voice') {
      if (newVideoRef.srcObject) {
        inlineVideoRef.value.srcObject = newVideoRef.srcObject
      }
    }
  }
)
</script>

<style scoped>
.mirror {
  transform: scaleX(-1);
}

textarea::placeholder {
  color: rgba(255, 255, 255, 0.3);
}

/* 隐藏滚动条但保留滚动功能 */
textarea {
  scrollbar-width: thin;
  scrollbar-color: rgba(111, 241, 125, 0.3) transparent;
}

textarea::-webkit-scrollbar {
  width: 6px;
}

textarea::-webkit-scrollbar-track {
  background: transparent;
}

textarea::-webkit-scrollbar-thumb {
  background: rgba(111, 241, 125, 0.3);
  border-radius: 3px;
}

textarea::-webkit-scrollbar-thumb:hover {
  background: rgba(111, 241, 125, 0.5);
}
</style>
