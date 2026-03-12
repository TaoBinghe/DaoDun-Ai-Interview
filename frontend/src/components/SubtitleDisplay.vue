<script lang="ts">
import { defineComponent } from 'vue'

export default defineComponent({
  name: 'SubtitleDisplay',
  props: {
    text: { type: String, required: true }
  }
})
</script>

<template>
  <div v-if="text" class="subtitle-box">
    <div class="subtitle-title">AI 字幕</div>
    <div class="subtitle-content">{{ text }}</div>
  </div>
</template>

<style scoped>
.subtitle-box {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f4f4f5;
}

.subtitle-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.subtitle-content {
  font-size: 13px;
  line-height: 1.5;
  color: #303133;
  white-space: pre-wrap;
}
</style>
