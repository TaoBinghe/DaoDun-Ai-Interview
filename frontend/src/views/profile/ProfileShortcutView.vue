<template>
  <el-card shadow="never" class="theme-el-card !rounded-2xl">
    <h2 class="theme-title text-xl font-semibold">快捷操作</h2>
    <p class="mt-1 text-sm theme-text-muted">快速进入常用功能模块。</p>
    <div class="mt-4 flex flex-wrap gap-3">
      <el-button class="theme-el-btn-secondary h-10! rounded-xl! px-5!" @click="router.push('/resume')">
        前往导入简历
      </el-button>
      <el-button class="theme-el-btn-primary h-10! rounded-xl! border-0! px-5!" @click="router.push('/interview')">
        开始AI面试
      </el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>
