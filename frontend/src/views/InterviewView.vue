<template>
  <div class="flex flex-col items-center justify-center min-h-[calc(100vh-64px)] text-gray-400">
    <h1 class="text-3xl font-medium text-white mb-4">AI 面试</h1>
    <p v-if="positionId">正在为你准备 {{ positionName }} 的面试...</p>
    <p v-else>请从导航栏选择一个岗位开始面试。</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import request from '../utils/request'

const route = useRoute()
const positionId = computed(() => route.query.positionId)
const positionName = ref('')

const fetchPositionName = async () => {
  if (!positionId.value) return
  try {
    const res = await request.get('/api/position/list') as any
    if (res.code === 200) {
      const p = res.data.find((item: any) => item.id == positionId.value)
      if (p) positionName.value = p.name
    }
  } catch (e) {
    console.error(e)
  }
}

onMounted(fetchPositionName)
</script>
