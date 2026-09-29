<template>
  <div class="pakafish-app-layout">
    <!-- 1. 顶部操作工具栏 -->
    <header class="top-toolbar">
      <div class="logo-area">
        <div class="wuhan-seal-logo-pc">楚</div>
        <div class="logo-text-group">
          <span class="logo-title">楚赢象棋</span>
          <span class="logo-badge-text">特级大师私教</span>
        </div>
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
            :type="isSandboxMode ? 'warning' : 'default'"
            @click="toggleSandboxMode"
          >
            {{ isSandboxMode ? '退出试拆' : '试拆/分析' }}
          </el-button>
          <el-button
            :type="isAnalysisMode ? 'primary' : 'default'"
            @click="toggleAnalysisMode"
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
                <div class="slider-row">
                  <div class="slider-label">
                    <span>目标深度：<strong>{{ searchDepth }}</strong> 层 (最高支持 128 层极限深算)</span>
                  </div>
                  <el-slider v-model="searchDepth" :min="10" :max="128" :step="2" show-input @change="triggerPikafishAnalyze" />
                </div>
                <div class="slider-row mt-2">
                  <div class="slider-label">
                    <span>思考限时：<strong>{{ searchMovetimeSec }}</strong> 秒</span>
                  </div>
                  <el-slider
                    v-model="searchMovetimeSec"
                    :min="0.5"
                    :max="10"
                    :step="0.5"
                    show-input
                    :format-tooltip="val => val + '秒'"
                    @change="onMovetimeSecChange"
                  />
                </div>
              </div>
              <div class="tip-text">深度越深，皮卡鱼对长变例和绝杀的算力越强。</div>
            </div>
          </el-popover>
        </div>

        <!-- 开局定式谱与开局库开关 -->
        <div class="opening-setting-group ml-2">
          <el-popover placement="bottom" :width="320" trigger="click">
            <template #reference>
              <el-button size="small" :type="useOpeningBook ? 'success' : 'info'" plain :icon="Reading">
                开局: {{ selectedOpeningName }} ({{ useOpeningBook ? '库开' : '库关' }})
              </el-button>
            </template>
            <div class="depth-popover-content">
              <div class="popover-title flex-between">
                <span>中国象棋开局库设置</span>
                <el-switch
                  v-model="useOpeningBook"
                  active-text="启用"
                  inactive-text="关闭"
                  inline-prompt
                  @change="onOpeningBookToggle"
                />
              </div>
              <div class="tip-text mb-2">启用后开局阶段毫秒级秒出正着；关闭后全部由皮卡鱼引擎深算。</div>
              <div class="popover-subtitle">快捷选择经典开局定式：</div>
              <div class="opening-list-scroll">
                <div
                  v-for="item in openingPresets"
                  :key="item.id"
                  class="opening-item"
                  :class="{ active: selectedOpeningId === item.id }"
                  @click="applyOpeningPreset(item)"
                >
                  <div class="opening-item-title">
                    <strong>{{ item.name }}</strong>
                    <el-tag size="small" effect="plain">{{ item.category }}</el-tag>
                  </div>
                  <div class="opening-item-desc">{{ item.description }}</div>
                  <div class="opening-item-moves">走法: {{ item.movesChinese.join(' ') }}</div>
                </div>
              </div>
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
          <el-tooltip content="体验全新移动端 / 手机界面" placement="bottom">
            <el-button
              size="small"
              type="warning"
              plain
              @click="$router.push('/mobile')"
            >
              📱 手机版
            </el-button>
          </el-tooltip>
        </el-button-group>
      </div>
    </header>

    <!-- 2. 主体工作区 -->
    <div class="main-workspace">
      <!-- 左列：棋盘 + 步进控制 + 局势优劣条 + 绝杀提示 (移至下方) -->
      <section class="left-board-col">
        <ChessBoard
          :board="boardState"
          :is-flipped="isFlipped"
          :selected-pos="selectedPiecePos"
          :legal-moves="legalMoves"
          :last-move="lastMoveHighlight"
          :suggest-move-uci="isAnalysisMode ? engineResult?.bestMove : ''"
          :variation-arrows="activeVariationArrows"
          @cell-click="onBoardCellClick"
        />

        <!-- 分支推演演播控制器 (推演变例时弹出，可随时逐步前进、后退、或一键还原原盘面) -->
        <transition name="el-zoom-in-top">
          <div v-if="isVariationMode" class="variation-player-bar">
            <div class="player-left">
              <span class="player-tag">演进推演中</span>
              <span class="player-progress">步骤 {{ variationStepIndex }} / {{ variationSteps.length }}</span>
            </div>
            <div class="player-controls">
              <el-button size="small" :disabled="variationStepIndex <= 0" @click="stepVariation(-1)">〈 回退一步</el-button>
              <el-button size="small" type="primary" :disabled="variationStepIndex >= variationSteps.length" @click="stepVariation(1)">走下一步 〉</el-button>
              <el-button size="small" type="danger" plain @click="exitVariationMode">❌ 退出并还原原局面</el-button>
            </div>
          </div>
        </transition>

        <!-- 试拆模式提示条 -->
        <transition name="el-zoom-in-top">
          <div v-if="isSandboxMode" class="sandbox-banner">
            <div class="banner-left">
              <span class="sandbox-tag">🧪 试拆分析中</span>
              <span class="sandbox-desc">当前所有落子仅用于推演，不影响原棋谱</span>
            </div>
            <el-button size="small" type="warning" plain @click="toggleSandboxMode">还原并退出试拆</el-button>
          </div>
        </transition>

        <!-- 步进控制按钮 -->
        <div class="step-controls" v-show="!isVariationMode">
          <el-button size="small" :disabled="currentMoveIndex <= 0" @click="jumpToMove(0)">《 开始</el-button>
          <el-button size="small" :disabled="currentMoveIndex <= 0" @click="stepMove(-1)">〈 上一步</el-button>
          <span class="step-text">{{ currentMoveIndex }} / {{ historyMoves.length }}</span>
          <el-button size="small" :disabled="currentMoveIndex >= historyMoves.length" @click="stepMove(1)">下一步 〉</el-button>
          <el-button size="small" :disabled="currentMoveIndex >= historyMoves.length" @click="jumpToMove(historyMoves.length)">最新 》</el-button>
        </div>

        <!-- 局势评估条：纯展示 红优/黑优多少分 (分析模式关闭时隐藏透视) -->
        <div class="eval-bar-card">
          <div class="eval-info">
            <span class="turn-tag" :class="currentTurn">轮到{{ currentTurn === 'r' ? '红方' : '黑方' }}走</span>
            <span class="score-text" :class="isAnalysisMode ? advantageClass : 'text-hidden'">
              {{ isAnalysisMode ? advantageLabel : '实战盲局中' }}
            </span>
            <span class="status-desc">
              {{ isAnalysisMode ? (engineResult?.advantageDescription || '正在计算局势...') : '分析模式已关闭' }}
            </span>
          </div>
          <!-- 局势平衡度指示条：中立50，红优向右红，黑优向左黑 -->
          <div class="advantage-bar-wrapper">
            <div
              class="advantage-bar"
              :style="isAnalysisMode ? advantageBarStyle : { width: '50%', backgroundColor: '#c0c4cc' }"
            ></div>
          </div>
        </div>

        <!-- 绝杀提示横幅 (放置在左列最下方，绝不顶起棋盘和控制条) -->
        <transition name="el-zoom-in-bottom">
          <div v-if="gameOverInfo" class="checkmate-banner">
            <span class="banner-icon">⚔️</span>
            <span class="banner-text">{{ gameOverInfo }}</span>
            <el-button size="small" type="danger" round @click="handleNewGame">再来一局</el-button>
          </div>
        </transition>
      </section>

      <!-- 中列：着法记录树 (可收起) -->
      <section v-show="showMoveTree" class="center-move-tree">
        <div class="move-tree-header">
          <span class="title">对局着法谱</span>
          <el-button size="small" text type="primary" @click="copyFen">复制FEN</el-button>
        </div>
        <div ref="moveListRef" class="move-list">
          <div
            v-for="(move, idx) in movePairs"
            :key="idx"
            :ref="el => setMoveRowRef(el, idx)"
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
            :history-text="historyFullText"
            :engine-analysis="engineResult"
            :history-moves="historyMoves"
            @jump-step="onCoachJumpStep"
            @play-variation="onCoachPlayVariation"
          />
        </div>

        <!-- 右下：皮卡鱼引擎分析实时面板 (可折叠收缩) -->
        <div v-show="showEnginePanel && isAnalysisMode" class="engine-panel-card">
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
              <el-tag v-if="engineResult?.fromBook" size="small" type="success" class="ml-1" effect="dark">
                开局库
              </el-tag>
              <el-tag v-else size="small" type="primary" class="ml-1" effect="plain">
                皮卡鱼纯算
              </el-tag>
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
            <span class="pv-label">{{ engineResult?.fromBook ? '开局库推荐变例：' : '推荐路线推演：' }}</span>
            <span class="pv-content" v-if="!engineResult?.fromBook">
              {{ (engineResult?.pvMovesChinese || []).join(' → ') || '等待计算' }}
            </span>
            <span class="pv-content" v-else>
              <template v-for="(bm, bidx) in (engineResult?.bookMoves || []).slice(0, 5)" :key="bm.uci">
                <el-tag size="small" type="info" class="mr-1 mb-1">
                  {{ bm.chinese }} ({{ bm.uci }}) 权重:{{ bm.weight }}
                </el-tag>
              </template>
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
  Setting,
  Reading
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
  uciToChinese,
  isCheckmate,
  isKingInCheck
} from '@/utils/chessEngine'

