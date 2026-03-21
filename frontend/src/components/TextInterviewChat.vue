<template>
  <!-- 固定整体高度，不随消息多少伸缩；消息区内部滚动 -->
  <div
    class="w-full flex flex-col gap-4 h-[min(70vh,560px)] min-h-[480px] max-h-[560px] shrink-0 box-border"
  >
    <div
      class="flex-1 min-h-0 flex flex-col bg-[#1f1e1d] rounded-2xl border border-[#faf9f5]/10 overflow-hidden"
    >
      <div
        ref="messagesContainer"
        class="flex-1 min-h-0 overflow-y-auto p-6 space-y-4"
      >
        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          class="flex gap-3 animate-fadeIn"
          :class="[msg.role === 'user' ? 'justify-end' : 'justify-start']"
        >
          <div
            :class="[
              'max-w-[70%] px-4 py-3 rounded-lg break-words',
              msg.role === 'user'
                ? 'bg-[#6ef17d] text-black'
                : 'bg-[#2b2a27] text-[#faf9f5] border border-[#faf9f5]/10'
            ]"
          >
            <p class="text-sm md:text-base leading-relaxed whitespace-pre-wrap">
              {{ msg.content }}
            </p>
          </div>
        </div>

        <div v-if="loading" class="flex gap-3 justify-start">
          <div class="flex gap-2 items-center px-4 py-3">
            <div class="w-2 h-2 bg-[#6ef17d] rounded-full animate-bounce" :style="{ animationDelay: '0s' }"></div>
            <div class="w-2 h-2 bg-[#6ef17d] rounded-full animate-bounce" :style="{ animationDelay: '0.1s' }"></div>
            <div class="w-2 h-2 bg-[#6ef17d] rounded-full animate-bounce" :style="{ animationDelay: '0.2s' }"></div>
          </div>
        </div>
      </div>
    </div>

    <div class="w-full flex gap-3 shrink-0">
      <input
        v-model="inputText"
        @keydown.enter="!disabled && !loading ? handleSend() : null"
        type="text"
        placeholder="输入你的回答..."
        :disabled="disabled || loading"
        class="flex-1 px-4 py-3 rounded-lg bg-[#2b2a27] border border-[#faf9f5]/10 text-[#faf9f5] placeholder-[#faf9f5]/40 focus:outline-none focus:border-[#6ef17d]/50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      />
      <button
        type="button"
        @click="handleSend"
        :disabled="disabled || loading || !inputText.trim()"
        class="px-6 py-3 rounded-lg bg-[#6ef17d] text-black font-bold transition-all duration-200 hover:bg-[#5edb6b] active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 whitespace-nowrap"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="22" x2="11" y1="2" y2="13" />
          <polygon points="22 2 15 22 11 13 2 9 22 2" />
        </svg>
        发送
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'

interface Message {
  role: 'user' | 'interviewer'
  content: string
  timestamp: number
}

interface Props {
  messages: Message[]
  loading: boolean
  disabled: boolean
}

interface Emits {
  (e: 'send', content: string): void
}

const props = withDefaults(defineProps<Props>(), {
  messages: () => [],
  loading: false,
  disabled: false
})

const emit = defineEmits<Emits>()

const inputText = ref('')
const messagesContainer = ref<HTMLElement | null>(null)

watch(
  () => [props.messages, props.loading],
  () => {
    nextTick(() => {
      if (messagesContainer.value) {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
      }
    })
  },
  { deep: true }
)

const handleSend = () => {
  const text = inputText.value.trim()
  if (!text) return

  emit('send', text)
  inputText.value = ''
}
</script>

<style scoped>
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fadeIn {
  animation: fadeIn 0.3s ease-out;
}

::-webkit-scrollbar {
  width: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #6ef17d;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #5edb6b;
}
</style>
