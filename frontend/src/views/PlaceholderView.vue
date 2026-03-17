<template>
  <div class="placeholder">
    <h1>后端 API 服务</h1>
    <p>调试用前端已移除，请直接调用后端接口。</p>
    <p class="hint">参见项目根目录 <code>接口文档.md</code></p>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.placeholder {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  font-family: system-ui, sans-serif;
  background: #fafafa;
}
h1 { font-size: 1.5rem; color: #111; margin-bottom: 0.5rem; }
p { color: #666; margin: 0.25rem 0; }
.hint { font-size: 0.875rem; color: #888; margin-top: 1rem; }
code { background: #eee; padding: 0.2em 0.4em; border-radius: 4px; }
</style>
