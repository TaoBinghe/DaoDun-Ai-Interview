<template>
  <div class="min-h-[calc(100vh-64px)] bg-[#141413] px-4 py-8 text-[#f5f5f5]">
    <div class="mx-auto max-w-4xl">
      <header class="mb-6">
        <h1 class="text-2xl font-semibold tracking-tight text-[#faf9f5]">讨论区</h1>
      </header>

      <div
        class="mb-6 flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-3 py-2"
      >
        <Search class="h-4 w-4 text-gray-400" :stroke-width="2" />
        <input
          v-model="searchQuery"
          type="text"
          class="flex-1 bg-transparent text-sm text-gray-100 placeholder:text-gray-500 focus:outline-none"
          placeholder="搜索标题或标签..."
          @keydown.esc="searchQuery = ''"
        />
      </div>

      <ul class="divide-y divide-white/5">
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
              <div class="text-[13px] text-white">
                <div class="flex gap-3 border-b border-white/10 pb-3">
                  <img
                    :src="post.author.avatar"
                    alt=""
                    class="h-12 w-12 shrink-0 rounded-full object-cover ring-1 ring-white/10"
                    width="48"
                    height="48"
                  />
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-1">
                      <span class="truncate font-medium text-white">{{ post.author.username }}</span>
                      <BadgeCheck
                        v-if="post.author.verified"
                        class="h-4 w-4 shrink-0 text-sky-400"
                        :stroke-width="2"
                        aria-hidden="true"
                      />
                    <p class="ml-auto text-xs text-white text-right">IP：{{ post.author.ipLocation }}</p>
                    </div>
                    <p class="mt-0.5 text-xs text-white">{{ post.author.rank }}</p>
                    
                  </div>
                </div>
                <div class="grid grid-cols-4 gap-2 border-b border-white/10 py-3 text-center">
                  <div>
                    <p class="text-[10px] text-gray-300">被阅读</p>
                    <p class="font-bold text-white">{{ formatCount(post.author.readCount) }}</p>
                  </div>
                  <div>
                    <p class="text-[10px] text-gray-300">被点赞</p>
                    <p class="font-bold text-white">{{ formatCount(post.author.receivedLikes) }}</p>
                  </div>
                  <div>
                    <p class="text-[10px] text-gray-300">被收藏</p>
                    <p class="font-bold text-white">{{ formatCount(post.author.favorites) }}</p>
                  </div>
                  <div>
                    <p class="text-[10px] text-gray-300">关注者</p>
                    <p class="font-bold text-white">{{ formatCount(post.author.followers) }}</p>
                  </div>
                </div>
                <el-button
                  class="mt-3 w-full !rounded-lg"
                  :class="
                    post.author.isFollowing
                      ? '!border-zinc-600 !bg-zinc-600 !text-gray-100'
                      : '!border-emerald-600 !bg-emerald-600 !text-white'
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
              <span class="text-gray-100">{{ post.author.username }}</span>
              <span class="text-white/20">·</span>
              <span class="text-gray-400">{{ post.createdAt }}</span>
            </div>
            <h2 class="mb-1 mt-1 text-lg font-bold text-gray-100">
              {{ post.title }}
            </h2>
            <p class="line-clamp-2 text-sm leading-relaxed text-gray-400">
              {{ getPreview(post.content) }}
            </p>

            <div class="mt-2 flex flex-wrap gap-1.5">
              <el-tag
                v-for="tag in post.tags"
                :key="tag"
                size="small"
                effect="dark"
                class="!border-white/10 !bg-zinc-700/80 !text-gray-300"
                @click.stop
              >
                {{ tag }}
              </el-tag>
            </div>

            <div class="mt-3 flex flex-wrap items-center gap-4 text-sm text-gray-500">
              <button
                type="button"
                class="inline-flex items-center gap-1.5 rounded-md px-1 py-0.5 transition-colors"
                :class="
                  post.isLiked
                    ? 'text-emerald-400 hover:text-emerald-300'
                    : 'text-gray-500 hover:text-gray-300'
                "
                @click.stop="handleLike(post.id)"
              >
                <ThumbsUp class="h-4 w-4 shrink-0" :stroke-width="2" />
                <span>{{ post.stats.likes }}</span>
              </button>
              <button
                type="button"
                class="inline-flex items-center gap-1.5 rounded-md px-1 py-0.5 transition-colors hover:text-gray-300"
                @click.stop="handleComment(post.id)"
              >
                <MessageCircle class="h-4 w-4 shrink-0" :stroke-width="2" />
                <span>{{ post.stats.comments }}</span>
              </button>
              <span class="inline-flex items-center gap-1.5 text-gray-500">
                <Eye class="h-4 w-4 shrink-0" :stroke-width="2" />
                <span>{{ post.stats.views }}</span>
              </span>
            </div>
          </div>

          <div class="hidden shrink-0 md:block" @click.stop>
            <img
              :src="post.author.avatar"
              alt=""
              class="h-24 w-24 rounded-lg object-cover ring-1 ring-white/10"
              width="96"
              height="96"
            />
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
  --el-bg-color-overlay: #2a2b2a;
  --el-border-color-light: #ffffff14;
  background: #2a2b2a !important;
  border: 1px solid #ffffff14 !important;
  color: #fff;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45) !important;
}
</style>
