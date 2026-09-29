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
        <!-- 实时电话连线按钮 -->
        <el-tooltip content="连线特级大师 (打电话模式)" placement="top">
          <el-button
            size="small"
            type="success"
            circle
            @click="openPhoneCall"
          >
            <el-icon><PhoneFilled /></el-icon>
          </el-button>
        </el-tooltip>

        <!-- 语音设置 Popover -->
        <el-popover placement="bottom-end" :width="280" trigger="click">
          <template #reference>
            <el-button size="small" :icon="Headset" circle />
          </template>
          <div class="voice-config-popover">
            <h4 class="popover-title">大师语音设置</h4>
            <div class="config-item">
              <span class="label">自动朗读回复：</span>
              <el-switch v-model="autoSpeakEnabled" size="small" />
            </div>
            <div class="config-item">
              <span class="label">大师音色：</span>
              <el-select v-model="selectedVoice" size="small" style="width: 150px">
                <el-option
                  v-for="v in voiceOptions"
                  :key="v.code"
                  :label="v.name"
                  :value="v.code"
                />
              </el-select>
            </div>
            <div class="config-item">
              <span class="label">朗读取速：</span>
              <el-select v-model="selectedRate" size="small" style="width: 150px">
                <el-option label="较慢 (-20%)" value="-20%" />
                <el-option label="正常 (标准)" value="+0%" />
                <el-option label="微快 (+15%)" value="+15%" />
                <el-option label="快速 (+30%)" value="+30%" />
              </el-select>
            </div>
            <div class="popover-actions">
              <el-button size="small" type="primary" plain @click="testCurrentVoice">
                试听大师音色
              </el-button>
            </div>
          </div>
        </el-popover>

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
      <!-- 消息列表 -->
      <div v-for="(msg, idx) in messageList" :key="idx" class="message-row" :class="msg.role">
        <div class="message-content">
          <div v-if="msg.role === 'assistant'" class="role-badge">
            <span>特级大师 · 棋理精解</span>
          </div>
          <!-- Markdown 渲染气泡 -->
          <div class="markdown-body text-bubble" @click="handleBubbleClick" v-html="renderMarkdown(msg.text)"></div>
          <!-- 气泡底部语音控制条 (仅 assistant 角色显示) -->
          <div v-if="msg.role === 'assistant' && msg.text" class="speech-action-bar">
            <button
              class="speech-btn"
              :class="{ playing: currentPlayingIndex === idx }"
              @click.stop="togglePlayMessageVoice(msg.text, idx)"
            >
              <el-icon :size="14">
                <VideoPause v-if="currentPlayingIndex === idx" />
                <Headset v-else />
              </el-icon>
              <span>{{ currentPlayingIndex === idx ? '正在朗读 (点击停止)' : '朗读本段' }}</span>
            </button>
          </div>
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
        :placeholder="inputPlaceholder"
        :disabled="isStreaming && !isRecording"
        @keyup.enter="handleSend"
      >
        <template #prepend>
          <el-tooltip :content="isRecording ? '点击结束语音录入' : '语音输入 (FunASR)'" placement="top">
            <el-button
              :type="isRecording ? 'danger' : 'default'"
              :class="{ 'recording-active': isRecording, 'recognizing-active': isRecognizing }"
              :loading="isRecognizing"
              @click="toggleSpeechInput"
            >
              <el-icon v-if="!isRecognizing"><Microphone /></el-icon>
            </el-button>
          </el-tooltip>
        </template>
        <template #append>
          <el-button type="primary" :loading="isStreaming" @click="handleSend">
            发送
          </el-button>
        </template>
      </el-input>
    </div>

    <!-- 实时语音电话弹窗 -->
    <AiPhoneCallDialog
      ref="phoneCallDialogRef"
      :current-fen="currentFen"
      :history-moves="historyMoves"
      :selected-voice="selectedVoice"
      :selected-rate="selectedRate"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import {
  InfoFilled,
  Delete,
  Loading,
  FullScreen,
  Close,
  Headset,
  PhoneFilled,
  Microphone,
  VideoPause
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import axios from 'axios'
import AiPhoneCallDialog from './AiPhoneCallDialog.vue'

// 配置 marked 安全基础设置
marked.setOptions({
  gfm: true,
  breaks: true
})

const props = defineProps({
  currentFen: { type: String, required: true },
  latestMoveChinese: { type: String, default: '' },
  historyText: { type: String, default: '' },
  engineAnalysis: { type: Object, default: null },
  historyMoves: { type: Array, default: () => [] }
})

const emit = defineEmits(['jump-step', 'play-variation'])

const inputQuery = ref('')
const isStreaming = ref(false)
const isExpanded = ref(false)
const chatBodyRef = ref(null)

const inputPlaceholder = computed(() => {
  if (isRecording.value) return '正在倾听中... 请清晰说出你的问题，说完可再次点击麦克风'
  if (isRecognizing.value) return '正在进行语音转写识别，请稍候...'
  return '向大师提问（如：红方能压马吗？对方刚才走这步用意何在？）'
})

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
    let text = content
      // 1. 修复大模型生成的标题缺少空格问题 (如 `###二、` -> `### 二、`)，防止 marked 无法识别为标题
      .replace(/^(#{1,6})([^\s#\n])/gm, '$1 $2')
      // 2. 将大模型常输出的 LaTeX 箭头语法 `$\to$` 或 `$\rightarrow$` 转为直观的中文箭头 `→`
      .replace(/\$\\(to|rightarrow|longrightarrow)\$/g, '→')
      .replace(/\\(to|rightarrow|longrightarrow)\b/g, '→')
      // 3. 修复各种中文引号与加粗语法粘连缺陷
      // 3.1 完整闭合的 **“xxx”** 或 “**xxx**”
      .replace(/\*\*“([^”\n]+)”\*\*/g, '<strong>“$1”</strong>')
      .replace(/“\*\*([^”\n]+)\*\*”/g, '<strong>“$1”</strong>')
      // 3.2 开头带 ** 但中间包含引号，冒号后闭合 (如 `**切忌“单兵深入，后防空虚”：七路马` 或漏掉右侧 **)
      .replace(/\*\*([^：:\n*]+[：:])([^*]+)\*\*/g, '<strong>$1</strong>$2')
      // 3.3 引号与星号错位，例如 `**切忌“xxx”**` 或 `**“xxx”`
      .replace(/(?<!\*)“([^”\n]+)”\*\*/g, '<strong>“$1”</strong>')
      .replace(/\*\*“([^”\n]+)”(?!\*)/g, '<strong>“$1”</strong>')

    // 4. 将带有推演箭头的长分支标记为可点击推演按钮 (例如：“▶ 车六退二 → 车6进8...” 或 “车六退二 → 车6进8...”)
    text = text.replace(/(?:“)?(?:[▶►]\s*)?([车馬马炮砲兵卒相象士仕帥帅將将][一二三四五六七八九123456789][进退平][一二三四五六七八九123456789](?:\s*→\s*[车馬马炮砲兵卒相象士仕帥帅將将][一二三四五六七八九123456789][进退平][一二三四五六七八九123456789])+)(?:”)?/g,
      '<button type="button" class="variation-replay-btn" data-variation="$1" title="点击在棋盘上动态演示并标注此演进路线"><span class="replay-icon">▶</span><span class="replay-text">$1</span><span class="replay-badge">演进推演</span></button>'
    )

    // 5. 将具体的“第 X 回合/第 X 步”历史棋步转化为可跳转链接
    text = text.replace(/(第\s*(\d+)\s*(?:回合|步)(?:\s*[红黑]方走[车馬马炮砲兵卒相象士仕帥帅將将][一二三四五六七八九123456789][进退平][一二三四五六七八九123456789])?)/g,
      '<button type="button" class="step-jump-btn" data-step="$2" title="点击棋盘立即回溯至第 $2 步"><span class="btn-icon">🎯</span><span class="btn-text">$1</span><span class="btn-action">跳转</span></button>'
    )

    // 6. 交给 marked 解析主体语法
    let parsed = marked.parse(text)
    parsed = DOMPurify.sanitize(parsed)

    // 7. 清理残留孤立未闭合的 **
    // 7.1 成对 **xxx** -> <strong>xxx</strong>
    parsed = parsed.replace(/\*\*([^*\n<]+)\*\*/g, '<strong>$1</strong>')
    // 7.2 单个未闭合的 **（大模型只输出了开头的 **切忌... 却没有输后半个 **）
    parsed = parsed.replace(/\*\*([^*\n<]{1,40}?)(?=[:：，。！？\s<]|$)/g, '<strong>$1</strong>')
    // 7.3 去除多余悬空的 **
    parsed = parsed.replace(/\*\*/g, '')

    return parsed
  } catch (e) {
    return content
  }
}

function handleBubbleClick(e) {
  const target = e.target.closest('.step-jump-btn, .step-jump-link, .variation-replay-btn')
  if (!target) return

  if (target.classList.contains('step-jump-btn') || target.classList.contains('step-jump-link')) {
    const stepNum = parseInt(target.getAttribute('data-step'), 10)
    if (!isNaN(stepNum)) {
      emit('jump-step', stepNum)
    }
  } else if (target.classList.contains('variation-replay-btn')) {
    const variationStr = target.getAttribute('data-variation')
    if (variationStr) {
      emit('play-variation', variationStr)
    }
  }
}

const phoneCallDialogRef = ref(null)

// 语音配置
const autoSpeakEnabled = ref(false)
const selectedVoice = ref('zh-CN-XiaoxiaoNeural')
const selectedRate = ref('+0%')
const voiceOptions = ref([])

// 语音播放控制
const currentPlayingIndex = ref(-1)
let activeAudio = null

// 语音输入（FunASR 录入状态）
const isRecording = ref(false)
const isRecognizing = ref(false) // 等待 ASR 最终返回中
let asrWs = null
let asrAudioContext = null
let asrMediaStream = null
let asrProcessor = null
let asrSilenceTimer = null
let asrHasSpoken = false
let asrCloseTimeout = null

// 获取后端主机地址 (兼容本地与局域网、线上统一 /chess 前缀)
const getApiBaseUrl = () => {
  if (window.location.pathname.startsWith('/chess')) {
    return '/chess'
  }
  const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:'
  const host = window.location.hostname || 'localhost'
  return `${protocol}//${host}:8080`
}

const getWsBaseUrl = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.hostname || 'localhost'
  if (window.location.pathname.startsWith('/chess')) {
    const port = window.location.port ? `:${window.location.port}` : ''
    return `${protocol}//${host}${port}/chess`
  }
  return `${protocol}//${host}:8080`
}

