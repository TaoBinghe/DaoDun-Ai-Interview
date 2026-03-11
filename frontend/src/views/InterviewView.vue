<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useDark } from '@vueuse/core'
import { ArrowLeft, Promotion, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const isDark = useDark()

// --- 状态变量 ---
const sessionId = ref<number | null>(null)
const positionId = ref<number | null>(null)
const positions = ref<any[]>([])
const turns = ref<any[]>([])
const userInput = ref('')
const isLoading = ref(false)
const isEnding = ref(false)
const chatBody = ref<HTMLElement | null>(null)

// --- API 请求头 ---
const getHeaders = () => ({
  'Content-Type': 'application/json',
  'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
})

/** 若为 401 则跳转登录并返回 true */
const checkUnauthorized = (res: Response): boolean => {
  if (res.status === 401) {
    ElMessage.error('登录已过期，请重新登录')
    router.push('/login')
    return true
  }
  return false
}

// --- 初始化：获取岗位列表 ---
onMounted(async () => {
  const token = localStorage.getItem('accessToken')
  if (!token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    const res = await fetch('/api/position/list', { headers: getHeaders() })
    const json = await res.json()
    if (res.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      router.push('/login')
      return
    }
    if (json.code === 200) {
      positions.value = json.data
    } else {
      ElMessage.error(json.msg || '获取岗位列表失败')
    }
  } catch (err) {
    console.error(err)
    ElMessage.error('网络错误，请检查后端是否启动（端口 8081）')
  }
})

// --- 开始面试 ---
const startInterview = async (id: number) => {
  positionId.value = id
  isLoading.value = true
  try {
    const res = await fetch('/api/interview/sessions', {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ positionId: id })
    })
    const json = await res.json()
    if (checkUnauthorized(res)) return
    if (json.code === 200) {
      sessionId.value = json.data.sessionId
      turns.value = json.data.turns
      ElMessage.success('面试开始，祝你表现顺利！')
      scrollToBottom()
    } else {
      ElMessage.error(json.msg || '创建面试失败')
    }
  } catch (err) {
    ElMessage.error('系统异常')
  } finally {
    isLoading.value = false
  }
}

// --- 发送回答 ---
const sendMessage = async () => {
  if (!userInput.value.trim() || isLoading.value || !sessionId.value) return

  const content = userInput.value.trim()
  userInput.value = ''
  isLoading.value = true

  // 1. 立即展示用户话语
  const clientTurnId = crypto.randomUUID()
  const userTurn = {
    role: 'USER',
    content: content,
    createTime: new Date().toISOString()
  }
  turns.value.push(userTurn)
  scrollToBottom()

  try {
    // 2. 发送到后端
    const res = await fetch(`/api/interview/sessions/${sessionId.value}/turns`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ content, clientTurnId })
    })
    const json = await res.json()
    if (checkUnauthorized(res)) return
    if (json.code === 200) {
      // 3. 更新面试官回复
      // 后端 postTurn 返回了 userTurn 和 interviewerTurn，我们用后端的覆盖前端模拟的
      const idx = turns.value.indexOf(userTurn)
      if (idx !== -1) turns.value.splice(idx, 1)
      
      turns.value.push(json.data.userTurn)
      turns.value.push(json.data.interviewerTurn)
      scrollToBottom()
    } else {
      ElMessage.error(json.msg || '发送失败')
    }
  } catch (err) {
    ElMessage.error('网络连接失败')
  } finally {
    isLoading.value = false
  }
}

// --- 结束面试 ---
const handleComplete = async () => {
  if (!sessionId.value) return
  
  try {
    await ElMessageBox.confirm('确定要结束本次模拟面试吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    isEnding.value = true
    const res = await fetch(`/api/interview/sessions/${sessionId.value}/complete`, {
      method: 'PATCH',
      headers: getHeaders()
    })
    const json = await res.json()
    if (checkUnauthorized(res)) return
    if (json.code === 200) {
      ElMessage.success('面试已顺利完成！')
      router.push('/')
    } else {
      ElMessage.error(json.msg || '结束面试失败')
    }
  } catch (err) {
    // 用户取消
  } finally {
    isEnding.value = false
  }
}

// --- 辅助方法 ---
const scrollToBottom = () => {
  nextTick(() => {
    if (chatBody.value) {
      chatBody.value.scrollTop = chatBody.value.scrollHeight
    }
  })
}

const formatTime = (timeStr: string) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}
</script>

