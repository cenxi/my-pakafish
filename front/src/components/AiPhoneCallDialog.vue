<template>
  <el-dialog
    v-model="visible"
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    width="480px"
    class="ai-phone-call-dialog"
    destroy-on-close
    append-to-body
  >
    <div class="call-container">
      <!-- 顶部通话状态栏 -->
      <div class="call-top-bar">
        <div class="call-status">
          <span class="status-dot" :class="{ connected: isConnected, speaking: isAiSpeaking }"></span>
          <span class="status-text">{{ statusText }}</span>
        </div>
        <div class="call-duration" v-if="isConnected">{{ formattedDuration }}</div>
      </div>

      <!-- 中心大师头像与动态声波圈 -->
      <div class="avatar-area">
        <div class="pulse-ring ring-1" :class="{ active: isAiSpeaking || isUserSpeaking }"></div>
        <div class="pulse-ring ring-2" :class="{ active: isAiSpeaking || isUserSpeaking }"></div>
        <div class="avatar-wrapper" :class="{ 'ai-speaking': isAiSpeaking, 'user-speaking': isUserSpeaking }">
          <el-avatar :size="100" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
        </div>
        <h3 class="coach-title">象棋特级大师 · 语音私教</h3>
        <p class="coach-subtitle">实时对弈连线 · 边下棋边交流</p>
      </div>

      <!-- 实时识别与 AI 回复动态文字流 -->
      <div class="subtitles-area">
        <div v-if="userLiveText" class="user-live-text">
          <span class="bubble-tag">你在说：</span>
          <span class="text-val">{{ userLiveText }}</span>
        </div>
        <div v-if="aiLiveText" class="ai-live-text">
          <span class="bubble-tag">大师说：</span>
          <span class="text-val">{{ aiLiveText }}</span>
        </div>
        <div v-if="!userLiveText && !aiLiveText" class="hint-text">
          <span>请直接对着麦克风说话，大师正在专注倾听...</span>
        </div>
      </div>

      <!-- 底部控制按键 -->
      <div class="call-actions">
        <!-- 静音/开启麦克风 -->
        <el-tooltip :content="isMuted ? '取消静音' : '静音麦克风'" placement="top">
          <button class="action-btn mute-btn" :class="{ active: isMuted }" @click="toggleMute">
            <el-icon :size="22"><Mute v-if="isMuted" /><Microphone v-else /></el-icon>
            <span>{{ isMuted ? '麦克风关' : '静音' }}</span>
          </button>
        </el-tooltip>

        <!-- 挂断电话 -->
        <el-tooltip content="挂断通话" placement="top">
          <button class="action-btn hangup-btn" @click="handleHangup">
            <el-icon :size="28"><PhoneFilled /></el-icon>
            <span>挂断</span>
          </button>
        </el-tooltip>

        <!-- 打断大师说话 -->
        <el-tooltip content="打断大师发音" placement="top">
          <button class="action-btn interrupt-btn" :disabled="!isAiSpeaking" @click="handleInterrupt">
            <el-icon :size="22"><VideoPause /></el-icon>
            <span>打断</span>
          </button>
        </el-tooltip>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onUnmounted, watch } from 'vue'
import { Microphone, Mute, PhoneFilled, VideoPause } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  currentFen: { type: String, default: '' },
  historyMoves: { type: Array, default: () => [] },
  selectedVoice: { type: String, default: 'zh-CN-XiaoxiaoNeural' },
  selectedRate: { type: String, default: '+0%' }
})

const emit = defineEmits(['close', 'call-summary'])

const visible = ref(false)
const isConnected = ref(false)
const isAiSpeaking = ref(false)
const isUserSpeaking = ref(false)
const isMuted = ref(false)
const statusText = ref('正在呼叫特级大师...')

const userLiveText = ref('')
const aiLiveText = ref('')

// 通话计时
const callSeconds = ref(0)
let timer = null

const formattedDuration = computed(() => {
  const m = Math.floor(callSeconds.value / 60).toString().padStart(2, '0')
  const s = (callSeconds.value % 60).toString().padStart(2, '0')
  return `${m}:${s}`
})

// WebSocket 与音频处理
let ws = null
let audioContext = null
let mediaStream = null
let scriptProcessor = null
let currentAudioElement = null
// 乱序缓冲：存储收到的音频片段 Map<seq, base64Audio>
const audioBufferMap = new Map()
let expectedPlaySeq = 1
let isPlayingQueue = false

function openCall() {
  visible.value = true
  statusText.value = '正在连线特级大师...'
  userLiveText.value = ''
  aiLiveText.value = ''
  callSeconds.value = 0
  audioBufferMap.clear()
  expectedPlaySeq = 1
  initWebSocket()
}

function initWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.hostname || 'localhost'
  const wsUrl = `${protocol}//${host}:8080/ws/ai-call`
  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    isConnected.value = true
    statusText.value = '通话中 · 大师倾听中'
    startDurationTimer()
    sendBoardUpdate()
    startMicrophone()
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      handleWsMessage(data)
    } catch (e) {
      console.error('WS 消息解析失败:', e)
    }
  }

  ws.onerror = () => {
    ElMessage.error('连接大师语音线路失败，请确认后端已启动')
    statusText.value = '连接异常'
  }

  ws.onclose = () => {
    isConnected.value = false
    statusText.value = '通话已结束'
    cleanupCall()
  }
}