onMounted(async () => {
  try {
    const res = await axios.get(`${getApiBaseUrl()}/api/tts/voices`)
    if (Array.isArray(res.data)) {
      voiceOptions.value = res.data
    }
  } catch (e) {
    voiceOptions.value = [
      { code: 'zh-CN-XiaoxiaoNeural', name: '晓晓 (温柔亲切女声 - 推荐)' },
      { code: 'zh-CN-YunxiNeural', name: '云希 (阳光开朗男声 - 推荐)' },
      { code: 'zh-CN-YunjianNeural', name: '云健 (稳重磁性男声 - 适合解说)' },
      { code: 'zh-CN-liaoning-XiaobeiNeural', name: '小北 (风趣东北女声)' }
    ]
  }
})

onUnmounted(() => {
  stopAudio()
  stopSpeechInput()
})

function openPhoneCall() {
  stopAudio()
  if (phoneCallDialogRef.value) {
    phoneCallDialogRef.value.openCall()
  }
}

function stopAudio() {
  if (activeAudio) {
    activeAudio.pause()
    activeAudio = null
  }
  currentPlayingIndex.value = -1
}

function playVoiceText(text, msgIdx = -1) {
  if (!text) return
  stopAudio()

  currentPlayingIndex.value = msgIdx
  const url = `${getApiBaseUrl()}/api/tts/speak?text=${encodeURIComponent(text)}&voice=${encodeURIComponent(selectedVoice.value)}&rate=${encodeURIComponent(selectedRate.value)}`
  activeAudio = new Audio(url)
  activeAudio.onended = () => {
    currentPlayingIndex.value = -1
    activeAudio = null
  }
  activeAudio.onerror = () => {
    currentPlayingIndex.value = -1
    activeAudio = null
  }
  activeAudio.play().catch(e => {
    console.warn('播放被浏览器安全策略限制:', e)
    currentPlayingIndex.value = -1
  })
}

