<template>
  <div class="mobile-app-container">
    <!-- 1. 顶部 Header (黄底红字「楚」字国风印章 + 引擎状态) -->
    <header class="mobile-top-header">
      <div class="brand-group">
        <div class="wuhan-seal-logo">楚</div>
        <div class="brand-info">
          <span class="app-name">楚赢象棋</span>
          <span class="app-sub">AI 特级大师私教</span>
        </div>
      </div>

      <div class="header-right-tools">
        <el-tag size="small" type="success" effect="dark" class="engine-tag">
          {{ isAnalyzing ? '计算中...' : 'AI在线' }}
        </el-tag>
        <button class="settings-mini-btn" @click="isFlipped = !isFlipped" title="翻转棋盘">
          🔄
        </button>
        <button class="settings-mini-btn" @click="$router.push('/chess')" title="切换电脑版">
          💻
        </button>
        <button class="settings-mini-btn" @click="showSettingsDrawer = true" title="对局设置">
          <el-icon :size="16"><Setting /></el-icon>
        </button>
      </div>
    </header>

    <!-- 局势胜率细条 (Evaluation Bar) -->
    <div class="eval-bar-strip">
      <div class="eval-red-fill" :style="{ width: redAdvantagePct + '%' }"></div>
    </div>

    <!-- 2. 黑方席位卡片 (消除上方大黑块，棋盘始终清晰可见) -->
    <div class="opponent-seat-bar">
      <div class="seat-left">
        <div class="seat-avatar">🤖</div>
        <div class="seat-info">
          <span class="seat-title">执黑: {{ engineSide === 'b' ? 'AI特级大师' : '对手' }}</span>
          <span class="seat-desc">NNUE 算力 30层</span>
        </div>
      </div>
      <div class="seat-right">
        <div class="captured-box" title="战果">
          <span class="cap-lbl">战果:</span>
          <span class="cap-pieces">{{ blackCapturedStr || '无' }}</span>
        </div>
        <span class="turn-chip" v-if="currentTurn === 'b'">思考中...</span>
      </div>
    </div>

    <!-- 3. 拟真实木质感中国象棋盘 (移动端满宽自适应，绝不遮挡) -->
    <main class="board-container">
      <div class="board-touch-wrapper">
        <ChessBoard
          :board="boardState"
          :is-flipped="isFlipped"
          :selected-pos="selectedPiecePos"
          :legal-moves="legalMoves"
          :last-move="lastMoveHighlight"
          :suggest-move-uci="isAnalysisMode ? engineResult?.bestMove : ''"
          @cell-click="onBoardCellClick"
        />
      </div>
    </main>

    <!-- 4. 红方学员席位卡片 (消除下方黑块，显示红方状态与战果) -->
    <div class="my-seat-bar">
      <div class="seat-left">
        <div class="seat-avatar my-avatar">👤</div>
        <div class="seat-info">
          <span class="seat-title">执红: {{ engineSide === 'r' ? 'AI特级大师' : '学员' }}</span>
          <span class="seat-desc">{{ currentTurn === 'r' ? '轮到你走子' : '等待对手' }}</span>
        </div>
      </div>
      <div class="seat-right">
        <div class="captured-box" title="战果">
          <span class="cap-lbl">战果:</span>
          <span class="cap-pieces">{{ redCapturedStr || '无' }}</span>
        </div>
        <div class="eval-score-chip" :class="scoreCp >= 0 ? 'score-red' : 'score-black'">
          {{ scoreCp >= 0 ? '红优 +' + (scoreCp / 100).toFixed(1) : '黑优 ' + (scoreCp / 100).toFixed(1) }}
        </div>
      </div>
    </div>

    <!-- 5. 方案二特色：天天象棋式圆形核心功能动作盘 -->
    <section class="action-deck">
      <!-- 悔棋 -->
      <div class="deck-btn-item" @click="handleUndoMove">
        <div class="circle-btn">
          <el-icon :size="18"><Back /></el-icon>
        </div>
        <span class="btn-text">悔棋一手</span>
      </div>

      <!-- 招法提示 (默认关闭箭头，点击后才显示推荐走步) -->
      <div class="deck-btn-item" @click="toggleAnalysisHint">
        <div class="circle-btn" :class="{ 'highlight-active-hint': isAnalysisMode }">
          <el-icon :size="18"><Opportunity /></el-icon>
        </div>
        <span class="btn-text" :class="{ 'hint-active-text': isAnalysisMode }">
          {{ isAnalysisMode ? '隐藏提示' : '招法提示' }}
        </span>
      </div>

      <!-- 立即出招 -->
      <div class="deck-btn-item" @click="triggerEngineBestMove">
        <div class="circle-btn highlight-gold">
          <el-icon :size="18"><Lightning /></el-icon>
        </div>
        <span class="btn-text">立即出招</span>
      </div>

      <!-- 新局 -->
      <div class="deck-btn-item" @click="handleNewGame">
        <div class="circle-btn">
          <el-icon :size="18"><DocumentAdd /></el-icon>
        </div>
        <span class="btn-text">重新开局</span>
      </div>

      <!-- 核心：大师语音通话 (带绿色呼吸高光) -->
      <div class="deck-btn-item" @click="openPhoneCall">
        <div class="circle-btn circle-call-green">
          <el-icon :size="20"><PhoneFilled /></el-icon>
        </div>
        <span class="btn-text call-text">大师连线</span>
      </div>
    </section>

    <!-- 6. 底部特大复盘与指导横栏 (无任何遮罩模糊，棋盘始终 100% 清晰可见) -->
    <footer class="coach-drawer-panel" :class="{ expanded: isDrawerExpanded }">
      <!-- 抽屉顶部手柄 -->
      <div class="drawer-drag-header" @click="isDrawerExpanded = !isDrawerExpanded">
        <div class="drag-bar"></div>
        <div class="drawer-title-row">
          <div class="coach-avatar-tag">
            <span class="avatar-badge">👨‍🏫</span>
            <span class="avatar-name">特级大师点评</span>
            <!-- 语音朗读开关 -->
            <button 
              type="button" 
              class="tts-toggle-btn" 
              :class="{ active: isVoiceEnabled, speaking: isSpeaking }"
              :title="isVoiceEnabled ? (isSpeaking ? '正在语音朗读，点击静音' : '语音已开启，点击静音') : '语音已静音，点击开启语音播报'"
              @click.stop="toggleVoiceOutput"
            >
              <span class="tts-icon">{{ isVoiceEnabled ? (isSpeaking ? '🔊' : '🔈') : '🔇' }}</span>
              <span class="tts-label">{{ isVoiceEnabled ? (isSpeaking ? '正在朗读' : '语音播报') : '已静音' }}</span>
            </button>
          </div>
          <div class="expand-indicator">
            <span>{{ isDrawerExpanded ? '收起问答 ▼' : '展开棋理对答 ▲' }}</span>
          </div>
        </div>
      </div>

      <!-- 大师实时点评气泡 (未展开时紧凑显示，展开时支持滚动，支持 Markdown 美化解析) -->
      <div class="coach-bubble-card">
        <div class="markdown-body bubble-content" v-html="renderMarkdown(coachAdviceText)"></div>
      </div>

      <!-- 展开时展示的历史走步谱与对话记录 (局限在底部，坚决不遮挡棋盘！) -->
      <div v-if="isDrawerExpanded" class="expanded-history-area">
        <div class="tab-selectors">
          <span :class="{ active: activeTab === 'chat' }" @click="activeTab = 'chat'">棋理问答</span>
          <span :class="{ active: activeTab === 'moves' }" @click="activeTab = 'moves'">走步谱 ({{ moveHistoryList.length }})</span>
        </div>

        <!-- 历史问答列表 -->
        <div v-if="activeTab === 'chat'" class="chat-scroll-list">
          <div v-for="(msg, mIdx) in chatHistoryList" :key="mIdx" class="chat-msg" :class="msg.role">
            <span class="chat-role-label">{{ msg.role === 'user' ? '学员问：' : '大师答：' }}</span>
            <div v-if="msg.role === 'assistant'" class="markdown-body chat-markdown" v-html="renderMarkdown(msg.text)"></div>
            <span v-else class="chat-text">{{ msg.text }}</span>
          </div>
        </div>

        <!-- 走步谱列表 -->
        <div v-if="activeTab === 'moves'" class="moves-scroll-list">
          <div v-if="moveHistoryList.length === 0" class="empty-tip">暂无走步记录</div>
          <div
            v-for="(mv, idx) in moveHistoryList"
            :key="idx"
            class="move-row-item"
          >
            <span class="move-seq">{{ idx + 1 }}.</span>
            <span class="move-zh">{{ mv.chinese }}</span>
            <span class="move-uci">{{ mv.uci }}</span>
          </div>
        </div>
      </div>

      <!-- 底部速问文字输入条 (移除不需要的语音输入图标，界面更清爽聚焦) -->
      <div class="drawer-input-row">
        <input
          v-model="mobileInputQuery"
          class="mobile-text-input"
          placeholder="向特大提问（如：我这步走得好吗？）"
          @keyup.enter="handleMobileSend"
        />

        <button class="mobile-send-btn" @click="handleMobileSend">
          发送
        </button>
      </div>
    </footer>

    <!-- 设置与算力侧边抽屉 -->
    <el-drawer
      v-model="showSettingsDrawer"
      title="楚赢象棋 · 对局与算力设置"
      direction="btt"
      size="55%"
      class="mobile-settings-drawer"
    >
      <div class="settings-content">
        <div class="setting-item">
          <span class="item-label">AI执子阵营</span>
          <el-radio-group v-model="engineSide" size="small" @change="onEngineSideChange">
            <el-radio-button value="b">执黑</el-radio-button>
            <el-radio-button value="r">执红</el-radio-button>
            <el-radio-button value="none">双人对弈</el-radio-button>
          </el-radio-group>
        </div>

        <div class="setting-item">
          <span class="item-label">开局定式库</span>
          <el-switch v-model="useOpeningBook" active-text="开启" inactive-text="关闭" />
        </div>

        <div class="setting-item">
          <span class="item-label">皮卡鱼推演算力</span>
          <el-radio-group v-model="depthPreset" size="small" @change="onPresetChange">
            <el-radio-button value="fast">⚡ 快棋 (15层/1秒)</el-radio-button>
            <el-radio-button value="standard">🧠 标准 (20层/1秒)</el-radio-button>
            <el-radio-button value="master">🏆 特大 (30层/1秒)</el-radio-button>
            <el-radio-button value="custom">🛠 自定义</el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="depthPreset === 'custom'" class="setting-item custom-depth-box">
          <div class="custom-row">
            <span class="item-label">目标深度：<strong>{{ searchDepth }}</strong> 层 (上限 128 层)</span>
            <el-slider v-model="searchDepth" :min="10" :max="128" :step="2" show-input />
          </div>
          <div class="custom-row" style="margin-top: 10px;">
            <span class="item-label">思考限时：<strong>{{ searchMovetimeSec }}</strong> 秒</span>
            <el-slider
              v-model="searchMovetimeSec"
              :min="0.5"
              :max="10"
              :step="0.5"
              show-input
              @change="val => searchMovetime = Math.round(val * 1000)"
            />
          </div>
        </div>

        <div class="setting-item">
          <span class="item-label">试拆/分析辅助</span>
          <el-switch v-model="isAnalysisMode" active-text="着法推荐与箭头" inactive-text="关闭" />
        </div>
      </div>
    </el-drawer>

    <!-- 实时双向语音通话弹窗 -->
    <AiPhoneCallDialog
      ref="phoneCallDialogRef"
      :current-fen="currentFen"
      :history-moves="moveHistoryList"
      :selected-voice="'zh-CN-XiaoxiaoNeural'"
      :selected-rate="'+0%'"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import {
  Setting,
  Back,
  Switch,
  Lightning,
  DocumentAdd,
  PhoneFilled,
  Microphone,
  Opportunity
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import axios from 'axios'
import ChessBoard from '@/components/ChessBoard.vue'
import AiPhoneCallDialog from '@/components/AiPhoneCallDialog.vue'
import {
  INITIAL_FEN,
  parseFen,
  boardToFen,
  getLegalMoves,
  uciToChinese
} from '@/utils/chessEngine'

const coordsToUci = (from, to) =>
  `${String.fromCharCode(97 + from.c)}${from.r}${String.fromCharCode(97 + to.c)}${to.r}`

function uciToCoords(uci) {
  if (!uci || uci.length < 4) return null
  return {
    from: { c: uci.charCodeAt(0) - 97, r: parseInt(uci[1], 10) },
    to: { c: uci.charCodeAt(2) - 97, r: parseInt(uci[3], 10) }
  }
}

function makeMoveOnBoard(board, from, to) {
  const p = board[from.r][from.c]
  board[to.r][to.c] = p
  board[from.r][from.c] = null
}

// 统计被吃子战果
const INITIAL_PIECES_COUNT = {
  r: { r: 2, n: 2, b: 2, a: 2, k: 1, c: 2, p: 5 },
  b: { r: 2, n: 2, b: 2, a: 2, k: 1, c: 2, p: 5 }
}

const currentPiecesCount = computed(() => {
  const count = {
    r: { r: 0, n: 0, b: 0, a: 0, k: 0, c: 0, p: 0 },
    b: { r: 0, n: 0, b: 0, a: 0, k: 0, c: 0, p: 0 }
  }
  for (let r = 0; r < 10; r++) {
    for (let c = 0; c < 9; c++) {
      const p = boardState.value[r]?.[c]
      if (p && count[p.color]) {
        count[p.color][p.type]++
      }
    }
  }
  return count
})

const PIECE_ZH = {
  r: { r: '车', n: '马', b: '相', a: '仕', k: '帅', c: '炮', p: '兵' },
  b: { r: '車', n: '馬', b: '象', a: '士', k: '將', c: '砲', p: '卒' }
}

// 黑方吃掉红方的子（红方缺失的子）
const blackCapturedStr = computed(() => {
  const cur = currentPiecesCount.value.r
  const init = INITIAL_PIECES_COUNT.r
  const caps = []
  for (const t in init) {
    const diff = init[t] - (cur[t] || 0)
    if (diff > 0) {
      caps.push(PIECE_ZH.r[t] + (diff > 1 ? `x${diff}` : ''))
    }
  }
  return caps.join(' ')
})

// 红方吃掉黑方的子（黑方缺失的子）
const redCapturedStr = computed(() => {
  const cur = currentPiecesCount.value.b
  const init = INITIAL_PIECES_COUNT.b
  const caps = []
  for (const t in init) {
    const diff = init[t] - (cur[t] || 0)
    if (diff > 0) {
      caps.push(PIECE_ZH.b[t] + (diff > 1 ? `x${diff}` : ''))
    }
  }
  return caps.join(' ')
})
// 1. 棋局核心状态
const currentFen = ref(INITIAL_FEN)
const currentTurn = ref('r')
const boardState = ref([])
const isFlipped = ref(false)
const selectedPiecePos = ref(null)
const legalMoves = ref([])
const lastMoveHighlight = ref(null)
const moveHistoryList = ref([])

// 2. AI 算力与配置
const engineSide = ref('b') // 默认 AI 执黑
const useOpeningBook = ref(true)
const isAnalysisMode = ref(false) // 默认关闭分析箭头，实战下棋不剧透！
const isAnalyzing = ref(false)
const searchDepth = ref(20)
const searchMovetimeSec = ref(1.0)
const searchMovetime = ref(1000)
const depthPreset = ref('standard')
const engineResult = ref(null)
const scoreCp = ref(0)
const showSettingsDrawer = ref(false)

// 优势比例计算 (0~100)
const redAdvantagePct = computed(() => {
  const score = scoreCp.value
  // 以 +-600 厘分为胜负临界点映射至 5%~95%
  const clamped = Math.max(-600, Math.min(600, score))
  return Math.round(50 + (clamped / 600) * 45)
})

// 3. 底部抽屉与特大解说
const isDrawerExpanded = ref(false)
const activeTab = ref('moves')
const coachAdviceText = ref('对局已就绪。起手架中炮攻势迅猛，飞相进兵则稳健沉着，请红方先发制人！')
const chatHistoryList = ref([
  { role: 'assistant', text: '你好！我是你的象棋特大教练，随时在下边给你支招。' }
])

// 语音播报状态管理（默认开启自动语音朗读）
const isVoiceEnabled = ref(true)
const isSpeaking = ref(false)
let activeAudio = null

// 配置 marked 解析
marked.setOptions({
  gfm: true,
  breaks: true
})

function renderMarkdown(content) {
  if (!content) return ''
  try {
    let text = content
      .replace(/^(#{1,6})([^\s#\n])/gm, '$1 $2')
      .replace(/\$\\(to|rightarrow|longrightarrow)\$/g, '→')
      .replace(/\\(to|rightarrow|longrightarrow)\b/g, '→')
      .replace(/\*\*“([^”\n]+)”\*\*/g, '<strong>“$1”</strong>')
      .replace(/“\*\*([^”\n]+)\*\*”/g, '<strong>“$1”</strong>')
      .replace(/\*\*([^：:\n*]+[：:])([^*]+)\*\*/g, '<strong>$1</strong>$2')
      .replace(/(?<!\*)“([^”\n]+)”\*\*/g, '<strong>“$1”</strong>')
      .replace(/\*\*“([^”\n]+)”(?!\*)/g, '<strong>“$1”</strong>')
    return marked.parse(text)
  } catch (e) {
    return content
  }
}

// 停止当前正在播放的音频
function stopSpeechAudio() {
  if (activeAudio) {
    try {
      activeAudio.pause()
      activeAudio.currentTime = 0
    } catch (e) {}
    activeAudio = null
  }
  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel()
  }
  isSpeaking.value = false
}

// 语音播报函数（优先 EdgeTTS 后端高拟真声音，降级浏览器原生 speechSynthesis）
function playVoiceText(text) {
  if (!isVoiceEnabled.value || !text) return
  stopSpeechAudio()

  // 过滤 Markdown 标记，生成适合朗读的纯文本
  const cleanText = text
    .replace(/[#*`~_>]/g, '')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/\n+/g, '，')
    .trim()

  if (!cleanText) return

  isSpeaking.value = true

  // 优先请求后端 TTS 接口
  const url = `${getApiBaseUrl()}/api/tts/speak?text=${encodeURIComponent(cleanText)}&voice=zh-CN-XiaoxiaoNeural&rate=+0%`
  activeAudio = new Audio(url)
  activeAudio.onended = () => {
    isSpeaking.value = false
    activeAudio = null
  }
  activeAudio.onerror = () => {
    // 后端 TTS 失败时降级走浏览器内置 SpeechSynthesis
    activeAudio = null
    if ('speechSynthesis' in window) {
      try {
        const utter = new SpeechSynthesisUtterance(cleanText)
        utter.lang = 'zh-CN'
        utter.rate = 1.0
        utter.onend = () => { isSpeaking.value = false }
        utter.onerror = () => { isSpeaking.value = false }
        window.speechSynthesis.speak(utter)
      } catch (err) {
        isSpeaking.value = false
      }
    } else {
      isSpeaking.value = false
    }
  }

  activeAudio.play().catch(e => {
    console.warn('语音播放受浏览器策略限制:', e)
    isSpeaking.value = false
    activeAudio = null
  })
}

// 切换语音播报开关
function toggleVoiceOutput() {
  if (isSpeaking.value) {
    // 正在朗读时点击直接打断停止
    stopSpeechAudio()
    ElMessage.info('已停止朗读')
    return
  }
  isVoiceEnabled.value = !isVoiceEnabled.value
  if (isVoiceEnabled.value) {
    ElMessage.success('已开启特大语音播报')
    if (coachAdviceText.value) {
      playVoiceText(coachAdviceText.value)
    }
  } else {
    stopSpeechAudio()
    ElMessage.info('已静音特大语音')
  }
}

// 4. 语音速问与 FunASR
const mobileInputQuery = ref('')
const isRecording = ref(false)
const isRecognizing = ref(false)
let asrWs = null
let asrMediaStream = null
let asrAudioContext = null
let asrProcessor = null
let asrSilenceTimer = null
let asrHasSpoken = false

const phoneCallDialogRef = ref(null)

const getWsBaseUrl = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.hostname || 'localhost'
  if (window.location.pathname.startsWith('/chess')) {
    const port = window.location.port ? `:${window.location.port}` : ''
    return `${protocol}//${host}${port}/chess`
  }
  return `${protocol}//${host}:8080`
}

const getApiBaseUrl = () => {
  if (window.location.pathname.startsWith('/chess')) {
    return '/chess'
  }
  const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:'
  const host = window.location.hostname || 'localhost'
  return `${protocol}//${host}:8080`
}

// 初始化棋盘
function initBoardState() {
  const { board, turn } = parseFen(currentFen.value)
  boardState.value = board
  currentTurn.value = turn
  selectedPiecePos.value = null
  legalMoves.value = []
}

onMounted(() => {
  initBoardState()
  if (isAnalysisMode.value) {
    triggerEngineAnalyze()
  }
})

onUnmounted(() => {
  stopSpeechInput()
  stopSpeechAudio()
})

// 点击棋子/棋格交互
function onBoardCellClick({ r, c }) {
  if (currentTurn.value === engineSide.value) {
    return
  }

  const piece = boardState.value[r]?.[c]

  // 若已选中棋子，检查是否点击合法目标格
  if (selectedPiecePos.value) {
    const isTarget = legalMoves.value.some(m => m.r === r && m.c === c)
    if (isTarget) {
      executeUserMove(selectedPiecePos.value, { r, c })
      return
    }
  }

  // 选中当前轮次方棋子
  if (piece && piece.color === currentTurn.value) {
    selectedPiecePos.value = { r, c }
    legalMoves.value = getLegalMoves(boardState.value, r, c)
  } else {
    selectedPiecePos.value = null
    legalMoves.value = []
  }
}

// 执行走棋
async function executeUserMove(from, to) {
  const uci = coordsToUci(from, to)
  const zh = uciToChinese(currentFen.value, uci)

  // 更新盘面
  makeMoveOnBoard(boardState.value, from, to)
  lastMoveHighlight.value = {
    from: { r: from.r, c: from.c },
    to: { r: to.r, c: to.c }
  }
  moveHistoryList.value.push({ uci, chinese: zh })

  selectedPiecePos.value = null
  legalMoves.value = []

  // 翻转行棋权
  currentTurn.value = currentTurn.value === 'r' ? 'b' : 'r'
  currentFen.value = boardToFen(boardState.value, currentTurn.value)

  // 如果轮到 AI 走子，立即在指定时间内计算并落子
  if (currentTurn.value === engineSide.value) {
    executeEngineMove()
  } else {
    // 轮到人类思考，引擎不闲着，持续向更深层（最高 60 层）深度分析推演
    startMobileSseAnalyze()
  }
}

// 移动端对手思考时的持续深度推演控制 (采用单连接 SSE 机制，彻底杜绝高频轮询！)
let currentSseSource = null

function stopMobileSseAnalyze() {
  if (currentSseSource) {
    try {
      currentSseSource.close()
    } catch (e) {}
    currentSseSource = null
  }
}

function startMobileSseAnalyze() {
  if (!isAnalysisMode.value) return
  stopMobileSseAnalyze()

  const fen = currentFen.value
  const targetDepth = searchDepth.value || 20
  const url = `${getApiBaseUrl()}/api/chess/stream-analyze?fen=${encodeURIComponent(fen)}&depth=${targetDepth}&useBook=${useOpeningBook.value}`

  isAnalyzing.value = true
  try {
    currentSseSource = new EventSource(url)

    currentSseSource.addEventListener('analysis', (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data && currentFen.value === fen) {
          engineResult.value = data
          scoreCp.value = data.scoreCp || 0
          if (data.comment) {
            coachAdviceText.value = data.comment
          }
        }
      } catch (e) {}
    })

    currentSseSource.onerror = () => {
      stopMobileSseAnalyze()
      isAnalyzing.value = false
    }
  } catch (e) {
    isAnalyzing.value = false
  }
}

// 请求引擎分析
function triggerEngineAnalyze() {
  startMobileSseAnalyze()
}

// 防重入互斥锁：确保走子绝不并发
let isEngineMoving = false

// AI 走子（严格在指定限时内立即走棋，绝不拖泥带水）
async function executeEngineMove() {
  if (isEngineMoving) return
  isEngineMoving = true

  stopMobileSseAnalyze()
  isAnalyzing.value = true

  const moveFen = currentFen.value
  try {
    const res = await axios.post(`${getApiBaseUrl()}/api/chess/analyze`, {
      fen: moveFen,
      depth: searchDepth.value,
      movetime: searchMovetime.value || 1000,
      immediate: true,
      noCache: true,
      useBook: useOpeningBook.value
    })

    if (!res.data || !res.data.bestMove || currentFen.value !== moveFen) return
    engineResult.value = res.data
    scoreCp.value = res.data.scoreCp || 0

    const bestMove = res.data.bestMove
    const coords = uciToCoords(bestMove)
    if (!coords) return

    // 严密校验：起点必须有子，且必须属于当前行棋方！
    const piece = boardState.value[coords.from.r]?.[coords.from.c]
    if (!piece || piece.color !== currentTurn.value) {
      console.warn('棋子合法性校验未通过，放弃非法移动:', bestMove)
      return
    }

    const zh = uciToChinese(currentFen.value, bestMove)
    makeMoveOnBoard(boardState.value, coords.from, coords.to)
    lastMoveHighlight.value = {
      from: { r: coords.from.r, c: coords.from.c },
      to: { r: coords.to.r, c: coords.to.c }
    }
    moveHistoryList.value.push({ uci: bestMove, chinese: zh })

    currentTurn.value = currentTurn.value === 'r' ? 'b' : 'r'
    currentFen.value = boardToFen(boardState.value, currentTurn.value)

    if (res.data.comment) {
      coachAdviceText.value = res.data.comment
      if (isVoiceEnabled.value) {
        playVoiceText(res.data.comment)
      }
    }

    // 走完后无缝开启对手思考推演
    startMobileSseAnalyze()
  } catch (err) {
    console.warn('AI出招异常:', err)
  } finally {
    isAnalyzing.value = false
    isEngineMoving = false
  }
}

// 悔棋
function handleUndoMove() {
  if (moveHistoryList.value.length === 0 || isEngineMoving) return
  stopMobileSseAnalyze()

  // 若与 AI 对弈且有步骤，回退 2 步；若只有1步或双人回退 1 步
  const stepCount = (engineSide.value && engineSide.value !== 'none') ? Math.min(2, moveHistoryList.value.length) : 1
  for (let i = 0; i < stepCount; i++) {
    moveHistoryList.value.pop()
  }

  // 从初盘重放，确保盘面状态绝对准确
  let state = parseFen(INITIAL_FEN)
  boardState.value = state.board
  currentTurn.value = state.turn
  for (const item of moveHistoryList.value) {
    const c = uciToCoords(item.uci)
    if (c) makeMoveOnBoard(boardState.value, c.from, c.to)
    currentTurn.value = currentTurn.value === 'r' ? 'b' : 'r'
  }
  currentFen.value = boardToFen(boardState.value, currentTurn.value)
  lastMoveHighlight.value = null
  selectedPiecePos.value = null
  legalMoves.value = []
  triggerEngineAnalyze()
}

// 新局
function handleNewGame() {
  if (isEngineMoving) return
  stopMobileSseAnalyze()
  currentFen.value = INITIAL_FEN
  moveHistoryList.value = []
  lastMoveHighlight.value = null
  initBoardState()
  triggerEngineAnalyze()
  ElMessage.success('棋局已重置')
}

// 立即让 AI 出招（严格防抖，连点直接忽略）
function triggerEngineBestMove() {
  if (isEngineMoving) return
  executeEngineMove()
}

// 档位切换 (快棋、标准、特大默认思考时间均为 1.0 秒，自定义支持自由配置)
function onPresetChange(val) {
  if (val === 'fast') {
    searchDepth.value = 15
    searchMovetimeSec.value = 1.0
    searchMovetime.value = 1000
  } else if (val === 'standard') {
    searchDepth.value = 20
    searchMovetimeSec.value = 1.0
    searchMovetime.value = 1000
  } else if (val === 'master') {
    searchDepth.value = 30
    searchMovetimeSec.value = 1.0
    searchMovetime.value = 1000
  } else if (val === 'custom') {
    searchDepth.value = Math.max(30, searchDepth.value)
  }
  triggerEngineAnalyze()
}

function onEngineSideChange(val) {
  if (currentTurn.value === val) {
    setTimeout(executeEngineMove, 300)
  }
}

// 切换招法提示箭头 (静默切换，不弹窗打扰)
function toggleAnalysisHint() {
  isAnalysisMode.value = !isAnalysisMode.value
  if (isAnalysisMode.value && !engineResult.value?.bestMove) {
    triggerEngineAnalyze()
  }
}

// 开启语音电话
function openPhoneCall() {
  if (phoneCallDialogRef.value) {
    phoneCallDialogRef.value.openCall()
  }
}

// 移动端语音输入 (FunASR)
async function toggleMobileSpeechInput() {
  if (isRecording.value) {
    finishRecording()
  } else if (!isRecognizing.value) {
    await startSpeechInput()
  }
}

async function startSpeechInput() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    ElMessage.warning('当前环境不支持麦克风调用')
    return
  }
  try {
    asrMediaStream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, sampleRate: 16000, echoCancellation: true, noiseSuppression: true }
    })
    asrWs = new WebSocket(`${getWsBaseUrl()}/ws/ai-call`)
    asrHasSpoken = false

    asrWs.onopen = () => {
      isRecording.value = true
      isRecognizing.value = false
      ElMessage.success('正在录音，请说出棋理问题...')
    }

    asrWs.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'asr_partial' || data.type === 'asr_final') {
          if (data.text && data.text.trim()) {
            mobileInputQuery.value = data.text
          }
          if (data.type === 'asr_final') {
            isRecognizing.value = false
            closeAsrWs()
          }
        }
      } catch (e) {}
    }

    asrWs.onerror = () => {
      ElMessage.warning('未能连接语音识别服务')
      stopSpeechInput()
    }

    asrAudioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 })
    const source = asrAudioContext.createMediaStreamSource(asrMediaStream)
    asrProcessor = asrAudioContext.createScriptProcessor(2048, 1, 1)

    asrProcessor.onaudioprocess = (e) => {
      if (!asrWs || asrWs.readyState !== WebSocket.OPEN) return
      const input = e.inputBuffer.getChannelData(0)
      let sum = 0
      for (let i = 0; i < input.length; i++) sum += input[i] * input[i]
      const rms = Math.sqrt(sum / input.length)

      if (rms > 0.015) {
        asrHasSpoken = true
        if (asrSilenceTimer) {
          clearTimeout(asrSilenceTimer)
          asrSilenceTimer = null
        }
      } else if (asrHasSpoken && !asrSilenceTimer) {
        asrSilenceTimer = setTimeout(() => {
          if (isRecording.value) finishRecording()
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

function finishRecording() {
  isRecording.value = false
  isRecognizing.value = true
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
  if (asrWs && asrWs.readyState === WebSocket.OPEN) {
    try {
      asrWs.send(JSON.stringify({ type: 'end_audio' }))
    } catch (e) {}
    setTimeout(() => {
      isRecognizing.value = false
      closeAsrWs()
    }, 2500)
  } else {
    isRecognizing.value = false
  }
}

function closeAsrWs() {
  if (asrWs) {
    try { asrWs.close() } catch (e) {}
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

// 发送文字提问 (优先流式打字机逐字响应，流畅呈现特大棋理)
async function handleMobileSend() {
  const query = mobileInputQuery.value.trim()
  if (!query) return

  // 1. 用户提问入列
  chatHistoryList.value.push({ role: 'user', text: query })
  mobileInputQuery.value = ''
  isDrawerExpanded.value = true
  activeTab.value = 'chat'

  // 2. 占位特大回答卡片
  const replyIdx = chatHistoryList.value.length
  chatHistoryList.value.push({ role: 'assistant', text: '正在深入推演盘面...' })
  coachAdviceText.value = '大师正在为你剖析局势...'

  const historyStr = moveHistoryList.value.map(m => m.chinese).join(' ')
  const streamUrl = `${getApiBaseUrl()}/api/chess/chat/stream?fen=${encodeURIComponent(currentFen.value)}&history=${encodeURIComponent(historyStr)}&question=${encodeURIComponent(query)}`

  let hasReceivedToken = false
  let eventSource = null

  try {
    eventSource = new EventSource(streamUrl)

    eventSource.onmessage = (event) => {
      const chunk = (event.data || '').replace(/\\n/g, '\n')
      if (!hasReceivedToken) {
        chatHistoryList.value[replyIdx].text = ''
        coachAdviceText.value = ''
        hasReceivedToken = true
      }
      chatHistoryList.value[replyIdx].text += chunk
      coachAdviceText.value += chunk
    }

    eventSource.addEventListener('finish', () => {
      if (eventSource) eventSource.close()
      // 回答结束后，自动朗读 AI 生成的回答
      if (isVoiceEnabled.value && chatHistoryList.value[replyIdx]?.text) {
        playVoiceText(chatHistoryList.value[replyIdx].text)
      }
    })

    eventSource.onerror = async () => {
      if (eventSource) eventSource.close()
      // 若尚未收到流式数据，自动回退到普通 POST 接口
      if (!hasReceivedToken) {
        try {
          const res = await axios.post(`${getApiBaseUrl()}/api/chess/chat`, {
            fen: currentFen.value,
            question: query,
            history: historyStr
          })
          if (res.data) {
            chatHistoryList.value[replyIdx].text = res.data
            coachAdviceText.value = res.data
            if (isVoiceEnabled.value) {
              playVoiceText(res.data)
            }
          }
        } catch (e) {
          chatHistoryList.value[replyIdx].text = '特级大师正在沉思，请稍后重新提问。'
        }
      }
    }
  } catch (err) {
    // 直接降级 POST
    try {
      const res = await axios.post(`${getApiBaseUrl()}/api/chess/chat`, {
        fen: currentFen.value,
        question: query,
        history: historyStr
      })
      if (res.data) {
        chatHistoryList.value[replyIdx].text = res.data
        coachAdviceText.value = res.data
        if (isVoiceEnabled.value) {
          playVoiceText(res.data)
        }
      }
    } catch (e) {
      chatHistoryList.value[replyIdx].text = '特级大师正在沉思，请稍后重新提问。'
    }
  }
}
</script>

<style lang="scss" scoped>
.mobile-app-container {
  width: 100%;
  max-width: 480px;
  height: 100vh;
  height: 100dvh;
  max-height: 100dvh;
  margin: 0 auto;
  /* 清雅素净浅色对弈茶台背景，告别压抑沉闷的死黑 */
  background-color: #f3efe6;
  background-image: 
    radial-gradient(ellipse at 50% 30%, rgba(255, 255, 255, 0.6) 0%, rgba(210, 195, 175, 0.45) 100%),
    repeating-linear-gradient(0deg, rgba(160, 130, 95, 0.04) 0px, rgba(160, 130, 95, 0.04) 1px, transparent 1px, transparent 4px);
  color: #1f2937;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden; /* 彻底锁定手机屏幕，杜绝上下滑动 */
  user-select: none;
  box-shadow: 0 0 40px rgba(0, 0, 0, 0.15);
}

/* 顶部栏 (白底明雅) */
.mobile-top-header {
  height: 44px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 12px;
  background: #ffffff;
  border-bottom: 1px solid #e5e0d3;
  flex-shrink: 0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);

  .brand-group {
    display: flex;
    align-items: center;
    gap: 8px;

    /* 武汉黄底 + 朱砂红字印章 Logo */
    .wuhan-seal-logo {
      width: 28px;
      height: 28px;
      background: linear-gradient(135deg, #fde047 0%, #f59e0b 55%, #d97706 100%);
      border-radius: 6px;
      border: 1.5px solid #fef08a;
      box-shadow: 0 2px 6px rgba(245, 158, 11, 0.35), inset 0 1px 1px rgba(255, 255, 255, 0.9);
      display: flex;
      align-items: center;
      justify-content: center;
      color: #b91c1c;
      font-size: 16px;
      font-weight: 900;
      font-family: 'STKaiti', 'Kaiti', 'KaiTi_GB2312', serif;
      letter-spacing: -1px;
    }

    .brand-info {
      display: flex;
      flex-direction: column;

      .app-name {
        font-size: 15px;
        font-weight: 800;
        color: #1f2937;
        line-height: 1.15;
      }
      .app-sub {
        font-size: 10px;
        color: #b45309;
        font-weight: 600;
      }
    }
  }

  .header-right-tools {
    display: flex;
    align-items: center;
    gap: 6px;

    .settings-mini-btn {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: #f3f4f6;
      border: 1px solid #e5e7eb;
      color: #4b5563;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      font-size: 12px;
      transition: background 0.15s;

      &:active {
        background: #e5e7eb;
      }
    }
  }
}

/* 局势优势条 */
.eval-bar-strip {
  width: 100%;
  height: 3px;
  background: #cbd5e1;
  position: relative;
  flex-shrink: 0;

  .eval-red-fill {
    height: 100%;
    background: linear-gradient(90deg, #ef4444, #f97316);
    box-shadow: 0 0 4px rgba(239, 68, 68, 0.5);
    transition: width 0.3s ease;
  }
}

/* 黑方对手席位 (浅色清雅卡片) */
.opponent-seat-bar {
  margin: 3px 10px 1px;
  padding: 4px 10px;
  background: #ffffff;
  border: 1px solid #e5e0d3;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);

  .seat-left {
    display: flex;
    align-items: center;
    gap: 7px;

    .seat-avatar {
      width: 26px;
      height: 26px;
      border-radius: 50%;
      background: #374151;
      border: 1px solid #4b5563;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
    }

    .seat-info {
      display: flex;
      flex-direction: column;

      .seat-title {
        font-size: 12px;
        font-weight: 700;
        color: #1f2937;
        line-height: 1.2;
      }
      .seat-desc {
        font-size: 9px;
        color: #6b7280;
      }
    }
  }

  .seat-right {
    display: flex;
    align-items: center;
    gap: 8px;

    .captured-box {
      display: flex;
      align-items: center;
      gap: 3px;
      background: #f3efe6;
      border: 1px solid #e5e0d3;
      padding: 1px 6px;
      border-radius: 6px;
      font-size: 10px;

      .cap-lbl { color: #6b7280; font-size: 9px; }
      .cap-pieces { color: #dc2626; font-weight: bold; font-family: 'STKaiti', serif; }
    }

    .turn-chip {
      font-size: 9px;
      background: rgba(239, 68, 68, 0.12);
      border: 1px solid rgba(239, 68, 68, 0.3);
      color: #dc2626;
      padding: 1px 4px;
      border-radius: 4px;
      font-weight: bold;
    }
  }
}

/* 红方学员席位 (浅色清雅卡片) */
.my-seat-bar {
  margin: 1px 10px 3px;
  padding: 4px 10px;
  background: #ffffff;
  border: 1px solid #e5e0d3;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);

  .seat-left {
    display: flex;
    align-items: center;
    gap: 7px;

    .seat-avatar.my-avatar {
      width: 26px;
      height: 26px;
      border-radius: 50%;
      background: linear-gradient(135deg, #dc2626, #b91c1c);
      border: 1px solid #ef4444;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      box-shadow: 0 1px 4px rgba(220, 38, 38, 0.3);
    }

    .seat-info {
      display: flex;
      flex-direction: column;

      .seat-title {
        font-size: 12px;
        font-weight: 700;
        color: #1f2937;
        line-height: 1.2;
      }
      .seat-desc {
        font-size: 9px;
        color: #dc2626;
      }
    }
  }

  .seat-right {
    display: flex;
    align-items: center;
    gap: 8px;

    .captured-box {
      display: flex;
      align-items: center;
      gap: 3px;
      background: #f3efe6;
      border: 1px solid #e5e0d3;
      padding: 1px 6px;
      border-radius: 6px;
      font-size: 10px;

      .cap-lbl { color: #6b7280; font-size: 9px; }
      .cap-pieces { color: #1f2937; font-weight: bold; font-family: 'STKaiti', serif; }
    }

    .eval-score-chip {
      font-size: 11px;
      font-weight: 700;
      font-family: monospace;
      padding: 1px 6px;
      border-radius: 4px;

      &.score-red {
        background: #fee2e2;
        color: #dc2626;
        border: 1px solid #fca5a5;
      }
      &.score-black {
        background: #f3f4f6;
        color: #4b5563;
        border: 1px solid #d1d5db;
      }
    }
  }
}

/* 主棋盘容器：宽度自适应撑满手机屏幕，绝不坍缩 */
.board-container {
  flex: 1;
  min-height: 0;
  padding: 4px 10px;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;

  .board-touch-wrapper {
    width: 100%;
    max-width: min(96vw, 390px);
    display: flex;
    justify-content: center;
    align-items: center;

    :deep(.chess-board-wrapper) {
      width: 100%;
      max-width: 100%;
      aspect-ratio: 9 / 10;
    }
  }
}

/* 方案二天天象棋动作盘 */
.action-deck {
  height: 60px;
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 0 10px;
  background: #141720;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;

  .deck-btn-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 3px;
    cursor: pointer;

    .circle-btn {
      width: 38px;
      height: 38px;
      border-radius: 50%;
      background: #ffffff;
      border: 1px solid #e5e0d3;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 17px;
      color: #374151;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
      transition: all 0.2s ease;

      &:active {
        transform: scale(0.92);
        background: #ede8dc;
      }

      &.highlight-gold {
        color: #d97706;
        background: #fef3c7;
        border-color: #fde68a;
      }

      &.highlight-active-hint {
        color: #0284c7;
        background: #e0f2fe;
        border-color: #bae6fd;
        box-shadow: 0 0 8px rgba(2, 132, 199, 0.3);
      }

      &.circle-call-green {
        background: linear-gradient(135deg, #10b981, #059669);
        border: 1px solid #34d399;
        color: #ffffff;
        box-shadow: 0 2px 8px rgba(16, 185, 129, 0.35);
        animation: pulse-call 2s infinite ease-in-out;
      }
    }

    .btn-text {
      font-size: 10px;
      color: #6b7280;

      &.call-text {
        color: #059669;
        font-weight: 700;
      }
      &.hint-active-text {
        color: #0284c7;
        font-weight: 700;
      }
    }
  }
}

@keyframes pulse-call {
  0%, 100% { box-shadow: 0 2px 6px rgba(16, 185, 129, 0.3); }
  50% { box-shadow: 0 2px 14px rgba(16, 185, 129, 0.6); }
}

/* 底部特大解盘与语音对话横栏 (清雅白底，棋盘始终 100% 清晰可见) */
.coach-drawer-panel {
  background: #ffffff;
  border-top: 1px solid #e5e0d3;
  border-radius: 12px 12px 0 0;
  padding: 6px 12px 8px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: all 0.25s ease;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
  position: relative;
  z-index: 20;

  &.expanded {
    max-height: 220px;
    box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.08);
  }

  .drawer-drag-header {
    cursor: pointer;
    margin-bottom: 4px;

    .drag-bar {
      width: 32px;
      height: 3px;
      background: #475569;
      border-radius: 2px;
      margin: 0 auto 4px;
    }

    .drawer-title-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 11px;

  .coach-avatar-tag {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #1f2937;
    font-weight: 700;

    .avatar-badge {
      font-size: 13px;
    }

    .tts-toggle-btn {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      padding: 2px 7px;
      border-radius: 12px;
      font-size: 10px;
      font-weight: 500;
      border: 1px solid #d1d5db;
      background: #f3f4f6;
      color: #6b7280;
      cursor: pointer;
      transition: all 0.2s ease;

      &.active {
        background: #ecfdf5;
        border-color: #a7f3d0;
        color: #059669;
      }

      &.speaking {
        background: #eff6ff;
        border-color: #bfdbfe;
        color: #2563eb;
        animation: pulse-tts 1.5s infinite;
      }
    }
  }

  @keyframes pulse-tts {
    0% { transform: scale(1); }
    50% { transform: scale(1.05); }
    100% { transform: scale(1); }
  }

      .expand-indicator {
        color: #0284c7;
        font-size: 10px;
        font-weight: 600;
      }
    }
  }

  .coach-bubble-card {
    background: #f8f6f0;
    border: 1px solid #e8e2d5;
    border-radius: 6px;
    padding: 6px 10px;
    border-left: 3px solid #10b981;
    font-size: 11px;
    color: #374151;
    line-height: 1.4;
    margin-bottom: 6px;
    max-height: 52px;
    overflow: hidden;
    text-overflow: ellipsis;

    .bubble-content {
      font-size: 11px;
      line-height: 1.4;

      :deep(p) {
        margin: 0 0 4px 0;
        &:last-child { margin-bottom: 0; }
      }
      :deep(strong) {
        color: #111827;
        font-weight: 700;
      }
      :deep(ul), :deep(ol) {
        margin: 2px 0;
        padding-left: 14px;
      }
      :deep(li) {
        margin-bottom: 2px;
      }
    }
  }

  &.expanded .coach-bubble-card {
    max-height: 120px;
    overflow-y: auto;
    display: block;
  }

  .expanded-history-area {
    margin-bottom: 8px;
    flex: 1;
    overflow: hidden;
    display: flex;
    flex-direction: column;

    .tab-selectors {
      display: flex;
      gap: 16px;
      border-bottom: 1px solid #e5e7eb;
      padding-bottom: 4px;
      margin-bottom: 6px;
      font-size: 12px;

      span {
        color: #6b7280;
        cursor: pointer;

        &.active {
          color: #059669;
          font-weight: bold;
          border-bottom: 2px solid #059669;
          padding-bottom: 3px;
        }
      }
    }

    .moves-scroll-list, .chat-scroll-list {
      max-height: 180px;
      overflow-y: auto;
      font-size: 12px;
    }

    .move-row-item {
      display: flex;
      gap: 12px;
      padding: 4px 6px;
      border-bottom: 1px solid #f3f4f6;
      .move-seq { color: #9ca3af; width: 24px; }
      .move-zh { color: #1f2937; font-weight: 600; }
      .move-uci { color: #6b7280; font-family: monospace; }
    }

    .chat-msg {
      margin-bottom: 6px;
      line-height: 1.4;
      &.user { color: #059669; font-weight: 600; }
      &.assistant {
        color: #0284c7;

        .chat-markdown {
          font-size: 11px;
          line-height: 1.4;
          display: inline-block;
          color: #374151;

          :deep(p) {
            margin: 0 0 4px 0;
            &:last-child { margin-bottom: 0; }
          }
          :deep(strong) {
            color: #111827;
            font-weight: 700;
          }
          :deep(ul), :deep(ol) {
            margin: 2px 0;
            padding-left: 14px;
          }
          :deep(li) {
            margin-bottom: 2px;
          }
        }
      }
    }

    .empty-tip {
      color: #9ca3af;
      text-align: center;
      padding: 10px 0;
    }
  }

  .drawer-input-row {
    display: flex;
    align-items: center;
    gap: 6px;

    .mobile-text-input {
      flex: 1;
      height: 32px;
      background: #f8f6f0;
      border: 1px solid #dfd9cb;
      border-radius: 16px;
      padding: 0 12px;
      color: #1f2937;
      font-size: 11px;
      outline: none;

      &::placeholder {
        color: #9ca3af;
      }
    }

    .mobile-send-btn {
      height: 32px;
      padding: 0 12px;
      border-radius: 16px;
      background: #2563eb;
      border: none;
      color: #ffffff;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      flex-shrink: 0;

      &:active {
        background: #1d4ed8;
      }
    }
  }
}

@keyframes pulse-rec {
  0% { transform: scale(1); box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7); }
  70% { transform: scale(1.06); box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
  100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
}

/* 抽屉设置内容 */
.settings-content {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .setting-item {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .item-label {
      font-size: 13px;
      color: #e2e8f0;
      font-weight: 500;
    }
  }
}
</style>