// 状态
const currentFen = ref(INITIAL_FEN)
const boardState = ref(parseFen(INITIAL_FEN).board)
const currentTurn = ref('r')
const isFlipped = ref(false)
const isAnalysisMode = ref(true)
const isSandboxMode = ref(false) // 试拆/沙盒模式
const sandboxSnapshot = ref(null) // 试拆开始时的原始盘面快照
const engineSide = ref(null) // 'r', 'b', null
const isAnalyzing = ref(false)
const gameOverInfo = ref('') // 绝杀局提示信息

// 开局库控制与定式谱状态
const useOpeningBook = ref(true)
const selectedOpeningId = ref('free')
const selectedOpeningName = ref('自由对局')
const openingPresets = ref([
  {
    id: 'free',
    name: '自由对局 (标准初始)',
    category: '标准局',
    description: '从象棋初始局面开始，开局库根据你的落子自动匹配全部经典变例。',
    movesChinese: [],
    movesUci: [],
    fen: INITIAL_FEN
  }
])

async function fetchOpeningPresets() {
  try {
    const resp = await axios.get('${getApiBaseUrl()}/api/chess/opening-presets')
    if (resp.data && resp.data.length > 0) {
      openingPresets.value = [
        {
          id: 'free',
          name: '自由对局 (标准初始)',
          category: '标准局',
          description: '从象棋初始局面开始，开局库根据你的落子自动匹配全部经典变例。',
          movesChinese: [],
          movesUci: [],
          fen: INITIAL_FEN
        },
        ...resp.data
      ]
    }
  } catch (err) {
    console.warn('获取开局定式目录失败，使用默认配置:', err)
  }
}

