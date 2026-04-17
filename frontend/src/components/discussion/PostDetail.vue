<template>
  <div class="theme-page min-h-[calc(100vh-64px)] px-4 py-8">
    <div class="mx-auto max-w-4xl">
      <div class="mb-6 flex items-center gap-3">
        <button
          type="button"
          class="post-back-btn inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl transition-colors focus:outline-none focus-visible:ring-2"
          aria-label="返回讨论区"
          @click="router.back()"
        >
          <ChevronLeft class="h-5 w-5" :stroke-width="2" />
        </button>
      </div>

      <h1 v-if="post" class="theme-title text-2xl md:text-3xl font-medium tracking-tight">
        {{ post.title }}
      </h1>
      <p v-else class="text-sm theme-text-muted">帖子不存在或已被删除。</p>

      <div v-if="post" class="mt-4 flex flex-wrap items-center gap-4 text-sm theme-text-muted">
        <div class="flex items-center gap-3">
          <img
            :src="post.author.avatar"
            :alt="post.author.username"
            class="h-10 w-10 rounded-full object-cover ring-1 ring-white/10"
          />
          <div>
            <p class="theme-title">{{ post.author.username }}</p>
            <p class="text-xs theme-text-muted">{{ post.author.rank }}</p>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-3 text-xs md:text-sm theme-text-muted">
          <div class="inline-flex items-center gap-1">
            <Eye class="h-4 w-4" :stroke-width="2" />
            <span>{{ formatCount(post.stats.views) }}</span>
          </div>
          <div class="inline-flex items-center gap-1">
            <Calendar class="h-4 w-4" :stroke-width="2" />
            <span>{{ postMeta.publishedAt }}</span>
          </div>
          <div class="inline-flex items-center gap-1">
            <Edit class="h-4 w-4" :stroke-width="2" />
            <span>最后编辑：{{ postMeta.updatedAt }}</span>
          </div>
          <div class="inline-flex items-center gap-1">
            <MapPin class="h-4 w-4" :stroke-width="2" />
            <span>{{ postMeta.location }}</span>
          </div>
        </div>
      </div>

      <div v-if="post" class="mt-3 flex flex-wrap gap-2">
        <span
          v-for="tag in post.tags"
          :key="tag"
          class="post-tag inline-flex items-center rounded-full px-3 py-1 text-xs"
        >
          {{ tag }}
        </span>
      </div>

      <section v-if="post" class="post-content mt-6 prose max-w-none">
        <p
          v-for="(paragraph, idx) in contentParagraphs"
          :key="idx"
          class="mb-4 text-[15px] leading-relaxed theme-text-soft"
        >
          {{ paragraph }}
        </p>
      </section>

      <div v-if="post" class="mt-6 flex flex-wrap gap-4">
        <button
          type="button"
          class="post-action inline-flex items-center gap-2 rounded-md px-1 py-1 text-sm transition-colors"
          @click="toggleLike"
        >
          <ThumbsUp
            class="h-4 w-4"
            :class="isLikedDetail ? 'post-liked' : ''"
            :stroke-width="2"
          />
          <span>{{ likeCount }}</span>
        </button>

        <button
          type="button"
          class="post-action inline-flex items-center gap-2 rounded-md px-1 py-1 text-sm transition-colors"
          @click="toggleStar"
        >
          <Star
            class="h-4 w-4"
            :class="isStarred ? 'post-starred' : ''"
            :stroke-width="2"
          />
          <span>{{ isStarred ? '已收藏' : '收藏' }}</span>
        </button>

        <button
          type="button"
          class="post-action inline-flex items-center gap-2 rounded-md px-1 py-1 text-sm transition-colors"
        >
          <MessageSquare class="h-4 w-4" :stroke-width="2" />
          <span>评论</span>
        </button>

        <button
          type="button"
          class="post-action inline-flex items-center gap-2 rounded-md px-1 py-1 text-sm transition-colors"
        >
          <Share2 class="h-4 w-4" :stroke-width="2" />
          <span>分享</span>
        </button>
      </div>

      <section class="mt-10">
        <h2 class="text-sm font-medium theme-text-soft">全部评论</h2>
        <div class="mt-4 space-y-4">
          <article
            v-for="comment in comments"
            :key="comment.id"
            class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-2"
          >
            <img
              :src="comment.avatarUrl"
              :alt="comment.authorName"
              class="row-start-1 h-8 w-8 shrink-0 rounded-full object-cover ring-1 ring-white/10"
            />
            <div
              class="row-start-1 flex min-w-0 flex-wrap items-center gap-x-2 gap-y-0.5 text-sm leading-relaxed theme-text-muted"
            >
              <span class="font-medium theme-text-soft">{{ comment.authorName }}</span>
              <span class="text-white/20">·</span>
              <span>{{ comment.createdAt }}</span>
              <span class="text-white/20">·</span>
              <span>IP：{{ comment.ipLocation }}</span>
            </div>
            <p class="col-start-2 row-start-2 min-w-0 text-sm leading-relaxed theme-text-soft">
              {{ comment.content }}
            </p>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.post-back-btn {
  background: var(--app-surface-strong);
  color: var(--app-text);
  border: 1px solid var(--app-border);
}

