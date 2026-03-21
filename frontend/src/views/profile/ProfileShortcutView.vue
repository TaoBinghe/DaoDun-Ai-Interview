<template>
  <el-card shadow="never" class="!rounded-2xl !border-[#ffffff14] !bg-[#1d1e1d]">
    <h2 class="text-xl font-semibold text-[#f1f1ec]">快捷操作</h2>
    <p class="mt-1 text-sm text-[#9f9f99]">快速进入常用功能模块。</p>
    <div class="mt-4 flex flex-wrap gap-3">
      <el-button class="!h-10 !rounded-xl !border-[#ffffff1f] !bg-[#2a2b2a] !px-5 !text-[#f1f1ec] hover:!bg-[#343533]" @click="router.push('/resume')">
        前往导入简历
      </el-button>
      <el-button class="!h-10 !rounded-xl !border-0 !bg-[#6ef17d] !px-5 !text-black hover:!bg-[#5edb6b]" @click="router.push('/interview')">
        开始AI面试
      </el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>
