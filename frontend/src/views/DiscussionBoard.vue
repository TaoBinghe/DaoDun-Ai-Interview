<template>
  <div class="min-h-[calc(100vh-64px)] bg-[#141413] px-4 py-8 text-[#f5f5f5]">
    <div class="mx-auto max-w-4xl">
      <header class="mb-8 border-b border-white/10 pb-6">
        <h1 class="text-2xl font-semibold tracking-tight text-[#faf9f5]">讨论区</h1>
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
              effect="dark"
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
              <div class="text-[13px] text-white">
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
              <span class="text-gray-400">{{ post.timeLabel }}</span>
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
                class="inline-flex items-center gap-1.5 rounded-md px-1 py-0.5 transition-colors"
                :class="
                  post.isLiked
                    ? 'text-emerald-400 hover:text-emerald-300'
                    : 'text-gray-500 hover:text-gray-300'
                "
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
  isLiked: boolean
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
    title: '字节跳动面经：LRU 缓存设计与 O(1) 淘汰复盘',
    summary:
      '从哈希表定位到双向链表维护访问顺序，实现 get/put 均摊 O(1)；重点梳理容量淘汰与边界测试点。',
    tags: ['LRU缓存', '算法', '字节跳动'],
    likes: 428,
    comments: 56,
    views: 12040,
    thumbnail: 'https://picsum.photos/id/180/96/96',
    isLiked: false
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
    title: '腾讯面经：二叉树路径求和与序列化高频复盘',
    summary:
      '覆盖递归与迭代两套实现；用前缀和剪枝降低搜索量，并补上树的序列化/空节点边界处理。',
    tags: ['二叉树', '腾讯', '算法'],
    likes: 612,
    comments: 89,
    views: 18920,
    thumbnail: 'https://picsum.photos/id/119/96/96',
    isLiked: false
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
    title: '美团技术面：动态规划 DP 状态转移三步走',
    summary:
      '从状态定义到转移方程与边界条件，统一讲清 dp[i]/dp[i][j] 如何落地；最后再做状态压缩与复杂度核对。',
    tags: ['动态规划', 'DP', '美团'],
    likes: 903,
    comments: 132,
    views: 35600,
    thumbnail: 'https://picsum.photos/id/201/96/96',
    isLiked: false
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
    title: '字节跳动面经：贪心与回溯混合策略（区间/排列）',
    summary:
      '区间类用交换论证/单调性把思路推到最优；回溯部分用剪枝清单控制搜索空间，附一道真题建模与反例。',
    tags: ['贪心', '回溯', '字节跳动'],
    likes: 341,
    comments: 47,
    views: 9800,
    thumbnail: 'https://picsum.photos/id/29/96/96',
    isLiked: false
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
    title: '腾讯笔试复盘：滑动窗口与哈希查重套路',
    summary:
      '固定窗口 vs 可变窗口怎么选；字符集大小决定数组还是 Map；附边界用例与复杂度验证。',
    tags: ['滑动窗口', '腾讯', '算法'],
    likes: 756,
    comments: 98,
    views: 22100,
    thumbnail: 'https://picsum.photos/id/48/96/96',
    isLiked: false
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
    title: '动态规划高频：最长公共子序列 LCS 与状态压缩',
    summary:
      '从二维 dp[i][j] 到一维滚动数组：讲清索引方向与覆盖风险；补几道常见变体的写法对比。',
    tags: ['动态规划', 'LCS', '算法'],
    likes: 512,
    comments: 71,
    views: 15400,
    thumbnail: 'https://picsum.photos/id/250/96/96',
    isLiked: false
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
    title: '二叉树高频：层序遍历与镜像翻转（队列/递归）',
    summary:
      '用队列实现层序并处理空节点；递归版如何写得更稳；最后总结镜像翻转的边界细节。',
    tags: ['二叉树', 'BFS', '面试'],
    likes: 604,
    comments: 88,
    views: 16800,
    thumbnail: 'https://picsum.photos/id/366/96/96',
    isLiked: false
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
    title: '工程算法：LRU 缓存容量淘汰与一致性口径',
    summary:
      '访问即更新的规则梳理；被淘汰元素统计的实现细节；附 LRU 边界测试点与易错场景。',
    tags: ['LRU缓存', '一致性', '算法'],
    likes: 389,
    comments: 44,
    views: 7600,
    thumbnail: 'https://picsum.photos/id/193/96/96',
    isLiked: false
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
    title: '动态规划进阶：树形 DP 子问题合并与后序遍历',
    summary:
      '后序遍历定义每个子树状态；说明合并顺序和 dp 的不变式；避免漏状态导致的转移漏洞。',
    tags: ['动态规划', '树形DP', '算法'],
    likes: 467,
    comments: 52,
    views: 11200,
    thumbnail: 'https://picsum.photos/id/28/96/96',
    isLiked: false
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
    title: '美团面经：并查集路径压缩与按秩合并证明直觉',
    summary:
      '用不变式理解 find/union 的正确性；通过冗余连接题复盘边界与均摊复杂度；附关键代码骨架。',
    tags: ['并查集', '美团', '面试真题'],
    likes: 445,
    comments: 41,
    views: 9900,
    thumbnail: 'https://picsum.photos/id/106/96/96',
    isLiked: false
  }
])

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
    baseLikes.set(p.id, p.likes)
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
    p.likes = (baseLikes.get(p.id) ?? p.likes) + 1
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
    p.likes = Math.max(0, p.likes - 1)
    p.isLiked = false
  } else {
    p.likes += 1
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

function openPostDetail(post: Post) {
  emit('openDetail', post)
  window.alert('展示帖子详情及评论区')
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