function onOpeningBookToggle(val) {
  ElMessage.info(val ? '已开启开局库（优先秒出经典定式）' : '已关闭开局库（完全由皮卡鱼引擎深算）')
  triggerPikafishAnalyze()
}

function applyOpeningPreset(preset) {
  selectedOpeningId.value = preset.id
  selectedOpeningName.value = preset.name

  // 重置棋盘至定式局面
  currentFen.value = preset.fen
  const parsed = parseFen(preset.fen)
  boardState.value = parsed.board
  currentTurn.value = parsed.turn

  // 构建历史步列表
  const newHistory = []
  if (preset.movesUci && preset.movesUci.length > 0) {
    let simFen = INITIAL_FEN
    for (let i = 0; i < preset.movesUci.length; i++) {
      const uci = preset.movesUci[i]
      const ch = preset.movesChinese[i] || uci
      newHistory.push({
        uci,
        chinese: ch,
        fen: (i === preset.movesUci.length - 1) ? preset.fen : simFen
      })
    }
  }
  historyMoves.value = newHistory
  currentMoveIndex.value = newHistory.length

  selectedPiecePos.value = null
  legalMoves.value = []
  lastMoveHighlight.value = null
  gameOverInfo.value = ''

  ElMessage.success(`已摆出经典开局：${preset.name}`)
  triggerPikafishAnalyze()
}

// 导师复盘点击历史步跳转 (如点击 "第3回合" -> 跳到第 3 回合)
function onCoachJumpStep(stepNum) {
  // 如果当前正处于推演模式，必须先干净退出并还原盘面，防止推演残留状态与历史步冲突
  if (isVariationMode.value) {
    exitVariationMode()
  }

  // 计算对应回合的步数索引：第 N 回合通常对应该回合红方走棋之后或黑方走棋之后
  // 象棋一回合包含红黑各一步：第 N 回合红方是 (N-1)*2+1，黑方是 N*2
  let targetIndex = (stepNum - 1) * 2 + 1
  if (targetIndex > historyMoves.value.length) {
    targetIndex = historyMoves.value.length
  }
  if (targetIndex < 1) targetIndex = 1

  jumpToMove(targetIndex)
  ElMessage.info(`已回溯至第 ${stepNum} 回合盘面`)
}

// 分支推演状态机
const isVariationMode = ref(false)
const variationSteps = ref([])
const variationStepIndex = ref(0)
const savedSnapshotBeforeVariation = ref(null)
const activeVariationArrows = ref([])