function handleWsMessage(data) {
  switch (data.type) {
    case 'asr_partial':
      userLiveText.value = data.text
      isUserSpeaking.value = true
      break
    case 'asr_final':
      userLiveText.value = data.text
      isUserSpeaking.value = false
      statusText.value = '大师正在思考回复...'
      break
    case 'ai_thinking':
      statusText.value = '大师正在斟酌棋理...'
      aiLiveText.value = ''
      expectedPlaySeq = 1
      audioBufferMap.clear()
      break
    case 'ai_text_chunk':
      aiLiveText.value += data.chunk
      break
    case 'ai_audio_clip':
      enqueueAudioWithSeq(data.audioBase64, data.seq || 1)
      break
    case 'ai_reply_finished':
      statusText.value = '通话中 · 大师倾听中'
      break
    case 'interrupted':
      stopCurrentAudio()
      break
  }
}

// 音频播放队列（按序号严格保序播放，防止多并发切句时乱序）
function enqueueAudioWithSeq(base64Audio, seq) {
  audioBufferMap.set(seq, base64Audio)
  if (!isPlayingQueue) {
    playNextSeqAudio()
  }
}

function playNextSeqAudio() {
  if (!audioBufferMap.has(expectedPlaySeq)) {
    isPlayingQueue = false
    // 检查是否还有更大的序号没播放完
    if (audioBufferMap.size === 0) {
      isAiSpeaking.value = false
    }
    return
  }

  isPlayingQueue = true
  isAiSpeaking.value = true
  const base64 = audioBufferMap.get(expectedPlaySeq)
  audioBufferMap.delete(expectedPlaySeq)
  expectedPlaySeq++

  const audioBlob = b64toBlob(base64, 'audio/mpeg')
  const blobUrl = URL.createObjectURL(audioBlob)

  currentAudioElement = new Audio(blobUrl)
  currentAudioElement.onended = () => {
    URL.revokeObjectURL(blobUrl)
    currentAudioElement = null
    playNextSeqAudio()
  }
  currentAudioElement.onerror = () => {
    URL.revokeObjectURL(blobUrl)
    currentAudioElement = null
    playNextSeqAudio()
  }
  currentAudioElement.play().catch(e => {
    console.warn('播放被阻止:', e)
    playNextSeqAudio()
  })
}

function stopCurrentAudio() {
  audioBufferMap.clear()
  if (currentAudioElement) {
    currentAudioElement.pause()
    currentAudioElement = null
  }
  isPlayingQueue = false
  isAiSpeaking.value = false
}

function b64toBlob(b64Data, contentType = 'audio/mpeg') {
  const byteCharacters = atob(b64Data)
  const byteNumbers = new Array(byteCharacters.length)
  for (let i = 0; i < byteCharacters.length; i++) {
    byteNumbers[i] = byteCharacters.charCodeAt(i)
  }
  const byteArray = new Uint8Array(byteNumbers)
  return new Blob([byteArray], { type: contentType })
}

// 录音并转为 16kHz PCM 单声道二进制推流
async function startMicrophone() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    ElMessage.warning('当前环境不支持麦克风调用（浏览器要求在 localhost 或 HTTPS 环境下使用），已切换至单向收听模式')
    return
  }
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        sampleRate: 16000,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }
    })

    audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 })
    const source = audioContext.createMediaStreamSource(mediaStream)
    // 缓冲区 2048 点
    scriptProcessor = audioContext.createScriptProcessor(2048, 1, 1)

    scriptProcessor.onaudioprocess = (e) => {
      if (isMuted.value || !ws || ws.readyState !== WebSocket.OPEN) return
      const inputData = e.inputBuffer.getChannelData(0)
      
      // 检测音量大小驱动界面动效
      let sum = 0
      for (let i = 0; i < inputData.length; i++) {
        sum += inputData[i] * inputData[i]
      }
      const rms = Math.sqrt(sum / inputData.length)
      if (rms > 0.03) {
        isUserSpeaking.value = true
      }

      // 转换为 16位 PCM (Little Endian)
      const pcm16 = new Int16Array(inputData.length)
      for (let i = 0; i < inputData.length; i++) {
        let s = Math.max(-1, Math.min(1, inputData[i]))
        pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7fff
      }
      ws.send(pcm16.buffer)
    }

    source.connect(scriptProcessor)
    scriptProcessor.connect(audioContext.destination)
  } catch (err) {
    ElMessage.warning('未能获取麦克风权限，将进入单向听课模式: ' + err.message)
  }
}

function sendBoardUpdate() {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({
      type: 'update_board',
      fen: props.currentFen,
      historyMoves: (props.historyMoves || []).map(m => m.chinese || m.uci).join(' '),
      voice: props.selectedVoice,
      rate: props.selectedRate
    }))
  }
}

