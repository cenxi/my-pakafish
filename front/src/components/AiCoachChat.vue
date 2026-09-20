<template>
  <div class="ai-coach-panel" :class="{ fullscreen: isExpanded }">
    <!-- 头部信息 -->
    <div class="panel-header">
      <div class="coach-profile">
        <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
        <div class="profile-info">
          <div class="name-line">
            <span class="coach-name">AI 象棋特级大师</span>
            <el-tag size="small" type="warning" effect="dark" round>实时深度棋理</el-tag>
          </div>
          <span class="coach-tag">结合皮卡鱼算力 · 战略破局指导</span>
        </div>
      </div>
      <div class="header-actions">
        <el-tooltip :content="isExpanded ? '还原窗口' : '全屏放大对话'" placement="top">
          <el-button
            size="small"
            :type="isExpanded ? 'primary' : 'default'"
            :icon="isExpanded ? Close : FullScreen"
            circle
            @click="isExpanded = !isExpanded"
          />
        </el-tooltip>
        <el-tooltip content="清空对话" placement="top">
          <el-button size="small" :icon="Delete" circle @click="handleClearChat" />
        </el-tooltip>
      </div>
    </div>

    <!-- 消息对话区域 -->
    <div ref="chatBodyRef" class="chat-body">
      <!-- 欢迎语与当前局面概况 -->
      <div class="welcome-card">
        <div class="card-title">
          <el-icon><InfoFilled /></el-icon> 执局辅导大师
        </div>
        <p>
          棋手你好！我是你的 AI 象棋导师。我会结合<strong>皮卡鱼引擎客观计算（胜率分、变着深度）</strong>为你剖析核心战局，解释每一步背后的棋理与潜在反击。
        </p>
      </div>

      <!-- 消息列表 -->
      <div v-for="(msg, idx) in messageList" :key="idx" class="message-row" :class="msg.role">
        <div class="message-content">
          <div v-if="msg.role === 'assistant'" class="role-badge">
            <span>特级大师 · 棋理精解</span>
          </div>
          <!-- Markdown 渲染气泡 -->
          <div class="markdown-body text-bubble" v-html="renderMarkdown(msg.text)"></div>
        </div>
      </div>

      <!-- 思考加载中打字状态 -->
      <div v-if="isStreaming" class="streaming-indicator">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>大师正在结合皮卡鱼算力深度构思中...</span>
      </div>
    </div>

    <!-- 快捷提问胶囊 -->
    <div class="quick-questions">
      <span class="quick-label">大师速问：</span>
      <el-tag
        v-for="(item, i) in quickPrompts"
        :key="i"
        class="prompt-tag"
        effect="plain"
        round
        @click="askQuestion(item)"
      >
        {{ item }}
      </el-tag>
    </div>

    <!-- 底部输入框 -->
    <div class="chat-footer">
      <el-input
        v-model="inputQuery"
        placeholder="向大师提问（如：红方能压马吗？对方刚才走这步用意何在？）"
        :disabled="isStreaming"
        @keyup.enter="handleSend"
      >
        <template #append>
          <el-button type="primary" :loading="isStreaming" @click="handleSend">
            发送
          </el-button>
        </template>
      </el-input>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { InfoFilled, Delete, Loading, FullScreen, Close } from '@element-plus/icons-vue'
import { marked } from 'marked'

// 配置 marked 安全基础设置
marked.setOptions({
  gfm: true,
  breaks: true
})

const props = defineProps({
  currentFen: { type: String, required: true },
  latestMoveChinese: { type: String, default: '' },
  engineAnalysis: { type: Object, default: null }
})

const inputQuery = ref('')
const isStreaming = ref(false)
const isExpanded = ref(false)
const chatBodyRef = ref(null)

const messageList = ref([
  {
    role: 'assistant',
    text: '盘面已就绪。你可以随意走子，或点击下方【大师速问】让我为你深度复盘当前局势。'
  }
])

const quickPrompts = [
  '分析当前局势焦点',
  '讲解最佳着法的战术意图',
  '对方下一步可能有什么威胁？',
  '我方应主攻哪一路？'
]

