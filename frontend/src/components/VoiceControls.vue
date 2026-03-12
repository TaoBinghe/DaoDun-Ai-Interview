<script lang="ts">
import { defineComponent } from 'vue'

export default defineComponent({
  name: 'VoiceControls',
  props: {
    recording: { type: Boolean, required: true },
    connecting: { type: Boolean, required: true }
  },
  emits: ['start', 'stop']
})
</script>

<template>
  <div class="voice-controls">
    <el-button
      v-if="!recording"
      type="primary"
      :loading="connecting"
      @click="$emit('start')"
    >
      开始语音作答
    </el-button>
    <el-button
      v-else
      type="danger"
      @click="$emit('stop')"
    >
      结束并发送
    </el-button>
    <span class="voice-tip">
      {{ recording ? '录音中，请开始说话...' : '点击开始后直接说话，结束后自动提交识别。' }}
    </span>
  </div>
</template>

<style scoped>
.voice-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.voice-tip {
  font-size: 12px;
  color: #909399;
}
</style>