// 盘面变动实时同步给电话后端
watch(() => props.currentFen, () => {
  sendBoardUpdate()
})

function toggleMute() {
  isMuted.value = !isMuted.value
}

function handleInterrupt() {
  stopCurrentAudio()
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: 'interrupt' }))
  }
}

function handleHangup() {
  visible.value = false
  cleanupCall()
  emit('close')
}

function startDurationTimer() {
  clearInterval(timer)
  timer = setInterval(() => {
    callSeconds.value++
  }, 1000)
}

function cleanupCall() {
  clearInterval(timer)
  stopCurrentAudio()
  if (scriptProcessor) {
    scriptProcessor.disconnect()
    scriptProcessor = null
  }
  if (audioContext) {
    audioContext.close().catch(() => {})
    audioContext = null
  }
  if (mediaStream) {
    mediaStream.getTracks().forEach(track => track.stop())
    mediaStream = null
  }
  if (ws) {
    ws.close()
    ws = null
  }
}

onUnmounted(() => {
  cleanupCall()
})

defineExpose({
  openCall
})
</script>

<style lang="scss" scoped>
:deep(.ai-phone-call-dialog) {
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 20px 48px rgba(0, 0, 0, 0.4);
  background: #181b22;
  color: #ffffff;

  .el-dialog__header {
    display: none;
  }
  .el-dialog__body {
    padding: 0;
  }
}

.call-container {
  padding: 36px 24px 28px;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: radial-gradient(circle at center top, #2b3345 0%, #15171e 100%);
  position: relative;
  overflow: hidden;
}

.call-top-bar {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  font-size: 13px;

  .call-status {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #a0aec0;

    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #718096;
      transition: all 0.3s;

      &.connected {
        background: #10b981;
        box-shadow: 0 0 8px #10b981;
      }
      &.speaking {
        background: #3b82f6;
        box-shadow: 0 0 10px #3b82f6;
        animation: pulse-dot 1.2s infinite;
      }
    }
  }

  .call-duration {
    color: #e2e8f0;
    font-family: monospace;
    font-size: 14px;
  }
}

.avatar-area {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 20px;

  .pulse-ring {
    position: absolute;
    top: 0;
    left: 50%;
    transform: translateX(-50%);
    width: 100px;
    height: 100px;
    border-radius: 50%;
    border: 2px solid rgba(59, 130, 246, 0.4);
    opacity: 0;
    pointer-events: none;

    &.active {
      animation: ripple 2s infinite ease-out;
    }
  }

  .ring-2 {
    animation-delay: 0.8s;
  }

  .avatar-wrapper {
    position: relative;
    z-index: 2;
    border-radius: 50%;
    padding: 4px;
    border: 2px solid transparent;
    transition: all 0.3s;

    &.ai-speaking {
      border-color: #3b82f6;
      box-shadow: 0 0 24px rgba(59, 130, 246, 0.6);
      transform: scale(1.05);
    }
    &.user-speaking {
      border-color: #10b981;
      box-shadow: 0 0 24px rgba(16, 185, 129, 0.6);
    }
  }

  .coach-title {
    margin: 16px 0 4px;
    font-size: 18px;
    font-weight: 600;
    color: #f8fafc;
  }

  .coach-subtitle {
    font-size: 12px;
    color: #94a3b8;
    margin: 0;
  }
}

.subtitles-area {
  width: 100%;
  min-height: 90px;
  max-height: 120px;
  overflow-y: auto;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 12px;
  padding: 12px 16px;
  box-sizing: border-box;
  margin-bottom: 28px;
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 8px;

  .user-live-text, .ai-live-text {
    line-height: 1.5;
    .bubble-tag {
      color: #94a3b8;
      margin-right: 4px;
    }
  }
  .user-live-text .text-val {
    color: #34d399;
  }
  .ai-live-text .text-val {
    color: #60a5fa;
  }
  .hint-text {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: #64748b;
    font-size: 12px;
  }
}

.call-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 28px;
  width: 100%;

  .action-btn {
    border: none;
    outline: none;
    cursor: pointer;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    background: transparent;
    color: #cbd5e1;
    font-size: 12px;
    transition: all 0.2s;

    .el-icon {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      background: #334155;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
    }

    &:hover .el-icon {
      transform: scale(1.08);
      background: #475569;
    }

    &.mute-btn.active .el-icon {
      background: #ef4444;
      color: #ffffff;
    }

    &.hangup-btn {
      .el-icon {
        width: 60px;
        height: 60px;
        background: #dc2626;
        color: #ffffff;
      }
      &:hover .el-icon {
        background: #b91c1c;
      }
    }

    &.interrupt-btn:disabled {
      opacity: 0.4;
      cursor: not-allowed;
      &:hover .el-icon {
        transform: none;
        background: #334155;
      }
    }
  }
}

@keyframes ripple {
  0% {
    width: 100px;
    height: 100px;
    opacity: 0.8;
  }
  100% {
    width: 180px;
    height: 180px;
    opacity: 0;
  }
}

@keyframes pulse-dot {
  0%, 100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.3);
    opacity: 0.5;
  }
}
</style>