// 导师复盘点击变例推演播放器 (如点击 "▶ 炮二平五 → 马8进7 → 车一平二")
function onCoachPlayVariation(variationText) {
  // 如果已经在推演模式中，先退出上一个推演并回到基准盘面
  if (isVariationMode.value) {
    exitVariationMode()
  }

  // 过滤掉开头结尾的引号、符号与“演进推演”文字
  let clean = variationText.replace(/[“”（）()▶►]/g, '').replace(/演进推演/g, '').trim()
  const rawMoves = clean.split('→').map(m => m.trim()).filter(Boolean)
  if (rawMoves.length === 0) return

  // 1. 保存进入推演前的原始盘面快照
  savedSnapshotBeforeVariation.value = {
    fen: currentFen.value,
    moveIndex: currentMoveIndex.value,
    history: [...historyMoves.value],
    lastHighlight: lastMoveHighlight.value
  }

  // 2. 模拟试走并计算全部步骤的起点和终点
  const parsedSteps = []
  const arrowList = []
  let simBoard = parseFen(currentFen.value).board
  let simTurn = parseFen(currentFen.value).turn

  for (const chMove of rawMoves) {
    let legalFound = findMoveByChinese(simBoard, simTurn, chMove)

    // 智能容错：如果未直接匹配到（例如棋评省略了对方的中间过渡棋步），自动从历史棋谱中查找这一步并补全
    if (!legalFound && historyMoves.value && historyMoves.value.length > 0) {
      // 尝试在历史对局中查找该中文招法
      for (let hIdx = 0; hIdx < historyMoves.value.length; hIdx++) {
        const hItem = historyMoves.value[hIdx]
        if (hItem.chinese === chMove || hItem.chinese.replace(/\s+/g, '') === chMove.replace(/\s+/g, '')) {
          const uci = hItem.uci
          if (uci && uci.length >= 4) {
            const fc = uci.charCodeAt(0) - 97
            const fr = parseInt(uci[1], 10)
            const tc = uci.charCodeAt(2) - 97
            const tr = parseInt(uci[3], 10)
            const p = simBoard[fr]?.[fc]
            legalFound = {
              from: { r: fr, c: fc },
              to: { r: tr, c: tc },
              turn: p ? p.color : simTurn
            }
            break
          }
        }
      }
    }

    if (legalFound) {
      arrowList.push({ from: legalFound.from, to: legalFound.to })
      parsedSteps.push({
        chinese: chMove,
        from: legalFound.from,
        to: legalFound.to,
        turn: legalFound.turn
      })
      // 推进模拟局面
      const piece = simBoard[legalFound.from.r][legalFound.from.c]
      if (piece) {
        simBoard[legalFound.to.r][legalFound.to.c] = piece
        simBoard[legalFound.from.r][legalFound.from.c] = null
      }
      simTurn = legalFound.turn === 'r' ? 'b' : 'r'
    } else {
      console.warn('分支招法在推演局面中未匹配:', chMove)
    }
  }

  if (arrowList.length === 0) {
    ElMessage.warning('未能匹配到当前局面下的合法走法路线，请先跳转到对应局面再点击推演')
    return
  }

  // 3. 激活推演模式并在棋盘上画出序号 1, 2, 3... 箭头
  isVariationMode.value = true
  variationSteps.value = parsedSteps
  variationStepIndex.value = 0
  activeVariationArrows.value = arrowList

  ElMessage.success(`已在棋盘绘制全部 ${arrowList.length} 步演进路线（标有序号 1, 2...），可点击控制条逐步拆解`)
}

// 逐步演进：前进或后退一步（纯推演沙盒，绝不写入对局真实历史谱）
function stepVariation(delta) {
  const target = variationStepIndex.value + delta
  if (target < 0 || target > variationSteps.value.length) return

  // 每次演进都基于推演开始时的原始盘面重新模拟至目标步
  const baseFen = savedSnapshotBeforeVariation.value.fen
  const p = parseFen(baseFen)
  let curBoard = p.board
  let curTurn = p.turn
  let lastFrom = null
  let lastTo = null

  for (let i = 0; i < target; i++) {
    const s = variationSteps.value[i]
    const piece = curBoard[s.from.r][s.from.c]
    curBoard[s.to.r][s.to.c] = piece
    curBoard[s.from.r][s.from.c] = null
    curTurn = s.turn === 'r' ? 'b' : 'r'
    lastFrom = s.from
    lastTo = s.to
  }

  boardState.value = curBoard
  currentTurn.value = curTurn
  currentFen.value = boardToFen(curBoard, curTurn)
  lastMoveHighlight.value = (lastFrom && lastTo) ? { from: lastFrom, to: lastTo } : null
  selectedPiecePos.value = null
  legalMoves.value = []
  variationStepIndex.value = target

  triggerPikafishAnalyze()
}

