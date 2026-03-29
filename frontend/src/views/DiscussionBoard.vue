<template>
  <div class="min-h-[calc(100vh-64px)] bg-[#141413] px-4 py-8 text-[#f5f5f5]">
    <div class="mx-auto max-w-4xl">
      <header class="mb-8 border-b border-white/10 pb-6">
        <h1 class="text-2xl font-semibold tracking-tight text-[#faf9f5]">讨论区</h1>
        <p class="mt-2 text-sm text-gray-400">硬核算法 · 贪心与回溯 · 大厂真题复盘</p>
      </header>

      <ul class="divide-y divide-white/5">
        <li
          v-for="post in posts"
          :key="post.id"
          class="flex cursor-pointer gap-4 py-5 transition-colors first:pt-0 hover:bg-white/[0.02]"
          @click="openPostDetail(post)"
        >
          <div class="shrink-0" @click.stop>
            <el-popover
              placement="bottom-start"
              :width="300"
              trigger="hover"
              popper-class="discussion-author-popover"
            >
              <template #reference>
                <img
                  :src="post.author.avatarUrl"
                  :alt="post.author.username"
                  class="h-10 w-10 cursor-default rounded-full object-cover ring-1 ring-white/10"
                  width="40"
                  height="40"
                />
              </template>
              <div class="text-[13px] text-gray-200">
                <div class="flex gap-3 border-b border-white/10 pb-3">
                  <img
                    :src="post.author.avatarUrl"
                    alt=""
                    class="h-12 w-12 shrink-0 rounded-full object-cover ring-1 ring-white/10"
                    width="48"
                    height="48"
                  />
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-1">
                      <span class="truncate font-medium text-gray-100">{{ post.author.username }}</span>
                      <BadgeCheck
                        v-if="post.author.verified"
                        class="h-4 w-4 shrink-0 text-sky-400"
                        :stroke-width="2"
                        aria-hidden="true"
                      />
                    </div>
                    <p class="mt-0.5 text-xs text-gray-400">{{ post.author.rank }}</p>
                    <p class="mt-0.5 text-xs text-gray-500">IP：{{ post.author.ipLocation }}</p>
                  </div>
                </div>
                <div class="grid grid-cols-4 gap-2 border-b border-white/10 py-3 text-center">
                  <div>
                    <p class="text-[10px] text-gray-500">被阅读</p>
                    <p class="font-bold text-gray-100">{{ formatCount(post.author.readCount) }}</p>
                  </div>
                  <div>
                    <p class="text-[10px] text-gray-500">被点赞</p>
                    <p class="font-bold text-gray-100">{{ formatCount(post.author.receivedLikes) }}</p>
                  </div>
                  <div>
                    <p class="text-[10px] text-gray-500">被收藏</p>
                    <p class="font-bold text-gray-100">{{ formatCount(post.author.favorites) }}</p>
                  </div>
                  <div>
                    <p class="text-[10px] text-gray-500">关注者</p>
                    <p class="font-bold text-gray-100">{{ formatCount(post.author.followers) }}</p>
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
            <div class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm text-gray-400">
              <span>{{ post.author.username }}</span>
              <span class="text-white/20">·</span>
              <span>{{ post.timeLabel }}</span>
            </div>
            <h2 class="mb-1 mt-1 text-lg font-bold text-gray-100" @click.stop="openPostDetail(post)">
              {{ post.title }}
            </h2>
            <p class="line-clamp-2 text-sm leading-relaxed text-gray-400">
              {{ post.summary }}
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
                # {{ tag }}
              </el-tag>
            </div>

            <div class="mt-3 flex flex-wrap items-center gap-4 text-sm text-gray-500">
              <button
                type="button"
                class="inline-flex items-center gap-1.5 rounded-md px-1 py-0.5 transition-colors hover:text-gray-300"
                :class="post.liked ? 'text-emerald-400' : 'text-gray-500'"
                @click.stop="handleLike(post.id)"
              >
                <ThumbsUp class="h-4 w-4 shrink-0" :stroke-width="2" />
                <span>{{ post.likes }}</span>
              </button>
              <button
                type="button"
                class="inline-flex items-center gap-1.5 rounded-md px-1 py-0.5 transition-colors hover:text-gray-300"
                @click.stop="handleComment(post.id)"
              >
                <MessageCircle class="h-4 w-4 shrink-0" :stroke-width="2" />
                <span>{{ post.comments }}</span>
              </button>
              <span class="inline-flex items-center gap-1.5 text-gray-500">
                <Eye class="h-4 w-4 shrink-0" :stroke-width="2" />
                <span>{{ post.views }}</span>
              </span>
            </div>
          </div>

          <div class="hidden shrink-0 md:block" @click.stop="openPostDetail(post)">
            <img
              :src="post.thumbnail"
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
import { ref } from 'vue'
import { ThumbsUp, MessageCircle, Eye, BadgeCheck } from 'lucide-vue-next'