.post-back-btn:hover {
  filter: brightness(1.05);
}

.post-back-btn:focus-visible {
  outline-color: color-mix(in srgb, var(--app-accent) 35%, transparent);
}

.post-tag {
  border: 1px solid var(--app-border);
  background: var(--app-surface-soft);
  color: var(--app-text-muted);
}

.post-content {
  color: var(--app-text);
}

.post-action {
  color: var(--app-text-muted);
}

.post-action:hover {
  color: var(--app-text);
}

.post-liked {
  color: var(--app-success);
}

.post-starred {
  color: var(--app-warning);
}
</style>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Calendar,
  ChevronLeft,
  Edit,
  Eye,
  MapPin,
  MessageSquare,
  Share2,
  Star,
  ThumbsUp
} from 'lucide-vue-next'
import { forumPosts, type ForumPost } from '@/data/forumPosts'

const route = useRoute()
const router = useRouter()

const post = computed<ForumPost | undefined>(() => {
  const id = Number(route.params.id)
  if (Number.isNaN(id)) return undefined
  return forumPosts.find((p) => p.id === id)
})

interface CommentItem {
  id: number
  authorName: string
  avatarUrl: string
  content: string
  createdAt: string
  ipLocation: string
}

const postMeta = computed(() => {
  if (!post.value) {
    return {
      publishedAt: '',
      updatedAt: '',
      location: ''
    }
  }
  return {
    publishedAt: post.value.createdAt.slice(0, 10),
    updatedAt: post.value.createdAt.slice(0, 10),
    location: post.value.author.ipLocation
  }
})

const contentParagraphs = computed(() => {
  if (!post.value) return []
  // 按空行拆分 Markdown 文本，简单渲染为段落
  return post.value.content
    .split(/\n\s*\n/)
    .map((p) => p.replace(/^[#>\-*\s`]+/g, '').trim())
    .filter(Boolean)
})

const isLikedDetail = ref(false)
const likeCount = ref(post.value?.stats.likes ?? 0)
const isStarred = ref(false)

const comments = ref<CommentItem[]>([
  {
    id: 1,
    authorName: '算法小白',
    avatarUrl: 'https://picsum.photos/id/500/48/48',
    content: '讲得很细，尤其是边界用例那一段，直接按这个 checklist 复盘自己的代码就行了。',
    createdAt: '1 小时前',
    ipLocation: '浙江杭州'
  },
  {
    id: 2,
    authorName: '秋招冲刺中',
    avatarUrl: 'https://picsum.photos/id/501/48/48',
    content: '刚面完字节二面，题目几乎一模一样，看完这篇感觉之前的写法还能再优化一层。',
    createdAt: '3 小时前',
    ipLocation: '北京'
  }
])

function formatCount(n: number): string {
  if (n >= 100000) return `${Math.round(n / 1000)}k`
  if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

function toggleLike() {
  if (isLikedDetail.value) {
    likeCount.value = Math.max(0, likeCount.value - 1)
    isLikedDetail.value = false
  } else {
    likeCount.value += 1
    isLikedDetail.value = true
  }
}

function toggleStar() {
  isStarred.value = !isStarred.value
}
</script>