<template>
  <div class="interview-page" :class="{ 'is-dark': isDark }">
    <!-- 头部区域 -->
    <header class="interview-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" circle @click="router.push('/')" />
        <h3 v-if="sessionId">正在进行模拟面试</h3>
        <h3 v-else>选择面试岗位</h3>
      </div>
      <div class="header-right" v-if="sessionId">
        <el-button type="danger" plain @click="handleComplete" :loading="isEnding">结束面试</el-button>
      </div>
    </header>

    <!-- 主体区域 -->
    <main class="interview-main">
      <!-- 岗位选择列表 (未开始时显示) -->
      <div v-if="!sessionId" class="selection-container">
        <el-empty description="请选择一个岗位开始面试" v-if="positions.length === 0" />
        <div class="position-grid">
          <el-card 
            v-for="p in positions" 
            :key="p.id" 
            class="position-card" 
            shadow="hover"
            @click="startInterview(p.id)"
          >
            <div class="pos-info">
              <h4>{{ p.name }}</h4>
              <p>{{ p.description || '暂无详细描述' }}</p>
            </div>
            <div class="pos-action">
              <el-button type="primary" link>开始面试</el-button>
            </div>
          </el-card>
        </div>
      </div>

      <!-- 聊天对话窗口 (面试开始后显示) -->
      <div v-else class="chat-container">
        <div class="chat-body" ref="chatBody">
          <div 
            v-for="(turn, index) in turns" 
            :key="index" 
            :class="['message-row', turn.role === 'INTERVIEWER' ? 'left' : 'right']"
          >
            <el-avatar 
              :size="40" 
              class="avatar"
              :src="turn.role === 'INTERVIEWER' ? '/interviewer-avatar.png' : ''"
            >
              {{ turn.role === 'INTERVIEWER' ? 'AI' : '我' }}
            </el-avatar>
            <div class="message-content">
              <div class="message-bubble">
                <p style="white-space: pre-wrap;">{{ turn.content }}</p>
              </div>
              <span class="message-time">{{ formatTime(turn.createTime) }}</span>
            </div>
          </div>
          
          <!-- 加载状态提示 -->
          <div v-if="isLoading" class="message-row left">
            <el-avatar :size="40" class="avatar">AI</el-avatar>
            <div class="message-content">
              <div class="message-bubble typing">
                <span>.</span><span>.</span><span>.</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部输入框 -->
        <footer class="chat-footer">
          <div class="input-wrapper">
            <el-input
              v-model="userInput"
              type="textarea"
              :rows="2"
              placeholder="输入你的回答... (Ctrl+Enter 发送)"
              resize="none"
              :disabled="isLoading"
              @keydown.ctrl.enter="sendMessage"
            />
            <el-button 
              type="primary" 
              :icon="Promotion" 
              class="send-btn"
              :loading="isLoading"
              @click="sendMessage"
            >
              发送
            </el-button>
          </div>
          <div class="footer-tip">
             AI 面试官会根据你的回答进行追问或切换题目，请认真作答。
          </div>
        </footer>
      </div>
    </main>
  </div>
</template>

<style scoped>
.interview-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
  color: #303133;
}

.is-dark.interview-page {
  background-color: #121212;
  color: #e5eaf3;
}

.interview-header {
  height: 60px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
  z-index: 10;
}

.is-dark .interview-header {
  background: #1e1e1e;
  border-bottom: 1px solid #333;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.header-left h3 {
  margin: 0;
  font-size: 1.1rem;
}

.interview-main {
  flex: 1;
  overflow: hidden;
  display: flex;
  justify-content: center;
  padding: 20px;
}

/* 岗位选择卡片列表 */
.selection-container {
  width: 100%;
  max-width: 900px;
}

.position-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.position-card {
  cursor: pointer;
  transition: transform 0.2s;
  border: none;
  border-radius: 12px;
}

.position-card:hover {
  transform: translateY(-5px);
}

.pos-info h4 {
  margin: 0 0 10px 0;
  color: #10b981;
}

.pos-info p {
  font-size: 0.9rem;
  color: #606266;
  line-height: 1.5;
}

.is-dark .pos-info p {
  color: #9ca3af;
}

/* 聊天窗口布局 */
.chat-container {
  width: 100%;
  max-width: 1000px;
  height: 100%;
  background: #fff;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
  overflow: hidden;
}

.is-dark .chat-container {
  background: #1e1e1e;
}

.chat-body {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  scroll-behavior: smooth;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message-row {
  display: flex;
  gap: 12px;
  max-width: 85%;
}

.message-row.left {
  align-self: flex-start;
}

.message-row.right {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.avatar {
  background-color: #10b981;
  flex-shrink: 0;
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-row.right .message-content {
  align-items: flex-end;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 0.95rem;
  line-height: 1.6;
  word-break: break-word;
}

.left .message-bubble {
  background-color: #f4f4f5;
  border-bottom-left-radius: 2px;
}

.right .message-bubble {
  background-color: #10b981;
  color: #fff;
  border-bottom-right-radius: 2px;
}

.is-dark .left .message-bubble {
  background-color: #2c2c2c;
  color: #e5eaf3;
}

.message-time {
  font-size: 0.75rem;
  color: #909399;
}

/* 打字动画效果 */
.typing span {
  animation: blink 1.4s infinite both;
  font-size: 24px;
  line-height: 0;
  display: inline-block;
}

.typing span:nth-child(2) { animation-delay: .2s; }
.typing span:nth-child(3) { animation-delay: .4s; }

@keyframes blink {
  0% { opacity: .2; }
  20% { opacity: 1; }
  100% { opacity: .2; }
}

/* 底部输入框 */
.chat-footer {
  padding: 20px;
  border-top: 1px solid #ebeef5;
}

.is-dark .chat-footer {
  border-top: 1px solid #333;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.send-btn {
  height: 52px;
  width: 90px;
}

.footer-tip {
  margin-top: 8px;
  font-size: 0.75rem;
  color: #909399;
  text-align: center;
}
</style>