export interface AuthorProfile {
  id: number
  username: string
  rank: string
  ipLocation: string
  verified: boolean
  readCount: number
  receivedLikes: number
  favorites: number
  followers: number
  avatarUrl: string
  isFollowing: boolean
}

export interface Post {
  id: number
  author: AuthorProfile
  timeLabel: string
  title: string
  summary: string
  tags: string[]
  likes: number
  comments: number
  views: number
  thumbnail: string
  liked: boolean
}

const emit = defineEmits<{
  openDetail: [post: Post]
}>()

function formatCount(n: number): string {
  if (n >= 100000) return `${Math.round(n / 1000)}k`
  if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

const posts = ref<Post[]>([
  {
    id: 1,
    author: {
      id: 101,
      username: '贪心练习生',
      rank: '全站第 86 名',
      ipLocation: '浙江杭州',
      verified: true,
      readCount: 128000,
      receivedLikes: 9200,
      favorites: 3100,
      followers: 5600,
      avatarUrl: 'https://picsum.photos/id/64/80/80',
      isFollowing: false
    },
    timeLabel: '2 小时前',
    title: '贪心策略证明：区间选点与活动选择问题',
    summary:
      '从交换论证到拟阵，说明为何局部最优能推出全局最优；附一道字节真题的建模与反例构造。',
    tags: ['算法', '贪心', '字节跳动'],
    likes: 428,
    comments: 56,
    views: 12040,
    thumbnail: 'https://picsum.photos/id/180/96/96',
    liked: false
  },
  {
    id: 2,
    author: {
      id: 102,
      username: '回溯钉子户',
      rank: '全站第 142 名',
      ipLocation: '广东深圳',
      verified: true,
      readCount: 96000,
      receivedLikes: 7100,
      favorites: 2400,
      followers: 4200,
      avatarUrl: 'https://picsum.photos/id/65/80/80',
      isFollowing: false
    },
    timeLabel: '5 小时前',
    title: '子集与排列：回溯模板与剪枝清单',
    summary:
      '统一「选或不选」与「按位填」两种写法；重点写剪枝条件如何减少指数爆炸，附 N 皇后复杂度直觉。',
    tags: ['算法', '回溯', '面经'],
    likes: 612,
    comments: 89,
    views: 18920,
    thumbnail: 'https://picsum.photos/id/119/96/96',
    liked: false
  },
  {
    id: 3,
    author: {
      id: 103,
      username: 'DP 学徒',
      rank: '全站第 55 名',
      ipLocation: '上海',
      verified: true,
      readCount: 210000,
      receivedLikes: 18000,
      favorites: 6200,
      followers: 9800,
      avatarUrl: 'https://picsum.photos/id/91/80/80',
      isFollowing: false
    },
    timeLabel: '12 小时前',
    title: '动态规划：状态压缩与背包九讲速记',
    summary:
      '从斐波那契到子集 DP，强调「状态定义—转移—边界」三板斧；对比滚动数组与 bitmask 适用场景。',
    tags: ['算法', '动态规划', '大厂真题'],
    likes: 903,
    comments: 132,
    views: 35600,
    thumbnail: 'https://picsum.photos/id/201/96/96',
    liked: false
  },
  {
    id: 4,
    author: {
      id: 104,
      username: '图论 AC',
      rank: '全站第 201 名',
      ipLocation: '北京',
      verified: false,
      readCount: 45000,
      receivedLikes: 3200,
      favorites: 1100,
      followers: 2100,
      avatarUrl: 'https://picsum.photos/id/177/80/80',
      isFollowing: false
    },
    timeLabel: '1 天前',
    title: 'Dijkstra 与堆优化：负权边为何失效',
    summary:
      '手写邻接表 + 二叉堆松弛；对比 SPFA 适用边界，附一道图论面试常考题的建图技巧。',
    tags: ['算法', '图论', '面试真题'],
    likes: 341,
    comments: 47,
    views: 9800,
    thumbnail: 'https://picsum.photos/id/29/96/96',
    liked: false
  },
  {
    id: 5,
    author: {
      id: 105,
      username: '真题挖掘机',
      rank: '全站第 38 名',
      ipLocation: '江苏南京',
      verified: true,
      readCount: 302000,
      receivedLikes: 24000,
      favorites: 9100,
      followers: 15000,
      avatarUrl: 'https://picsum.photos/id/338/80/80',
      isFollowing: false
    },
    timeLabel: '1 天前',
    title: '阿里笔试复盘：双指针与单调队列组合拳',
    summary:
      '2025 秋招真题拆解，如何把 O(n²) 暴力优化到 O(n log n)；附边界测试用例设计思路。',
    tags: ['大厂真题', '双指针', '单调队列'],
    likes: 756,
    comments: 98,
    views: 22100,
    thumbnail: 'https://picsum.photos/id/48/96/96',
    liked: false
  },
  {
    id: 6,
    author: {
      id: 106,
      username: '二分查找狂魔',
      rank: '全站第 120 名',
      ipLocation: '四川成都',
      verified: false,
      readCount: 72000,
      receivedLikes: 5100,
      favorites: 1800,
      followers: 3600,
      avatarUrl: 'https://picsum.photos/id/1005/80/80',
      isFollowing: false
    },
    timeLabel: '2 天前',
    title: '二分答案：最大化最小值与第 K 小数的套路',
    summary:
      'check 函数单调性判定、边界 mid 选取防死循环；结合一道「最小化最大值」真题手写模板。',
    tags: ['算法', '二分', '面经'],
    likes: 512,
    comments: 71,
    views: 15400,
    thumbnail: 'https://picsum.photos/id/250/96/96',
    liked: false
  },
  {
    id: 7,
    author: {
      id: 107,
      username: '滑动窗口练习生',
      rank: '全站第 95 名',
      ipLocation: '湖北武汉',
      verified: true,
      readCount: 88000,
      receivedLikes: 6400,
      favorites: 2200,
      followers: 4100,
      avatarUrl: 'https://picsum.photos/id/1027/80/80',
      isFollowing: false
    },
    timeLabel: '3 天前',
    title: '滑动窗口与哈希：最长无重复子串变体',
    summary:
      '固定窗口与可变窗口对比；字符集大小决定用数组还是 Map，附腾讯笔试同类题扩展。',
    tags: ['算法', '滑动窗口', '腾讯'],
    likes: 604,
    comments: 88,
    views: 16800,
    thumbnail: 'https://picsum.photos/id/366/96/96',
    liked: false
  },
  {
    id: 8,
    author: {
      id: 108,
      username: '单调栈笔记',
      rank: '全站第 167 名',
      ipLocation: '陕西西安',
      verified: false,
      readCount: 39000,
      receivedLikes: 2800,
      favorites: 900,
      followers: 1700,
      avatarUrl: 'https://picsum.photos/id/129/80/80',
      isFollowing: false
    },
    timeLabel: '3 天前',
    title: '单调栈：接雨水与柱状图最大矩形',
    summary:
      '一次遍历维护递增/递减栈的直觉；把高度与索引同时入栈，复盘两道高频手写题边界。',
    tags: ['算法', '单调栈', '面试'],
    likes: 389,
    comments: 44,
    views: 7600,
    thumbnail: 'https://picsum.photos/id/193/96/96',
    liked: false
  },
  {
    id: 9,
    author: {
      id: 109,
      username: 'Trie 手写匠',
      rank: '全站第 74 名',
      ipLocation: '浙江杭州',
      verified: true,
      readCount: 105000,
      receivedLikes: 7800,
      favorites: 2600,
      followers: 4800,
      avatarUrl: 'https://picsum.photos/id/342/80/80',
      isFollowing: false
    },
    timeLabel: '4 天前',
    title: 'Trie 树与前缀匹配：搜索引擎补全思路',
    summary:
      '节点压缩与双数组 Trie 简介；结合一道多模式匹配题说明 DFS 在树上的剪枝。',
    tags: ['算法', 'Trie', '字符串'],
    likes: 467,
    comments: 52,
    views: 11200,
    thumbnail: 'https://picsum.photos/id/28/96/96',
    liked: false
  },
  {
    id: 10,
    author: {
      id: 110,
      username: '并查集入门',
      rank: '全站第 210 名',
      ipLocation: '福建厦门',
      verified: false,
      readCount: 33000,
      receivedLikes: 2400,
      favorites: 800,
      followers: 1500,
      avatarUrl: 'https://picsum.photos/id/447/80/80',
      isFollowing: false
    },
    timeLabel: '5 天前',
    title: '并查集：路径压缩与按秩合并证明直觉',
    summary:
      '从 Kruskal 到连通块计数；均摊复杂度直觉解释，附一道「冗余连接」真题代码骨架。',
    tags: ['算法', '并查集', '图论'],
    likes: 445,
    comments: 41,
    views: 9900,
    thumbnail: 'https://picsum.photos/id/106/96/96',
    liked: false
  }
])

function handleLike(id: number) {
  const p = posts.value.find((x) => x.id === id)
  if (!p) return
  if (!p.liked) {
    p.likes += 1
    p.liked = true
  }
}

function handleComment(_id: number) {
  window.alert('正在进入详情页')
}

function toggleFollow(postId: number) {
  const p = posts.value.find((x) => x.id === postId)
  if (!p) return
  p.author.isFollowing = !p.author.isFollowing
}

function openPostDetail(post: Post) {
  emit('openDetail', post)
  window.alert('展示帖子详情及评论区')
}
</script>

<style scoped>
:deep(.discussion-author-popover) {
  --el-bg-color-overlay: #1f1e1d;
  --el-border-color-light: rgba(255, 255, 255, 0.12);
  background: #1f1e1d !important;
  border: 1px solid rgba(255, 255, 255, 0.12) !important;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45) !important;
}
</style>
