<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDark } from '@vueuse/core'
import { ArrowLeft, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

interface PositionItem {
  id: number
  name: string
  description?: string
}

interface ChunkItem {
  id: number
  documentId: number
  title: string
  categoryLevel1?: string
  categoryLevel2?: string
  keywords?: string
  difficulty?: number
  canonicalQuestion?: string
  answerKeyPoints?: string
  exampleAnswer?: string
  followUps?: string
  scoringPoints?: string
  pitfalls?: string
  sourceOrder?: number
}

const router = useRouter()
const isDark = useDark()

const positions = ref<PositionItem[]>([])
const positionName = ref('')
const queryText = ref('')
const keywordsInput = ref('')
const loading = ref(false)
const chunks = ref<ChunkItem[]>([])
const stats = ref<{ totalChunks: number; withEmbedding: number } | null>(null)

onMounted(async () => {
  await Promise.all([fetchPositions(), fetchStats()])
})

const fetchPositions = async () => {
  const res: any = await request.get('/api/position/list')
  positions.value = res.data || []
  if (positions.value.length && !positionName.value) {
    positionName.value = positions.value[0].name
  }
}

const fetchStats = async () => {
  try {
    const res: any = await request.get('/api/knowledge/stats')
    stats.value = res.data
  } catch {
    stats.value = null
  }
}

const clearEmbeddingsLoading = ref(false)
const clearEmbeddings = async () => {
  try {
    await ElMessageBox.confirm(
      '将清空所有 chunk 的向量，清空后可点击「重建知识库」重新生成。是否继续？',
      '一键清除向量',
      { type: 'warning' }
    )
  } catch {
    return
  }
  clearEmbeddingsLoading.value = true
  try {
    const res: any = await request.post('/api/knowledge/clear-embeddings')
    const count = res.data?.clearedCount ?? 0
    ElMessage.success(`已清除 ${count} 条向量`)
    await fetchStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '清除失败')
  } finally {
    clearEmbeddingsLoading.value = false
  }
}

const rebuildLoading = ref(false)
const rebuild = async () => {
  rebuildLoading.value = true
  try {
    const res: any = await request.post('/api/knowledge/rebuild')
    const count = res.data?.ingestedChunks ?? 0
    ElMessage.success(res.data?.message || `重建完成，本次入库 ${count} 条`)
    await fetchStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '重建失败')
  } finally {
    rebuildLoading.value = false
  }
}

