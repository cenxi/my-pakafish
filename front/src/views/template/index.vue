<template>
  <div class="pakafish-app-layout">
    <!-- 1. 顶部操作工具栏 -->
    <header class="top-toolbar">
      <div class="logo-area">
        <span class="logo-icon">🐟</span>
        <span class="logo-title">皮卡鱼·AI象棋特大</span>
      </div>

      <div class="toolbar-buttons">
        <el-button-group>
          <el-button :icon="DocumentAdd" @click="handleNewGame">新局</el-button>
          <el-button
            :type="engineSide === 'r' ? 'danger' : 'default'"
            @click="toggleEngineSide('r')"
          >
            引擎执红
          </el-button>
          <el-button
            :type="engineSide === 'b' ? 'info' : 'default'"
            @click="toggleEngineSide('b')"
          >
            引擎执黑
          </el-button>
          <el-button
            :type="isAnalysisMode ? 'primary' : 'default'"
            @click="isAnalysisMode = !isAnalysisMode"
          >
            分析模式: {{ isAnalysisMode ? '开启' : '关闭' }}
          </el-button>
        </el-button-group>

        <el-button-group class="ml-2">
          <el-button :icon="Lightning" type="warning" @click="triggerEngineBestMove">立即出招</el-button>
          <el-button :icon="Switch" @click="isFlipped = !isFlipped">翻转</el-button>
          <el-button :icon="ChatLineRound" type="success" @click="handleAskCoach">大师指导</el-button>
        </el-button-group>

        <!-- 动态推演深度与算力设置 -->
        <div class="depth-setting-group ml-2">
          <el-popover placement="bottom" :width="280" trigger="click">
            <template #reference>
              <el-button size="small" type="primary" plain :icon="Setting">
                算力: {{ currentPresetLabel }} ({{ searchDepth }}层)
              </el-button>
            </template>
            <div class="depth-popover-content">
              <div class="popover-title">皮卡鱼推演深度与算力档位</div>
              <el-radio-group v-model="depthPreset" size="small" class="preset-radios" @change="onPresetChange">
                <el-radio-button value="fast">⚡ 快棋 (15层)</el-radio-button>
                <el-radio-button value="standard">🧠 标准 (20层)</el-radio-button>
                <el-radio-button value="master">🏆 特大 (30层)</el-radio-button>
                <el-radio-button value="custom">🛠 自定义</el-radio-button>
              </el-radio-group>
              <div v-if="depthPreset === 'custom'" class="custom-slider-box">
                <div class="slider-label">
                  <span>目标深度：<strong>{{ searchDepth }}</strong> 层</span>
                </div>
                <el-slider v-model="searchDepth" :min="10" :max="50" :step="2" show-input />
              </div>
              <div class="tip-text">深度越深，皮卡鱼对长变例和绝杀的算力越强。</div>
            </div>
          </el-popover>
        </div>

        <!-- 面板展开/收缩控制 -->
        <el-button-group class="ml-2">
          <el-tooltip :content="showMoveTree ? '收起着法谱' : '展开着法谱'" placement="bottom">
            <el-button
              size="small"
              :type="showMoveTree ? 'default' : 'info'"
              :icon="showMoveTree ? ArrowLeft : ArrowRight"
              @click="showMoveTree = !showMoveTree"
            >
              着法谱
            </el-button>
          </el-tooltip>
          <el-tooltip :content="showEnginePanel ? '收起底部分析' : '展开底部分析'" placement="bottom">
            <el-button
              size="small"
              :type="showEnginePanel ? 'default' : 'info'"
              :icon="showEnginePanel ? ArrowDown : ArrowUp"
              @click="showEnginePanel = !showEnginePanel"
            >
              引擎面板
            </el-button>
          </el-tooltip>
        </el-button-group>
      </div>
    </header>

    <!-- 2. 主体工作区 -->
    <div class="main-workspace">
      <!-- 左列：棋盘 + 步进控制 + 局势优劣条 -->
      <section class="left-board-col">
        <ChessBoard
          :board="boardState"
          :is-flipped="isFlipped"
          :selected-pos="selectedPiecePos"
          :legal-moves="legalMoves"
          :last-move="lastMoveHighlight"
          :suggest-move-uci="engineResult?.bestMove"
          @cell-click="onBoardCellClick"
        />

        <!-- 步进控制按钮 -->
        <div class="step-controls">
          <el-button size="small" :disabled="currentMoveIndex <= 0" @click="jumpToMove(0)">《 开始</el-button>
          <el-button size="small" :disabled="currentMoveIndex <= 0" @click="stepMove(-1)">〈 上一步</el-button>
          <span class="step-text">{{ currentMoveIndex }} / {{ historyMoves.length }}</span>
          <el-button size="small" :disabled="currentMoveIndex >= historyMoves.length" @click="stepMove(1)">下一步 〉</el-button>
          <el-button size="small" :disabled="currentMoveIndex >= historyMoves.length" @click="jumpToMove(historyMoves.length)">最新 》</el-button>
        </div>

        <!-- 局势评估条：纯展示 红优/黑优多少分 -->
        <div class="eval-bar-card">
          <div class="eval-info">
            <span class="turn-tag" :class="currentTurn">轮到{{ currentTurn === 'r' ? '红方' : '黑方' }}走</span>
            <span class="score-text" :class="advantageClass">
              {{ advantageLabel }}
            </span>
            <span class="status-desc">{{ engineResult?.advantageDescription || '正在计算局势...' }}</span>
          </div>
          <!-- 局势平衡度指示条：中立50，红优向右红，黑优向左黑 -->
          <div class="advantage-bar-wrapper">
            <div class="advantage-bar" :style="advantageBarStyle"></div>
          </div>
        </div>
      </section>

      <!-- 中列：着法记录树 (可收起) -->
      <section v-show="showMoveTree" class="center-move-tree">
        <div class="move-tree-header">
          <span class="title">对局着法谱</span>
          <el-button size="small" text type="primary" @click="copyFen">复制FEN</el-button>
        </div>
        <div class="move-list">
          <div
            v-for="(move, idx) in movePairs"
            :key="idx"
            class="move-pair-row"
            :class="{ active: Math.floor((currentMoveIndex - 1) / 2) === idx }"
          >
            <span class="round-num">{{ idx + 1 }}.</span>
            <span
              class="move-step red"
              :class="{ selected: currentMoveIndex === idx * 2 + 1 }"
              @click="jumpToMove(idx * 2 + 1)"
            >
              {{ move.red || '' }}
            </span>
            <span
              class="move-step black"
              :class="{ selected: currentMoveIndex === idx * 2 + 2 }"
              @click="jumpToMove(idx * 2 + 2)"
            >
              {{ move.black || '' }}
            </span>
          </div>
          <div v-if="historyMoves.length === 0" class="empty-hint">暂无走步，点击棋盘开始对局</div>
        </div>
      </section>

      <!-- 右列：右上 AI 大师对话 + 右下皮卡鱼计算面板 -->
      <section class="right-ai-col">
        <!-- 右上：AI 对话分析 (自适应最大化占满) -->
        <div class="coach-chat-wrap">
          <AiCoachChat
            ref="aiCoachRef"
            :current-fen="currentFen"
            :latest-move-chinese="latestMoveChinese"
            :engine-analysis="engineResult"
          />
        </div>

        <!-- 右下：皮卡鱼引擎分析实时面板 (可折叠收缩) -->
        <div v-show="showEnginePanel" class="engine-panel-card">
          <div class="engine-header">
            <span class="engine-title">
              <el-icon class="mr-1"><Cpu /></el-icon> 皮卡鱼 Pikafish 实时计算
            </span>
            <div class="header-right">
              <span v-if="isAnalyzing" class="analyzing-spin mr-2">
                <el-icon class="is-loading"><Loading /></el-icon> 深度推演中...
              </span>
              <el-button size="small" text :icon="Close" @click="showEnginePanel = false" />
            </div>
          </div>

          <div class="engine-data-grid">
            <div class="data-item">
              <span class="label">最佳选点:</span>
              <span class="val highlight">{{ engineResult?.bestMoveChinese || '推演中' }}</span>
              <span class="sub-val">({{ engineResult?.bestMove || '--' }})</span>
            </div>
            <div class="data-item">
              <span class="label">分值评估:</span>
              <span class="val" :class="advantageClass">{{ advantageLabel }}</span>
            </div>
            <div class="data-item">
              <span class="label">搜索深度:</span>
              <span class="val">{{ engineResult?.depth || '--' }} 层</span>
            </div>
          </div>

          <!-- PV 最佳路径 -->
          <div class="pv-line-box">
            <span class="pv-label">推荐路线推演：</span>
            <span class="pv-content">
              {{ (engineResult?.pvMovesChinese || []).join(' → ') || '等待计算' }}
            </span>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import {
  DocumentAdd,
  Lightning,
  Switch,
  ChatLineRound,
  Cpu,
  Loading,
  ArrowLeft,
  ArrowRight,
  ArrowDown,
  ArrowUp,
  Close,
  Setting
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

import ChessBoard from '@/components/ChessBoard.vue'
import AiCoachChat from '@/components/AiCoachChat.vue'
import {
  INITIAL_FEN,
  parseFen,
  boardToFen,
  getLegalMoves,
  uciToChinese
} from '@/utils/chessEngine'

// 状态
const currentFen = ref(INITIAL_FEN)
const boardState = ref(parseFen(INITIAL_FEN).board)
const currentTurn = ref('r')
const isFlipped = ref(false)
const isAnalysisMode = ref(true)
const engineSide = ref(null) // 'r', 'b', null
const isAnalyzing = ref(false)

// 界面收缩控制
const showMoveTree = ref(true)
const showEnginePanel = ref(true)

// 动态推演深度与算力档位
const depthPreset = ref('standard')
const searchDepth = ref(20)
const searchMovetime = ref(1500)

const currentPresetLabel = computed(() => {
  switch (depthPreset.value) {
    case 'fast': return '快棋'
    case 'standard': return '标准'
    case 'master': return '特大深算'
    default: return '自定义'
  }
})

function onPresetChange(val) {
  if (val === 'fast') {
    searchDepth.value = 15
    searchMovetime.value = 800
  } else if (val === 'standard') {
    searchDepth.value = 20
    searchMovetime.value = 1500
  } else if (val === 'master') {
    searchDepth.value = 30
    searchMovetime.value = 3500
  }
  triggerPikafishAnalyze()
}

const selectedPiecePos = ref(null)
const legalMoves = ref([])
const lastMoveHighlight = ref(null)

// 历史记录与分支
const historyMoves = ref([]) // [ { uci, chinese, fen } ]
const currentMoveIndex = ref(0)
const engineResult = ref(null)
const aiCoachRef = ref(null)

const latestMoveChinese = computed(() => {
  if (currentMoveIndex.value <= 0) return ''
  return historyMoves.value[currentMoveIndex.value - 1]?.chinese || ''
})

const movePairs = computed(() => {
  const pairs = []
  for (let i = 0; i < historyMoves.value.length; i += 2) {
    pairs.push({
      red: historyMoves.value[i]?.chinese,
      black: historyMoves.value[i + 1]?.chinese
    })
  }
  return pairs
})

// 计算明确的红优/黑优多少分
const redScoreCp = computed(() => {
  if (!engineResult.value || engineResult.value.scoreCp === undefined) return 0
  // 后端传出的 scoreCp 已经是以红方为绝对基准（正数为红优，负数为黑优）
  return engineResult.value.scoreCp
})

const isRedAdvantage = computed(() => {
  if (engineResult.value?.sideAdvantageText) {
    return engineResult.value.sideAdvantageText.includes('红优')
  }
  return redScoreCp.value > 20
})

const isBlackAdvantage = computed(() => {
  if (engineResult.value?.sideAdvantageText) {
    return engineResult.value.sideAdvantageText.includes('黑优')
  }
  return redScoreCp.value < -20
})

const advantageLabel = computed(() => {
  if (engineResult.value?.sideAdvantageText) {
    return engineResult.value.sideAdvantageText
  }
  const score = redScoreCp.value
  if (score === 0) return '均势 (0分)'
  if (score > 0) return `红优 +${score}分`
  return `黑优 +${Math.abs(score)}分`
})

const advantageClass = computed(() => {
  if (isRedAdvantage.value) return 'text-red'
  if (isBlackAdvantage.value) return 'text-black'
  return 'text-balance'
})

// 平衡指示条样式 (红方在右，黑方在左；红优偏红，黑优偏黑)
const advantageBarStyle = computed(() => {
  const score = redScoreCp.value
  // -1000 ~ +1000 映射为 0% ~ 100%
  const clamped = Math.max(-1000, Math.min(1000, score))
  const percent = 50 + (clamped / 1000) * 50
  return {
    width: `${percent}%`,
    backgroundColor: isRedAdvantage.value ? '#f56c6c' : (isBlackAdvantage.value ? '#1d2129' : '#e6a23c')
  }
})

onMounted(() => {
  triggerPikafishAnalyze()
})

// 点击棋盘落子
function onBoardCellClick({ r, c }) {
  const piece = boardState.value[r]?.[c]

  if (selectedPiecePos.value) {
    const isTargetLegal = legalMoves.value.some(m => m.r === r && m.c === c)
    if (isTargetLegal) {
      executeMove(selectedPiecePos.value, { r, c })
      selectedPiecePos.value = null
      legalMoves.value = []
      return
    }
  }

  if (piece && piece.color === currentTurn.value) {
    selectedPiecePos.value = { r, c }
    legalMoves.value = getLegalMoves(boardState.value, r, c)
  } else {
    selectedPiecePos.value = null
    legalMoves.value = []
  }
}

// 执行走棋
function executeMove(from, to) {
  const piece = boardState.value[from.r][from.c]
  if (!piece) {
    console.warn('executeMove 起点无子', from)
    return
  }

  const uci = `${String.fromCharCode(97 + from.c)}${from.r}${String.fromCharCode(97 + to.c)}${to.r}`
  const chinese = uciToChinese(currentFen.value, uci)

  // 1. 先用当前局面记录
  const moveTurn = currentTurn.value

  // 2. 移动棋子
  boardState.value[to.r][to.c] = piece
  boardState.value[from.r][from.c] = null

  // 3. 切换行棋方
  currentTurn.value = moveTurn === 'r' ? 'b' : 'r'
  const newFen = boardToFen(boardState.value, currentTurn.value)
  currentFen.value = newFen

  lastMoveHighlight.value = { from, to }

  if (currentMoveIndex.value < historyMoves.value.length) {
    historyMoves.value = historyMoves.value.slice(0, currentMoveIndex.value)
  }

  historyMoves.value.push({ uci, chinese, fen: newFen })
  currentMoveIndex.value = historyMoves.value.length

  triggerPikafishAnalyze()

  // 引擎出招判定
  checkEngineAutoMove()
}

function checkEngineAutoMove() {
  if (engineSide.value && engineSide.value === currentTurn.value) {
    setTimeout(() => {
      triggerEngineBestMove()
    }, 600)
  }
}

// 调度皮卡鱼后端接口分析
async function triggerPikafishAnalyze() {
  isAnalyzing.value = true
  try {
    const resp = await axios.post('http://localhost:8080/api/chess/analyze', {
      fen: currentFen.value,
      depth: searchDepth.value,
      movetime: searchMovetime.value
    })
    engineResult.value = resp.data
  } catch (err) {
    console.error('皮卡鱼分析接口调用失败:', err)
  } finally {
    isAnalyzing.value = false
  }
}

async function triggerEngineBestMove() {
  isAnalyzing.value = true
  try {
    const resp = await axios.post('http://localhost:8080/api/chess/analyze', {
      fen: currentFen.value,
      depth: searchDepth.value,
      movetime: searchMovetime.value
    })
    engineResult.value = resp.data

    const best = engineResult.value?.bestMove
    if (!best || best.length < 4) {
      ElMessage.warning('未能计算出最佳着法')
      return
    }

    const fc = best.charCodeAt(0) - 97
    const fr = parseInt(best[1], 10)
    const tc = best.charCodeAt(2) - 97
    const tr = parseInt(best[3], 10)

    const piece = boardState.value[fr]?.[fc]
    if (!piece) {
      console.error('引擎走子起点为空:', best, 'FEN:', currentFen.value)
      return
    }

    executeMove({ r: fr, c: fc }, { r: tr, c: tc })
  } catch (err) {
    console.error('引擎出招异常:', err)
  } finally {
    isAnalyzing.value = false
  }
}

function toggleEngineSide(side) {
  if (engineSide.value === side) {
    engineSide.value = null
  } else {
    engineSide.value = side
    if (engineSide.value === currentTurn.value) {
      triggerEngineBestMove()
    }
  }
}

function stepMove(delta) {
  const target = currentMoveIndex.value + delta
  jumpToMove(target)
}

function jumpToMove(index) {
  if (index < 0 || index > historyMoves.value.length) return
  currentMoveIndex.value = index

  const fen = index === 0 ? INITIAL_FEN : historyMoves.value[index - 1].fen
  currentFen.value = fen
  const parsed = parseFen(fen)
  boardState.value = parsed.board
  currentTurn.value = parsed.turn

  selectedPiecePos.value = null
  legalMoves.value = []

  if (index > 0) {
    const lastMoveItem = historyMoves.value[index - 1]
    const uci = lastMoveItem.uci
    if (uci && uci.length >= 4) {
      const fc = uci.charCodeAt(0) - 97
      const fr = parseInt(uci[1], 10)
      const tc = uci.charCodeAt(2) - 97
      const tr = parseInt(uci[3], 10)
      lastMoveHighlight.value = { from: { r: fr, c: fc }, to: { r: tr, c: tc } }
    } else {
      lastMoveHighlight.value = null
    }
  } else {
    lastMoveHighlight.value = null
  }

  triggerPikafishAnalyze()
}

function handleNewGame() {
  currentFen.value = INITIAL_FEN
  boardState.value = parseFen(INITIAL_FEN).board
  currentTurn.value = 'r'
  historyMoves.value = []
  currentMoveIndex.value = 0
  selectedPiecePos.value = null
  legalMoves.value = []
  lastMoveHighlight.value = null
  triggerPikafishAnalyze()
  ElMessage.success('已开启全新棋局')
}

function handleAskCoach() {
  if (aiCoachRef.value) {
    aiCoachRef.value.requestAnalysis('请结合皮卡鱼的建议，为我深度复盘当前局面的战术焦点与破局方案。')
  }
}

function copyFen() {
  navigator.clipboard.writeText(currentFen.value)
  ElMessage.success('FEN 已复制到剪贴板')
}
</script>

<style lang="scss" scoped>
.pakafish-app-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f2f3f5;
  overflow: hidden;
}