function togglePlayMessageVoice(text, idx) {
  if (currentPlayingIndex.value === idx) {
    stopAudio()
  } else {
    playVoiceText(text, idx)
  }
}

function testCurrentVoice() {
  playVoiceText('你好！我是你的象棋特级大师私教，很高兴为你讲盘！')
}

// 语音输入 (通过 WebSocket 直连 FunASR 识别)
async function toggleSpeechInput() {
  if (isRecording.value) {
    finishRecording()
  } else if (!isRecognizing.value) {
    await startSpeechInput()
  }
}

async function startSpeechInput() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    ElMessage.warning('当前环境不支持麦克风调用（浏览器要求在 localhost 或 HTTPS 环境下使用）')
    return
  }
  try {
    asrMediaStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        sampleRate: 16000,
        echoCancellation: true,
        noiseSuppression: true
      }
    })

    const wsUrl = `${getWsBaseUrl()}/ws/ai-call`
    asrWs = new WebSocket(wsUrl)
    asrHasSpoken = false

    asrWs.onopen = () => {
      isRecording.value = true
      isRecognizing.value = false
      ElMessage.success('正在录音，请清晰说出你的问题...')
    }

    asrWs.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'asr_partial' || data.type === 'asr_final') {
          if (data.text && data.text.trim()) {
            inputQuery.value = data.text
          }
          if (data.type === 'asr_final') {
            isRecognizing.value = false
            closeAsrWs()
          }
        }
      } catch (e) {}
    }

    asrWs.onerror = () => {
      ElMessage.warning('未能连接语音识别服务（若未启动 FunASR Docker 请先启动）')
      stopSpeechInput()
    }

    asrAudioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 })
    const source = asrAudioContext.createMediaStreamSource(asrMediaStream)
    asrProcessor = asrAudioContext.createScriptProcessor(2048, 1, 1)

    asrProcessor.onaudioprocess = (e) => {
      if (!asrWs || asrWs.readyState !== WebSocket.OPEN) return
      const input = e.inputBuffer.getChannelData(0)

      // 计算音量能量判断是否说话与停顿
      let sum = 0
      for (let i = 0; i < input.length; i++) {
        sum += input[i] * input[i]
      }
      const rms = Math.sqrt(sum / input.length)

      if (rms > 0.015) {
        asrHasSpoken = true
        if (asrSilenceTimer) {
          clearTimeout(asrSilenceTimer)
          asrSilenceTimer = null
        }
      } else if (asrHasSpoken && !asrSilenceTimer) {
        // 用户说话后停顿 1.2 秒，自动结束录音并转写填入输入框
        asrSilenceTimer = setTimeout(() => {
          if (isRecording.value) {
            finishRecording()
          }
        }, 1200)
      }

      const pcm16 = new Int16Array(input.length)
      for (let i = 0; i < input.length; i++) {
        let s = Math.max(-1, Math.min(1, input[i]))
        pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7fff
      }
      asrWs.send(pcm16.buffer)
    }

    source.connect(asrProcessor)
    asrProcessor.connect(asrAudioContext.destination)

  } catch (err) {
    ElMessage.error('无法启用麦克风: ' + err.message)
    stopSpeechInput()
  }
}

