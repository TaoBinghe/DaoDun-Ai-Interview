<template>
  <div class="theme-page min-h-[calc(100vh-64px)] px-4 py-8">
    <div class="mx-auto max-w-4xl">
      <header class="mb-6">
        <h1 class="theme-title text-2xl font-semibold tracking-tight">讨论区</h1>
      </header>

      <div
        class="discussion-search mb-6 flex items-center gap-3 rounded-xl px-3 py-2"
      >
        <Search class="h-4 w-4 text-gray-400" :stroke-width="2" />
        <input
          v-model="searchQuery"
          type="text"
          class="flex-1 bg-transparent text-sm theme-title placeholder:theme-text-faint focus:outline-none"
          placeholder="搜索标题或标签..."
          @keydown.esc="searchQuery = ''"
        />
      </div>

      <ul class="discussion-list divide-y">
        <li
          v-for="post in filteredPosts"
          :key="post.id"
          class="flex cursor-pointer gap-4 py-5 first:pt-0"
          @click="openPostDetail(post)"
        >
          <div class="shrink-0" @click.stop>
            <el-popover
              placement="bottom-start"
              :width="300"
              trigger="hover"
              effect="dark"
              popper-class="discussion-author-popover"
            >
              <template #reference>
                <img
                  :src="post.author.avatar"
                  :alt="post.author.username"
                  class="h-10 w-10 cursor-default rounded-full object-cover ring-1 ring-white/10"
                  width="40"
                  height="40"
                />
              </template>
              <div class="text-[13px] theme-title">
                <div class="discussion-popover-section flex gap-3 border-b pb-3">
                  <img
                    :src="post.author.avatar"
                    alt=""
                    class="h-12 w-12 shrink-0 rounded-full object-cover ring-1 ring-white/10"
                    width="48"
                    height="48"
                  />
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-1">
                      <span class="truncate font-medium theme-title">{{ post.author.username }}</span>
                      <BadgeCheck
                        v-if="post.author.verified"
                        class="h-4 w-4 shrink-0 text-sky-400"
                        :stroke-width="2"
                        aria-hidden="true"
                      />
                      <p class="ml-auto text-xs theme-title text-right">IP：{{ post.author.ipLocation }}</p>
                    </div>
                    <p class="mt-0.5 text-xs theme-title">{{ post.author.rank }}</p>
                    
                  </div>
                </div>
                <div class="discussion-popover-section grid grid-cols-4 gap-2 border-b py-3 text-center">
                  <div>
                    <p class="text-[10px] theme-text-muted">被阅读</p>
                    <p class="font-bold theme-title">{{ formatCount(post.author.readCount) }}</p>
                  </div>
                  <div>
                    <p class="text-[10px] theme-text-muted">被点赞</p>
                    <p class="font-bold theme-title">{{ formatCount(post.author.receivedLikes) }}</p>
                  </div>
                  <div>
                    <p class="text-[10px] theme-text-muted">被收藏</p>
                    <p class="font-bold theme-title">{{ formatCount(post.author.favorites) }}</p>
                  </div>
                  <div>
                    <p class="text-[10px] theme-text-muted">关注者</p>
                    <p class="font-bold theme-title">{{ formatCount(post.author.followers) }}</p>
                  </div>
                </div>
                <el-button
                  class="mt-3 w-full !rounded-lg"
                  :class="
                    post.author.isFollowing
                      ? 'discussion-following'
                      : 'discussion-follow'
                  "
                  @click.stop="toggleFollow(post.id)"
                >
                  {{ post.author.isFollowing ? '已关注' : '+ 关注' }}
                </el-button>
              </div>
            </el-popover>
          </div>

          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm">
              <span class="theme-title">{{ post.author.username }}</span>
              <span class="text-white/20">·</span>
              <span class="text-gray-400">{{ post.createdAt }}</span>
            </div>
            <h2 class="mb-1 mt-1 text-lg font-bold theme-title">
              {{ post.title }}
            </h2>
            <p class="line-clamp-2 text-sm leading-relaxed theme-text-muted">
              {{ getPreview(post.content) }}
            </p>

            <div class="mt-2 flex flex-wrap gap-1.5">
              <el-tag
                v-for="tag in post.tags"
                :key="tag"
                size="small"
                effect="dark"
                class="discussion-tag"
                @click.stop
              >
                {{ tag }}
              </el-tag>
            </div>

            <div class="mt-3 flex flex-wrap items-center gap-4 text-sm theme-text-faint">
              <button
                type="button"
                class="inline-flex items-center gap-1.5 rounded-md px-1 py-0.5 transition-colors"
                :class="
                  post.isLiked
                    ? 'discussion-liked'
                    : 'discussion-action'
                "
                @click.stop="handleLike(post.id)"
              >
                <ThumbsUp class="h-4 w-4 shrink-0" :stroke-width="2" />
                <span>{{ post.stats.likes }}</span>
              </button>
              <button
                type="button"
                class="discussion-action inline-flex items-center gap-1.5 rounded-md px-1 py-0.5 transition-colors"
                @click.stop="handleComment(post.id)"
              >
                <MessageCircle class="h-4 w-4 shrink-0" :stroke-width="2" />
                <span>{{ post.stats.comments }}</span>
              </button>
              <span class="inline-flex items-center gap-1.5 theme-text-faint">
                <Eye class="h-4 w-4 shrink-0" :stroke-width="2" />
                <span>{{ post.stats.views }}</span>
              </span>
            </div>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ThumbsUp, MessageCircle, Eye, BadgeCheck, Search } from 'lucide-vue-next'