.top-toolbar {
  height: 52px;
  background: #ffffff;
  border-bottom: 1px solid #e2e4e8;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  z-index: 10;

  .logo-area {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: bold;
    color: #1f2329;
    .logo-icon {
      font-size: 22px;
    }
  }

  .toolbar-buttons {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .depth-setting-group {
    display: flex;
    align-items: center;
  }
}

.depth-popover-content {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .popover-title {
    font-size: 13px;
    font-weight: bold;
    color: #1f2329;
  }

  .preset-radios {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  .custom-slider-box {
    background: #f7f8fa;
    padding: 8px 12px;
    border-radius: 6px;
    .slider-label {
      font-size: 12px;
      color: #606266;
      margin-bottom: 4px;
      strong { color: #409eff; }
    }
  }

  .tip-text {
    font-size: 11.5px;
    color: #909399;
    line-height: 1.4;
  }
}

.main-workspace {
  flex: 1;
  display: flex;
  padding: 14px;
  gap: 14px;
  min-height: 0;
  box-sizing: border-box;
}

// 左列
.left-board-col {
  width: 580px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;

  .step-controls {
    display: flex;
    align-items: center;
    gap: 8px;
    background: #ffffff;
    padding: 6px 14px;
    border-radius: 6px;
    border: 1px solid #ebeef5;
    width: 100%;
    max-width: 580px;
    box-sizing: border-box;
    justify-content: center;

    .step-text {
      font-size: 13px;
      font-weight: bold;
      color: #606266;
      margin: 0 6px;
    }
  }

  .eval-bar-card {
    width: 100%;
    max-width: 580px;
    background: #ffffff;
    padding: 10px 14px;
    border-radius: 6px;
    border: 1px solid #ebeef5;
    box-sizing: border-box;

    .eval-info {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      font-size: 13.5px;

      .turn-tag {
        font-weight: bold;
        &.r { color: #f56c6c; }
        &.b { color: #303133; }
      }
      .score-text {
        font-weight: bold;
        font-size: 15px;
        &.text-red { color: #f56c6c !important; }
        &.text-black { color: #1d2129 !important; }
        &.text-balance { color: #e6a23c !important; }
      }
      .status-desc {
        color: #909399;
        font-size: 12.5px;
      }
    }

    .advantage-bar-wrapper {
      height: 10px;
      background: #e4e7ed;
      border-radius: 5px;
      overflow: hidden;
      display: flex;
      align-items: center;
      .advantage-bar {
        height: 100%;
        transition: width 0.3s ease, background-color 0.3s ease;
      }
    }
  }
}

// 中列：着法列表
.center-move-tree {
  width: 200px;
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: all 0.3s ease;

  .move-tree-header {
    padding: 12px 14px;
    border-bottom: 1px solid #f0f2f5;
    display: flex;
    justify-content: space-between;
    align-items: center;
    .title {
      font-weight: 600;
      font-size: 14px;
      color: #303133;
    }
  }

  .move-list {
    flex: 1;
    overflow-y: auto;
    padding: 6px 0;

    .move-pair-row {
      display: flex;
      align-items: center;
      padding: 6px 12px;
      font-size: 13px;
      cursor: pointer;
      border-radius: 4px;
      margin: 2px 6px;

      &:hover {
        background: #f5f7fa;
      }
      &.active {
        background: #ecf5ff;
      }

      .round-num {
        width: 32px;
        color: #909399;
      }
      .move-step {
        flex: 1;
        padding: 2px 4px;
        border-radius: 4px;

        &.red { color: #f56c6c; }
        &.black { color: #303133; }

        &.selected {
          background: #409eff;
          color: #ffffff;
          font-weight: bold;
        }
      }
    }

    .empty-hint {
      text-align: center;
      color: #c0c4cc;
      font-size: 12px;
      padding: 30px 10px;
    }
  }
}

// 右列
.right-ai-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 380px;
  min-height: 0;

  .coach-chat-wrap {
    flex: 1;
    min-height: 0;
  }

  .engine-panel-card {
    background: #ffffff;
    border-radius: 8px;
    border: 1px solid #ebeef5;
    padding: 14px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    transition: all 0.3s ease;

    .engine-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 14px;
      font-weight: 600;
      color: #303133;

      .header-right {
        display: flex;
        align-items: center;
      }

      .analyzing-spin {
        font-size: 12px;
        color: #409eff;
      }
    }

    .engine-data-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 10px;
      background: #fafafa;
      padding: 10px;
      border-radius: 6px;

      .data-item {
        display: flex;
        flex-direction: column;
        gap: 2px;
        .label {
          font-size: 12px;
          color: #909399;
        }
        .val {
          font-size: 15px;
          font-weight: bold;
          color: #303133;
          &.highlight {
            color: #409eff;
          }
          &.text-red { color: #f56c6c; }
          &.text-black { color: #303133; }
          &.text-balance { color: #e6a23c; }
        }
        .sub-val {
          font-size: 11px;
          color: #c0c4cc;
        }
      }
    }

    .pv-line-box {
      font-size: 13px;
      line-height: 1.6;
      background: #fdf6ec;
      border: 1px solid #faecd8;
      border-radius: 6px;
      padding: 8px 12px;

      .pv-label {
        color: #e6a23c;
        font-weight: bold;
      }
      .pv-content {
        color: #606266;
      }
    }
  }
}
</style>