// 正常结束录制：停麦克风推流，发 end_audio，保持 WS 等待最终文字返回
function finishRecording() {
  isRecording.value = false
  isRecognizing.value = true

  if (asrSilenceTimer) {
    clearTimeout(asrSilenceTimer)
    asrSilenceTimer = null
  }

  // 释放麦克风硬件采集资源
  if (asrProcessor) {
    asrProcessor.disconnect()
    asrProcessor = null
  }
  if (asrAudioContext) {
    asrAudioContext.close().catch(() => {})
    asrAudioContext = null
  }
  if (asrMediaStream) {
    asrMediaStream.getTracks().forEach(t => t.stop())
    asrMediaStream = null
  }

  // 通知服务端音频推流已结束，触发最终文字解码
  if (asrWs && asrWs.readyState === WebSocket.OPEN) {
    try {
      asrWs.send(JSON.stringify({ type: 'end_audio' }))
    } catch (e) {}

    // 设置 3 秒兜底超时关闭 WS
    if (asrCloseTimeout) clearTimeout(asrCloseTimeout)
    asrCloseTimeout = setTimeout(() => {
      isRecognizing.value = false
      closeAsrWs()
    }, 3000)
  } else {
    isRecognizing.value = false
  }
}

function closeAsrWs() {
  if (asrCloseTimeout) {
    clearTimeout(asrCloseTimeout)
    asrCloseTimeout = null
  }
  if (asrWs) {
    try {
      asrWs.close()
    } catch (e) {}
    asrWs = null
  }
}

