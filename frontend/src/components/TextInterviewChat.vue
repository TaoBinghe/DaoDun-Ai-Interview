<template>
  <!-- 固定整体高度，不随消息多少伸缩；消息区内部滚动 -->
  <div
    class="w-full flex flex-col gap-4 h-[min(70vh,560px)] min-h-[480px] max-h-[560px] shrink-0 box-border"
  >
    <div
      class="chat-shell flex-1 min-h-0 flex flex-col rounded-2xl border overflow-hidden"
    >
      <div
        ref="messagesContainer"
        class="theme-scrollbar flex-1 min-h-0 overflow-y-auto p-6 space-y-4"
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
                ? 'chat-user-bubble'
                : 'chat-ai-bubble'
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
        class="chat-input flex-1 px-4 py-3 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      />
      <button
        type="button"
        @click="handleSend"
        :disabled="disabled || loading || !inputText.trim()"
        class="chat-send-btn px-6 py-3 rounded-lg font-bold transition-all duration-200 active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 whitespace-nowrap"
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

<style scoped>
.chat-shell {
  background: var(--app-surface);
  border-color: var(--app-border);
}

.chat-user-bubble {
  background: var(--app-accent);
  color: var(--app-accent-contrast);
}

.chat-ai-bubble {
  background: var(--app-surface-strong);
  color: var(--app-text);
  border: 1px solid var(--app-border);
}

.chat-input {
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
  color: var(--app-text);
}

.chat-input::placeholder {
  color: var(--app-text-faint);
}

.chat-input:focus {
  outline: none;
  border-color: color-mix(in srgb, var(--app-accent) 45%, transparent);
}

.chat-send-btn {
  background: var(--app-accent);
  color: var(--app-accent-contrast);
}

.chat-send-btn:hover:not(:disabled) {
  background: var(--app-accent-strong);
}
</style>