// 退出演进推演，一键还原原棋局
function exitVariationMode() {
  if (!savedSnapshotBeforeVariation.value) return
  const snap = savedSnapshotBeforeVariation.value
  historyMoves.value = snap.history
  currentMoveIndex.value = snap.moveIndex
  currentFen.value = snap.fen
  const p = parseFen(snap.fen)
  boardState.value = p.board
  currentTurn.value = p.turn
  lastMoveHighlight.value = snap.lastHighlight
  selectedPiecePos.value = null
  legalMoves.value = []

  // 退出推演状态并清空箭头
  isVariationMode.value = false
  variationSteps.value = []
  variationStepIndex.value = 0
  activeVariationArrows.value = []
  savedSnapshotBeforeVariation.value = null

  triggerPikafishAnalyze()
  ElMessage.info('已退出推演，已完整恢复原始对局与棋谱')
}

// 辅助函数：根据中文招法在当前棋盘中匹配对应的走法（若未指定行棋方，则自动识别红黑双方）
function findMoveByChinese(board, turn, chinese) {
  // 1. 先用期望的行棋方匹配
  const res = tryFindMove(board, turn, chinese)
  if (res) return { ...res, turn }

  // 2. 如果当前方没匹配到，尝试用对方匹配（处理如“车9进1 → 车9平3”这种仅给出单方连续走子的跨回合棋评）
  const oppTurn = turn === 'r' ? 'b' : 'r'
  const oppRes = tryFindMove(board, oppTurn, chinese)
  if (oppRes) return { ...oppRes, turn: oppTurn }

  return null
}

function tryFindMove(board, turn, chinese) {
  for (let r = 0; r < 10; r++) {
    for (let c = 0; c < 9; c++) {
      const p = board[r][c]
      if (p && p.color === turn) {
        const moves = getLegalMoves(board, r, c)
        for (const m of moves) {
          const uci = `${String.fromCharCode(97 + c)}${r}${String.fromCharCode(97 + m.c)}${m.r}`
          const curFen = boardToFen(board, turn)
          const name = uciToChinese(curFen, uci)
          if (name === chinese || name.replace(/\s+/g, '') === chinese.replace(/\s+/g, '')) {
            return { from: { r, c }, to: { r: m.r, c: m.c } }
          }
        }
      }
    }
  }
  return null
}
const showMoveTree = ref(true)
const showEnginePanel = ref(true)

// 动态推演深度与算力档位
const depthPreset = ref('standard')
const searchDepth = ref(20)
const searchMovetimeSec = ref(1.0)
const searchMovetime = ref(1000)

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
  }
  triggerPikafishAnalyze()
}