function stopSpeechInput() {
  isRecording.value = false
  isRecognizing.value = false
  if (asrSilenceTimer) {
    clearTimeout(asrSilenceTimer)
    asrSilenceTimer = null
  }
  if (asrProcessor) {
    asrProcessor.disconnect()
    asrProcessor = null
  }
  if (asrAudioContext) {
    asrAudioContext.close().catch(() => {})
    asrAudioContext = null
  }
  if (asrMediaStream) {
    asrMediaStream.getTracks().forEach(t => t.stop())
    asrMediaStream = null
  }
  closeAsrWs()
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

  const url = `${getApiBaseUrl()}/api/chess/chat/stream?fen=${encodeURIComponent(props.currentFen)}&history=${encodeURIComponent(props.historyText)}&question=${encodeURIComponent(question)}`
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
    // 若开启了自动朗读，大模型输出结束后自动发音
    if (autoSpeakEnabled.value && messageList.value[assistantMsgIndex]) {
      playVoiceText(messageList.value[assistantMsgIndex].text, assistantMsgIndex)
    }
  })

  const maxRetries = 3
  let retryCount = 0
  eventSource.addEventListener('error', (e) => {
    console.warn('SSE stream error or finished', e)
    isStreaming.value = false
    eventSource.close()
    if (retryCount < maxRetries) {
      retryCount++
      const delay = Math.min(5000, 1000 * Math.pow(2, retryCount))
      ElMessage.error(`SSE 连接中断，${delay}ms 后尝试重新连接（${retryCount}/${maxRetries}）`)
      setTimeout(() => {
        startStreamChat(question)
      }, delay)
    } else {
      ElMessage.error('对话中断，请稍后重试。')
    }
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
  position: relative;
  overflow: hidden;
  box-sizing: border-box;

  &.fullscreen {
    position: fixed;
    top: 20px;
    right: 20px;
    bottom: 20px;
    left: 20px;
    height: auto !important;
    max-height: calc(100vh - 40px);
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

  :deep(.step-jump-btn) {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    background: #eef5ff;
    color: #165dff;
    border: 1px solid #bedaff;
    border-radius: 6px;
    padding: 2px 8px;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    vertical-align: middle;
    margin: 2px 4px;
    outline: none;
    box-shadow: 0 1px 3px rgba(22, 93, 255, 0.1);
    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

    .btn-icon {
      font-size: 13px;
      line-height: 1;
    }

    .btn-text {
      font-weight: 600;
      color: #165dff;
    }

    .btn-action {
      font-size: 11px;
      background: #165dff;
      color: #ffffff;
      padding: 0 4px;
      border-radius: 3px;
      line-height: 1.4;
      margin-left: 2px;
      opacity: 0.85;
    }

    &:hover {
      background: #165dff;
      border-color: #165dff;
      color: #ffffff;
      transform: translateY(-1.5px);
      box-shadow: 0 3px 8px rgba(22, 93, 255, 0.35);

      .btn-text {
        color: #ffffff;
      }
      .btn-action {
        background: #ffffff;
        color: #165dff;
        opacity: 1;
      }
    }

    &:active {
      transform: translateY(0);
      box-shadow: 0 1px 2px rgba(22, 93, 255, 0.2);
    }
  }

  :deep(.step-jump-link) {
    display: inline-flex;
    align-items: center;
    background: #e8f3ff;
    color: #165dff;
    border: 1px solid #b3d8ff;
    border-radius: 4px;
    padding: 1px 6px;
    font-weight: bold;
    cursor: pointer;
    font-size: 13px;
    margin: 0 3px;
    transition: all 0.2s;

    &:hover {
      background: #165dff;
      color: #ffffff;
      transform: translateY(-1px);
      box-shadow: 0 2px 6px rgba(22, 93, 255, 0.3);
    }
  }

  :deep(.variation-replay-btn) {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background: #fdf6ec;
    color: #b8741a;
    border: 1.5px solid #f3d19e;
    border-radius: 6px;
    padding: 3px 10px;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    margin: 4px 2px;
    outline: none;
    box-shadow: 0 1.5px 4px rgba(230, 162, 60, 0.15);
    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    vertical-align: middle;

    .replay-icon {
      font-size: 12px;
      color: #e6a23c;
    }

    .replay-text {
      color: #8c5b00;
      font-weight: 600;
    }

    .replay-badge {
      font-size: 11px;
      background: #e6a23c;
      color: #ffffff;
      padding: 1px 6px;
      border-radius: 4px;
      font-weight: bold;
    }

    &:hover {
      background: #e6a23c;
      border-color: #e6a23c;
      color: #ffffff;
      transform: translateY(-1.5px);
      box-shadow: 0 4px 12px rgba(230, 162, 60, 0.35);

      .replay-icon,
      .replay-text {
        color: #ffffff;
      }
      .replay-badge {
        background: #ffffff;
        color: #e6a23c;
      }
    }

    &:active {
      transform: translateY(0);
      box-shadow: 0 1px 2px rgba(230, 162, 60, 0.2);
    }
  }
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
  padding: 10px 16px;
  border-top: 1px solid #f2f3f5;
  background: #ffffff;
  flex-shrink: 0;
  box-sizing: border-box;
  width: 100%;

  :deep(.el-input) {
    display: flex;
    width: 100%;
  }

  :deep(.el-input-group__append) {
    background-color: #409eff;
    border-color: #409eff;
    color: #ffffff;
    padding: 0 18px;

    .el-button {
      color: #ffffff;
      font-weight: 500;
    }
  }

  :deep(.el-input__wrapper) {
    padding-top: 6px;
    padding-bottom: 6px;
  }
}

.voice-config-popover {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .popover-title {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: #1f2329;
    border-bottom: 1px solid #f2f3f5;
    padding-bottom: 8px;
  }

  .config-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 13px;
    color: #4e5969;
  }

  .popover-actions {
    display: flex;
    justify-content: flex-end;
    margin-top: 4px;
  }
}

.speech-action-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;

  .speech-btn {
    border: none;
    background: transparent;
    cursor: pointer;
    font-size: 12px;
    color: #86909c;
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 3px 8px;
    border-radius: 4px;
    transition: all 0.2s;

    &:hover {
      color: #409eff;
      background: #f0f7ff;
    }

    &.playing {
      color: #e6a23c;
      background: #fdf6ec;
      font-weight: 500;
      animation: pulse-audio 1.5s infinite;
    }
  }
}

.recording-active {
  animation: pulse-mic 1.2s infinite;
  background-color: #f56c6c !important;
  color: #ffffff !important;
}

@keyframes pulse-mic {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.7);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(245, 108, 108, 0);
  }
}

@keyframes pulse-audio {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}
</style>