const runVerify = async () => {
  if (!positionName.value?.trim()) {
    ElMessage.warning('请选择岗位')
    return
  }
  const kws = keywordsInput.value
    .split(/[,，\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
  loading.value = true
  chunks.value = []
  try {
    const res: any = await request.post('/api/knowledge/verify', {
      positionName: positionName.value.trim(),
      queryText: queryText.value || '',
      keywords: kws.length ? kws : undefined
    })
    chunks.value = res.data || []
    ElMessage.success(`检索到 ${chunks.value.length} 条知识条目`)
  } catch (e: any) {
    ElMessage.error(e?.message || '检索失败')
  } finally {
    loading.value = false
  }
}

const difficultyLabel = (d: number | undefined) => {
  if (d == null) return '-'
  return { 1: '简单', 2: '中等', 3: '困难' }[d] ?? String(d)
}
</script>

<template>
  <div class="verify-container" :class="{ 'is-dark': isDark }">
    <el-header class="app-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" text @click="router.push('/')">返回</el-button>
        <h2>知识库检索验证</h2>
      </div>
    </el-header>

    <el-main class="app-main">
      <el-card class="form-card" shadow="never">
        <template #header>
          <span>检索参数（与面试中 RAG 使用相同逻辑）</span>
        </template>
        <el-form label-width="100px" label-position="top">
          <el-form-item label="岗位">
            <el-select v-model="positionName" placeholder="选择岗位" style="width: 100%">
              <el-option
                v-for="p in positions"
                :key="p.id"
                :label="p.name"
                :value="p.name"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="查询文本（模拟当前问题+回答）">
            <el-input
              v-model="queryText"
              type="textarea"
              :rows="3"
              placeholder="例如：请说明 JVM、JDK、JRE 三者的关系。 或输入候选人的回答内容"
            />
          </el-form-item>
          <el-form-item label="关键词（可选，逗号或空格分隔）">
            <el-input v-model="keywordsInput" placeholder="例如：JVM, JDK, 字节码" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" :icon="Search" @click="runVerify">
              执行检索
            </el-button>
          </el-form-item>
        </el-form>
        <div v-if="stats" class="stats-line">
          知识库统计：共 {{ stats.totalChunks }} 条 chunk，已生成向量 {{ stats.withEmbedding }} 条
          <el-button
            type="warning"
            size="small"
            :loading="clearEmbeddingsLoading"
            @click="clearEmbeddings"
          >
            一键清除向量
          </el-button>
          <el-button
            type="success"
            size="small"
            :loading="rebuildLoading"
            @click="rebuild"
          >
            重建知识库
          </el-button>
        </div>
      </el-card>

      <el-card v-if="chunks.length" class="result-card" shadow="never">
        <template #header>
          <span>命中条目（共 {{ chunks.length }} 条，即会注入到面试官上下文的参考）</span>
        </template>
        <div class="chunk-list">
          <div v-for="(c, i) in chunks" :key="c.id" class="chunk-item">
            <div class="chunk-title">
              {{ i + 1 }}. {{ c.title }}
              <el-tag v-if="c.difficulty != null" size="small" type="info">
                {{ difficultyLabel(c.difficulty) }}
              </el-tag>
            </div>
            <div v-if="c.categoryLevel1 || c.categoryLevel2" class="chunk-meta">
              {{ [c.categoryLevel1, c.categoryLevel2].filter(Boolean).join(' / ') }}
            </div>
            <div v-if="c.answerKeyPoints" class="chunk-block">
              <strong>标准要点：</strong>
              <span class="text-preview">{{ c.answerKeyPoints }}</span>
            </div>
            <div v-if="c.followUps" class="chunk-block">
              <strong>可追问：</strong>
              <span class="text-preview">{{ c.followUps }}</span>
            </div>
            <div v-if="c.scoringPoints" class="chunk-block">
              <strong>评分要点：</strong>
              <span class="text-preview">{{ c.scoringPoints }}</span>
            </div>
          </div>
        </div>
      </el-card>
      <el-empty v-else-if="!loading && chunks.length === 0 && queryText" description="未命中任何条目，可调整查询或关键词后重试" />
    </el-main>
  </div>
</template>

<style scoped>
.verify-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f3f4f6;
}
.verify-container.is-dark {
  background-color: #121212;
}
.app-header {
  display: flex;
  align-items: center;
  background-color: #fff;
  padding: 0 1.5rem;
  height: 56px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.is-dark .app-header {
  background-color: #1e1e1e;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
}
.header-left h2 {
  margin: 0;
  font-size: 1.1rem;
  color: #10b981;
}
.app-main {
  padding: 1.5rem;
  max-width: 900px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}
.form-card,
.result-card {
  border-radius: 12px;
  margin-bottom: 1rem;
}
.form-card :deep(.el-card__header) {
  font-weight: 600;
}
.stats-line {
  margin-top: 12px;
  font-size: 12px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 8px;
}
.is-dark .stats-line {
  color: #9ca3af;
}
.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.chunk-item {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fafafa;
}
.is-dark .chunk-item {
  border-color: #374151;
  background: #1f2937;
}
.chunk-title {
  font-weight: 600;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.chunk-meta {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 8px;
}
.is-dark .chunk-meta {
  color: #9ca3af;
}
.chunk-block {
  font-size: 13px;
  margin-top: 6px;
}
.text-preview {
  white-space: pre-wrap;
  word-break: break-word;
}
:deep(.el-button--primary) {
  --el-button-bg-color: #10b981;
  --el-button-border-color: #10b981;
  --el-button-hover-bg-color: #34d399;
  --el-button-hover-border-color: #34d399;
}
</style>