import { forumPosts, type ForumPost } from '@/data/forumPosts'

const searchQuery = ref('')
const router = useRouter()

function formatCount(n: number): string {
  if (n >= 100000) return `${Math.round(n / 1000)}k`
  if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

const posts = ref<ForumPost[]>(forumPosts.map((p) => ({ ...p })))

const filteredPosts = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return posts.value
  return posts.value.filter((post) => {
    const inTitle = post.title.toLowerCase().includes(q)
    const inTags = post.tags.some((tag) => tag.toLowerCase().includes(q))
    return inTitle || inTags
  })
})

function pickRandomIndices(total: number, count: number): number[] {
  const idxs = Array.from({ length: total }, (_, i) => i)
  // Fisher–Yates shuffle
  for (let i = idxs.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    // TS 对索引取值会推断为可能 undefined，这里通过非空断言表达“索引必然有效”
    const tmp = idxs[i]!
    idxs[i] = idxs[j]!
    idxs[j] = tmp
  }
  return idxs.slice(0, count)
}

function seedRandomStates() {
  const baseLikes = new Map<number, number>()
  const baseFollowers = new Map<number, number>()

  // 以当前 posts 的数值作为“未点赞/未关注”的基准，然后随机加 1 展示状态。
  posts.value.forEach((p) => {
    baseLikes.set(p.id, p.stats.likes)
    baseFollowers.set(p.id, p.author.followers)
    p.isLiked = false
    p.author.isFollowing = false
  })

  const total = posts.value.length
  const likeCount = Math.max(2, Math.min(total - 1, 2 + Math.floor(Math.random() * 4)))
  const followCount = Math.max(2, Math.min(total - 1, 2 + Math.floor(Math.random() * 4)))

  for (const i of pickRandomIndices(total, likeCount)) {
    const p = posts.value[i]
    if (!p) continue
    p.isLiked = true
    p.stats.likes = (baseLikes.get(p.id) ?? p.stats.likes) + 1
  }

  for (const i of pickRandomIndices(total, followCount)) {
    const p = posts.value[i]
    if (!p) continue
    p.author.isFollowing = true
    p.author.followers = (baseFollowers.get(p.id) ?? p.author.followers) + 1
  }
}

seedRandomStates()

function handleLike(id: number) {
  const p = posts.value.find((x) => x.id === id)
  if (!p) return
  if (p.isLiked) {
    p.stats.likes = Math.max(0, p.stats.likes - 1)
    p.isLiked = false
  } else {
    p.stats.likes += 1
    p.isLiked = true
  }
}

function handleComment(_id: number) {
  void _id
  window.alert('正在进入详情页')
}

function toggleFollow(postId: number) {
  const p = posts.value.find((x) => x.id === postId)
  if (!p) return
  if (p.author.isFollowing) {
    p.author.isFollowing = false
    p.author.followers = Math.max(0, p.author.followers - 1)
  } else {
    p.author.isFollowing = true
    p.author.followers += 1
  }
}

function getPreview(content: string, length = 80): string {
  const plain = content.replace(/[#>*`_\\-]/g, '').replace(/\\s+/g, ' ').trim()
  if (plain.length <= length) return plain
  return `${plain.slice(0, length)}...`
}

function openPostDetail(post: ForumPost) {
  router.push(`/post/${post.id}`)
}
</script>

<style scoped>
:deep(.discussion-author-popover) {
  --el-bg-color-overlay: var(--app-surface);
  --el-border-color-light: var(--app-border);
  background: var(--app-surface) !important;
  border: 1px solid var(--app-border) !important;
  color: var(--app-text);
  box-shadow: var(--app-shadow) !important;
}

.discussion-search {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
}

.discussion-list {
  border-color: var(--app-border);
}

.discussion-popover-section {
  border-color: var(--app-border);
}

.discussion-tag {
  border-color: var(--app-border) !important;
  background: var(--app-surface-strong) !important;
  color: var(--app-text-muted) !important;
}

.discussion-follow {
  background: var(--app-accent) !important;
  border-color: transparent !important;
  color: var(--app-accent-contrast) !important;
}

.discussion-following {
  background: var(--app-surface-strong) !important;
  border-color: var(--app-border-strong) !important;
  color: var(--app-text-soft) !important;
}

.discussion-action {
  color: var(--app-text-faint);
}

.discussion-action:hover {
  color: var(--app-text);
}

.discussion-liked {
  color: var(--app-success);
}

.discussion-liked:hover {
  color: color-mix(in srgb, var(--app-success) 84%, white);
}
</style>