function onMovetimeSecChange(val) {
  searchMovetime.value = Math.round(val * 1000)
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
const moveListRef = ref(null)
const moveRowRefs = ref({})

function setMoveRowRef(el, idx) {
  if (el) {
    moveRowRefs.value[idx] = el
  }
}

// 自动让着法谱列表平滑滚动到当前所选回合所在位置
function scrollToCurrentMoveRow(index) {
  nextTick(() => {
    if (index <= 0) {
      if (moveListRef.value) {
        moveListRef.value.scrollTo({ top: 0, behavior: 'smooth' })
      }
      return
    }
    const roundIdx = Math.floor((index - 1) / 2)
    const targetEl = moveRowRefs.value[roundIdx]
    if (targetEl && moveListRef.value) {
      targetEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  })
}

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

// 拼接整盘走子历史文本 (如: 1. 炮二平五 马8进7; 2. 兵七进一 ...)
const historyFullText = computed(() => {
  if (historyMoves.value.length === 0) return ''
  const lines = []
  for (let i = 0; i < historyMoves.value.length; i += 2) {
    const round = Math.floor(i / 2) + 1
    const rMove = historyMoves.value[i]?.chinese || ''
    const bMove = historyMoves.value[i + 1]?.chinese || ''
    lines.push(`${round}. ${rMove}  ${bMove}`.trim())
  }
  return lines.join('\n')
})

// 计算明确的红优/黑优多少分
const redScoreCp = computed(() => {
  if (!engineResult.value || engineResult.value.scoreCp === undefined) return 0
  // 后端已经统一归一化为红方基准分 (正为红优，负为黑优)
  return engineResult.value.scoreCp
})

const isRedAdvantage = computed(() => {
  if (engineResult.value?.sideAdvantageText) {
    return engineResult.value.sideAdvantageText.includes('红优')
  }
  return redScoreCp.value > 0
})

const isBlackAdvantage = computed(() => {
  if (engineResult.value?.sideAdvantageText) {
    return engineResult.value.sideAdvantageText.includes('黑优')
  }
  return redScoreCp.value < 0
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

// 平衡指示条样式 (红方在右偏红，黑方在左偏黑)
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

const getApiBaseUrl = () => {
  if (window.location.pathname.startsWith('/chess')) {
    return '/chess'
  }
  const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:'
  const host = window.location.hostname || 'localhost'
  return `${protocol}//${host}:8080`
}

onMounted(() => {
  fetchOpeningPresets()
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

// 切换试拆/沙盒模式
function toggleSandboxMode() {
  if (!isSandboxMode.value) {
    // 开启试拆：记录当前对局的所有快照
    sandboxSnapshot.value = {
      fen: currentFen.value,
      moveIndex: currentMoveIndex.value,
      history: [...historyMoves.value],
      lastHighlight: lastMoveHighlight.value
    }
    isSandboxMode.value = true
    ElMessage.success('已进入【试拆模式】：现在可以随意摆子拆棋，棋谱将完全保留，退出时一键还原！')
  } else {
    // 退出试拆：完整还原原始局面
    if (sandboxSnapshot.value) {
      const snap = sandboxSnapshot.value
      historyMoves.value = snap.history
      currentMoveIndex.value = snap.moveIndex
      currentFen.value = snap.fen
      const p = parseFen(snap.fen)
      boardState.value = p.board
      currentTurn.value = p.turn
      lastMoveHighlight.value = snap.lastHighlight
      selectedPiecePos.value = null
      legalMoves.value = []
      sandboxSnapshot.value = null
    }
    isSandboxMode.value = false
    triggerPikafishAnalyze()
    ElMessage.info('已退出试拆模式，已恢复完整原始棋谱')
  }
}

// 执行走棋
function executeMove(from, to) {
  const piece = boardState.value[from.r][from.c]
  if (!piece) {
    console.warn('executeMove 起点无子', from)
    return
  }

  // 严格校验：落子方必须匹配当前的行棋方 (currentTurn)
  if (piece.color !== currentTurn.value) {
    console.warn('非当前行棋方走子，已拦截:', piece.color, '当前方:', currentTurn.value)
    return
  }

  const uci = `${String.fromCharCode(97 + from.c)}${from.r}${String.fromCharCode(97 + to.c)}${to.r}`
  const chinese = uciToChinese(currentFen.value, uci)

  // 1. 先用当前局面记录
  const moveTurn = currentTurn.value

  // 2. 移动棋子
  boardState.value[to.r][to.c] = piece
  boardState.value[from.r][from.c] = null

  // 3. 严格切换行棋方 (红->黑，黑->红)
  currentTurn.value = moveTurn === 'r' ? 'b' : 'r'
  const newFen = boardToFen(boardState.value, currentTurn.value)
  currentFen.value = newFen
  stopPcSseAnalyze() // 走子先停掉旧 SSE 推演流

  lastMoveHighlight.value = { from, to }

  // 如果是在【试拆模式】下落子，只演练盘面，坚决不修改、不截断原对局棋谱！
  if (isSandboxMode.value) {
    triggerPikafishAnalyze()
    return
  }

  if (currentMoveIndex.value < historyMoves.value.length) {
    historyMoves.value = historyMoves.value.slice(0, currentMoveIndex.value)
  }

  historyMoves.value.push({ uci, chinese, fen: newFen })
  currentMoveIndex.value = historyMoves.value.length
  scrollToCurrentMoveRow(currentMoveIndex.value)

  // 4. 判定是否已进入绝杀局 (胜负已分)
  const isMated = isCheckmate(boardState.value, currentTurn.value)
  if (isMated) {
    const winnerName = moveTurn === 'r' ? '红方' : '黑方'
    const loserName = currentTurn.value === 'r' ? '红方' : '黑方'
    const tip = `绝杀！${winnerName}胜！${loserName}无路可走。`
    gameOverInfo.value = tip
    ElMessage.success({
      message: tip,
      duration: 5000,
      showClose: true
    })
    triggerPikafishAnalyze()
    return // 绝杀后终止引擎走棋
  } else {
    gameOverInfo.value = ''
  }

  // 如果轮到引擎执子，直接让引擎计算并在规定时间内落子
  if (engineSide.value && engineSide.value === currentTurn.value) {
    triggerEngineBestMove()
  } else {
    // 轮到对手走棋（或者双人对弈），引擎不停止分析，通过 SSE 单连接持续接收推演流
    startPcSseAnalyze()
  }
}

// 对手思考时的持续深度推演控制 (采用单连接 SSE 机制，彻底杜绝轮询！)
let pcSseSource = null

function stopPcSseAnalyze() {
  if (pcSseSource) {
    try {
      pcSseSource.close()
    } catch (e) {}
    pcSseSource = null
  }
}

function startPcSseAnalyze() {
  if (!isAnalysisMode.value) return
  stopPcSseAnalyze()

  const fen = currentFen.value
  const targetDepth = searchDepth.value || 20
  const url = `${getApiBaseUrl()}/api/chess/stream-analyze?fen=${encodeURIComponent(fen)}&depth=${targetDepth}&useBook=${useOpeningBook.value}`

  isAnalyzing.value = true
  try {
    pcSseSource = new EventSource(url)

    pcSseSource.addEventListener('analysis', (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data && currentFen.value === fen) {
          engineResult.value = data
        }
      } catch (e) {}
    })

    pcSseSource.onerror = () => {
      stopPcSseAnalyze()
      isAnalyzing.value = false
    }
  } catch (e) {
    isAnalyzing.value = false
  }
}

// 调度皮卡鱼分析
function triggerPikafishAnalyze() {
  startPcSseAnalyze()
}

let isPcEngineMoving = false

async function triggerEngineBestMove() {
  if (isPcEngineMoving) return
  isPcEngineMoving = true

  stopPcSseAnalyze() // 立即中断对手思考推演
  if (currentTurn.value !== (engineSide.value || currentTurn.value)) {
    isPcEngineMoving = false
    return
  }

  isAnalyzing.value = true
  const moveFen = currentFen.value
  try {
    const resp = await axios.post(`${getApiBaseUrl()}/api/chess/analyze`, {
      fen: moveFen,
      depth: searchDepth.value,
      movetime: searchMovetime.value || 1000,
      immediate: true,
      useBook: useOpeningBook.value
    })
    engineResult.value = resp.data

    if (currentFen.value !== moveFen) return

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

    // 严密检查：引擎走出的子必须是当前行棋方 (currentTurn) 的棋子
    if (piece.color !== currentTurn.value) {
      console.warn('引擎计算出的走法与当前行棋方不一致，拒绝执行:', best, '棋子色:', piece.color, '当前方:', currentTurn.value)
      return
    }

    // 落子
    executeMove({ r: fr, c: fc }, { r: tr, c: tc })
  } catch (err) {
    console.error('引擎出招异常:', err)
  } finally {
    isAnalyzing.value = false
    isPcEngineMoving = false
  }
}

function toggleEngineSide(side) {
  if (engineSide.value === side) {
    engineSide.value = null
    stopPcSseAnalyze()
  } else {
    engineSide.value = side
    // 如果当前轮到引擎走棋（例如开局点击“引擎执红”，红方先行），立即在指定时间内出招
    if (engineSide.value === currentTurn.value) {
      triggerEngineBestMove()
    } else {
      // 否则当前是人类思考，引擎立刻开启持续深算
      startPcSseAnalyze()
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

  // 检查当前步是否绝杀
  const isMated = isCheckmate(boardState.value, currentTurn.value)
  if (isMated && index > 0) {
    const winnerName = currentTurn.value === 'r' ? '黑方' : '红方'
    const loserName = currentTurn.value === 'r' ? '红方' : '黑方'
    gameOverInfo.value = `绝杀！${winnerName}胜！${loserName}无路可走。`
  } else {
    gameOverInfo.value = ''
  }

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

  // 联动着法谱滚动条，平滑滚动至可视区域
  scrollToCurrentMoveRow(index)

  triggerPikafishAnalyze()
}

function handleNewGame() {
  gameOverInfo.value = ''
  currentFen.value = INITIAL_FEN
  boardState.value = parseFen(INITIAL_FEN).board
  currentTurn.value = 'r'
  historyMoves.value = []
  currentMoveIndex.value = 0
  selectedPiecePos.value = null
  legalMoves.value = []
  lastMoveHighlight.value = null
  selectedOpeningId.value = 'free'
  selectedOpeningName.value = '自由对局'
  triggerPikafishAnalyze()
  ElMessage.success('已开启全新棋局')
}

function handleAskCoach() {
  if (aiCoachRef.value) {
    aiCoachRef.value.requestAnalysis('请结合皮卡鱼的建议，为我深度复盘当前局面的战术焦点与破局方案。')
  }
}

function toggleAnalysisMode() {
  isAnalysisMode.value = !isAnalysisMode.value
  if (isAnalysisMode.value) {
    ElMessage.success('已开启分析模式：已开启实时选点箭头与局势评分')
    triggerPikafishAnalyze()
  } else {
    ElMessage.info('已关闭分析模式：进入实战盲局对弈，已隐藏走子剧透')
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
  min-height: 52px;
  background: #ffffff;
  border-bottom: 1px solid #e2e4e8;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  padding: 6px 18px;
  row-gap: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  z-index: 10;
  flex-shrink: 0;

  .logo-area {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;

    .wuhan-seal-logo-pc {
      width: 32px;
      height: 32px;
      background: linear-gradient(135deg, #fde047 0%, #f59e0b 55%, #d97706 100%);
      border-radius: 7px;
      border: 1.5px solid #fef08a;
      box-shadow: 0 2px 8px rgba(245, 158, 11, 0.4), inset 0 1px 1px rgba(255, 255, 255, 0.9);
      display: flex;
      align-items: center;
      justify-content: center;
      color: #b91c1c;
      font-size: 18px;
      font-weight: 900;
      font-family: 'STKaiti', 'Kaiti', 'KaiTi_GB2312', serif;
      letter-spacing: -1px;
    }

    .logo-text-group {
      display: flex;
      flex-direction: column;

      .logo-title {
        font-size: 16px;
        font-weight: 800;
        color: #1f2329;
        line-height: 1.2;
      }
      .logo-badge-text {
        font-size: 10px;
        color: #b45309;
        font-weight: 600;
      }
    }
  }

  .toolbar-buttons {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .depth-setting-group,
  .opening-setting-group {
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
    padding: 10px 12px;
    border-radius: 6px;

    .slider-row {
      display: flex;
      flex-direction: column;
      &.mt-2 {
        margin-top: 10px;
        padding-top: 8px;
        border-top: 1px dashed #e4e7ed;
      }
    }

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
  padding: 12px;
  gap: 12px;
  min-height: 0;
  box-sizing: border-box;
  overflow: hidden;
}

// 左列 (根据屏幕高度自适应缩放，保持棋盘完整不变形)
.left-board-col {
  width: min(560px, 42vw, calc((100vh - 180px) * 0.9));
  min-width: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  position: relative;

  .checkmate-banner {
    width: 100%;
    background: linear-gradient(135deg, #ffefe6 0%, #ffe0d1 100%);
    border: 1px solid #f9905c;
    border-radius: 8px;
    padding: 8px 14px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    box-sizing: border-box;
    box-shadow: 0 4px 12px rgba(249, 144, 92, 0.2);

    .banner-icon {
      font-size: 18px;
      margin-right: 6px;
    }
    .banner-text {
      font-weight: bold;
      color: #cf3d00;
      font-size: 13.5px;
      flex: 1;
    }
  }

  // 演进推演条样式
  .variation-player-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: #fdf6ec;
    border: 1px solid #f3d19e;
    border-radius: 6px;
    padding: 6px 12px;
    width: 100%;
    box-sizing: border-box;
    box-shadow: 0 2px 8px rgba(230, 162, 60, 0.2);

    .player-left {
      display: flex;
      align-items: center;
      gap: 8px;

      .player-tag {
        font-size: 11.5px;
        background: #e6a23c;
        color: #ffffff;
        padding: 2px 6px;
        border-radius: 4px;
        font-weight: bold;
      }

      .player-progress {
        font-size: 13px;
        font-weight: bold;
        color: #8c5b00;
      }
    }

    .player-controls {
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }

  .step-controls {
    display: flex;
    align-items: center;
    gap: 6px;
    background: #ffffff;
    padding: 4px 10px;
    border-radius: 6px;
    border: 1px solid #ebeef5;
    width: 100%;
    box-sizing: border-box;
    justify-content: center;

    .step-text {
      font-size: 13px;
      font-weight: bold;
      color: #606266;
      margin: 0 4px;
    }
  }

  .eval-bar-card {
    width: 100%;
    background: #ffffff;
    padding: 8px 12px;
    border-radius: 6px;
    border: 1px solid #ebeef5;
    box-sizing: border-box;

    .eval-info {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 6px;
      font-size: 13px;

      .turn-tag {
        font-weight: bold;
        &.r { color: #f56c6c; }
        &.b { color: #303133; }
      }
      .score-text {
        font-weight: bold;
        font-size: 14.5px;
        &.text-red { color: #f56c6c !important; }
        &.text-black { color: #1d2129 !important; }
        &.text-balance { color: #e6a23c !important; }
        &.text-hidden { color: #909399 !important; }
      }
      .status-desc {
        color: #909399;
        font-size: 12px;
      }
    }

    .advantage-bar-wrapper {
      height: 8px;
      background: #e4e7ed;
      border-radius: 4px;
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
  width: min(200px, 15vw);
  min-width: 150px;
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
  gap: 10px;
  min-width: 320px;
  min-height: 0;
  overflow: hidden;

  .coach-chat-wrap {
    flex: 1 1 0;
    min-height: 0;
    overflow: hidden;
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
    flex-shrink: 0;

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
.flex-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.popover-subtitle {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}

.opening-list-scroll {
  max-height: 280px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;

  .opening-item {
    padding: 8px 10px;
    border-radius: 6px;
    background: #f8fafc;
    border: 1px solid #ebeef5;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      background: #ecf5ff;
      border-color: #b3d8ff;
    }

    &.active {
      background: #f0f9eb;
      border-color: #67c23a;
    }

    .opening-item-title {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 4px;
      font-size: 13px;
      color: #303133;
    }

    .opening-item-desc {
      font-size: 11.5px;
      color: #909399;
      line-height: 1.4;
      margin-bottom: 4px;
    }

    .opening-item-moves {
      font-size: 11px;
      color: #409eff;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}
</style>