function renderMarkdown(content) {
  if (!content) return ''
  try {
    return marked.parse(content)
  } catch (e) {
    return content
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
}

function handleClearChat() {
  messageList.value = []
}

function askQuestion(q) {
  inputQuery.value = q
  handleSend()
}

function handleSend() {
  const query = inputQuery.value.trim()
  if (!query || isStreaming.value) return

  // 添加用户提问
  messageList.value.push({
    role: 'user',
    text: query
  })
  inputQuery.value = ''
  scrollToBottom()

  // 发起流式对话
  startStreamChat(query)
}

// 供外部主动触发
function requestAnalysis(customPrompt) {
  if (isStreaming.value) return
  startStreamChat(customPrompt || '请全面剖析当前盘面走势与最佳应对。')
}

defineExpose({
  requestAnalysis,
  isExpanded
})

function startStreamChat(question) {
  isStreaming.value = true
  const assistantMsgIndex = messageList.value.length
  messageList.value.push({
    role: 'assistant',
    text: ''
  })

  const url = `http://localhost:8080/api/chess/chat/stream?fen=${encodeURIComponent(props.currentFen)}&question=${encodeURIComponent(question)}`
  const eventSource = new EventSource(url)

  eventSource.onmessage = (event) => {
    // 还原 \n 换行符
    const chunk = event.data.replace(/\\n/g, '\n')
    messageList.value[assistantMsgIndex].text += chunk
    scrollToBottom()
  }

  eventSource.addEventListener('finish', () => {
    isStreaming.value = false
    eventSource.close()
  })

  eventSource.addEventListener('error', (e) => {
    console.warn('SSE stream error or finished', e)
    isStreaming.value = false
    eventSource.close()
  })
}
</script>

<style lang="scss" scoped>
.ai-coach-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  border: 1px solid #ebeef5;
  transition: all 0.3s ease;

  &.fullscreen {
    position: fixed;
    top: 20px;
    right: 20px;
    bottom: 20px;
    left: 20px;
    z-index: 2000;
    box-shadow: 0 12px 36px rgba(0, 0, 0, 0.25);
  }
}

.panel-header {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f2f5;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .coach-profile {
    display: flex;
    align-items: center;
    gap: 12px;

    .profile-info {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .name-line {
        display: flex;
        align-items: center;
        gap: 8px;

        .coach-name {
          font-weight: 700;
          font-size: 15px;
          color: #1f2329;
        }
      }
      .coach-tag {
        font-size: 12px;
        color: #8f959e;
      }
    }
  }

  .header-actions {
    display: flex;
    gap: 8px;
  }
}

.chat-body {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.welcome-card {
  background: #f0f7ff;
  border: 1px solid #d0e5ff;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 13.5px;
  color: #4e5969;

  .card-title {
    font-weight: bold;
    color: #165dff;
    margin-bottom: 6px;
    display: flex;
    align-items: center;
    gap: 6px;
  }
  p {
    margin: 0;
    line-height: 1.6;
  }
}

.message-row {
  display: flex;
  flex-direction: column;

  &.user {
    align-items: flex-end;
    .text-bubble {
      background: #165dff;
      color: #ffffff;
      border-radius: 12px 12px 2px 12px;
      padding: 10px 16px;
      font-size: 14px;
    }
  }

  &.assistant {
    align-items: flex-start;
    .role-badge {
      font-size: 12px;
      color: #ff7d00;
      font-weight: 600;
      margin-bottom: 4px;
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .text-bubble {
      background: #f7f8fa;
      color: #1d2129;
      border-radius: 2px 12px 12px 12px;
      border: 1px solid #e5e6eb;
      padding: 12px 18px;
      width: 100%;
      box-sizing: border-box;
    }
  }
}

// Markdown 排版样式优化
:deep(.markdown-body) {
  font-size: 14px;
  line-height: 1.7;
  color: #272e3b;

  p {
    margin: 0 0 10px 0;
    &:last-child {
      margin-bottom: 0;
    }
  }

  strong {
    color: #165dff;
    font-weight: 600;
  }

  ul, ol {
    margin: 6px 0 10px 18px;
    padding: 0;
    li {
      margin-bottom: 4px;
    }
  }

  blockquote {
    margin: 8px 0;
    padding: 6px 12px;
    background: #eef4ff;
    border-left: 4px solid #165dff;
    border-radius: 2px;
    color: #4e5969;
  }

  code {
    background: #e5e6eb;
    padding: 2px 6px;
    border-radius: 4px;
    font-family: Consolas, Monaco, monospace;
    font-size: 12.5px;
    color: #d91f11;
  }

  pre code {
    display: block;
    padding: 10px;
    background: #272e3b;
    color: #f7f8fa;
    border-radius: 6px;
    overflow-x: auto;
  }

  h1, h2, h3, h4 {
    margin: 12px 0 8px 0;
    font-weight: bold;
    color: #1d2129;
  }
  h3 { font-size: 15px; }
  h4 { font-size: 14px; }
}

.streaming-indicator {
  font-size: 13px;
  color: #165dff;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
}

.quick-questions {
  padding: 8px 16px;
  background: #fbfbfb;
  border-top: 1px solid #f2f3f5;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;

  .quick-label {
    font-size: 12px;
    color: #86909c;
    font-weight: 500;
  }

  .prompt-tag {
    cursor: pointer;
    border-color: #e5e6eb;
    color: #4e5969;
    transition: all 0.2s;
    &:hover {
      color: #165dff;
      border-color: #165dff;
      background-color: #e8f3ff;
    }
  }
}

.chat-footer {
  padding: 12px 16px;
  border-top: 1px solid #f2f3f5;
  background: #ffffff;
}
</style>
