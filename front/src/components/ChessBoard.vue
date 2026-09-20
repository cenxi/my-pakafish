<template>
  <div class="chess-board-wrapper" :class="{ flipped: isFlipped }">
    <!-- 棋盘主网格背景 -->
    <div class="chess-board">
      <!-- 楚河汉界标注 -->
      <div class="river-text" :class="{ flipped: isFlipped }">
        <span class="chu">楚 河</span>
        <span class="han">漢 界</span>
      </div>

      <!-- 棋盘 SVG 线格绘制 -->
      <svg class="board-lines" viewBox="0 0 900 1000">
        <!-- 外边框 -->
        <rect x="50" y="50" width="800" height="900" fill="none" stroke="#684a28" stroke-width="4" />
        <rect x="42" y="42" width="816" height="916" fill="none" stroke="#684a28" stroke-width="1.5" />

        <!-- 横线 (0~9 共10条) -->
        <line v-for="i in 10" :key="'h' + i"
          :x1="50" :y1="50 + (i - 1) * 100"
          :x2="850" :y2="50 + (i - 1) * 100"
          stroke="#684a28" stroke-width="2" />

        <!-- 纵线 (红黑两侧，楚河汉界隔开) -->
        <!-- 两侧贯通边线 -->
        <line :x1="50" :y1="50" :x2="50" :y2="950" stroke="#684a28" stroke-width="2" />
        <line :x1="850" :y1="50" :x2="850" :y2="950" stroke="#684a28" stroke-width="2" />

        <!-- 内部7条竖线 (被楚河断开) -->
        <template v-for="i in 7" :key="'v' + i">
          <!-- 上方 (4条格) -->
          <line :x1="50 + i * 100" :y1="50" :x2="50 + i * 100" :y2="450" stroke="#684a28" stroke-width="2" />
          <!-- 下方 (4条格) -->
          <line :x1="50 + i * 100" :y1="550" :x2="50 + i * 100" :y2="950" stroke="#684a28" stroke-width="2" />
        </template>

        <!-- 九宫斜线 -->
        <!-- 上九宫 (黑方将府) -->
        <line x1="350" y1="50" x2="550" y2="250" stroke="#684a28" stroke-width="2" />
        <line x1="550" y1="50" x2="350" y2="250" stroke="#684a28" stroke-width="2" />
        <!-- 下九宫 (红方帅府) -->
        <line x1="350" y1="750" x2="550" y2="950" stroke="#684a28" stroke-width="2" />
        <line x1="550" y1="750" x2="350" y2="950" stroke="#684a28" stroke-width="2" />

        <!-- 推荐着法高亮指示箭头 (若有皮卡鱼分析) -->
        <g v-if="suggestMoveCoord" class="suggest-arrow">
          <line :x1="suggestMoveCoord.x1" :y1="suggestMoveCoord.y1"
                :x2="suggestMoveCoord.x2" :y2="suggestMoveCoord.y2"
                stroke="#409EFF" stroke-width="6" stroke-linecap="round" marker-end="url(#arrow)" />
        </g>
        <defs>
          <marker id="arrow" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#409EFF" />
          </marker>
        </defs>
      </svg>

      <!-- 棋子与交互格定位网格 -->
      <div class="pieces-grid">
        <template v-for="(row, r) in 10" :key="'row' + r">
          <template v-for="(col, c) in 9" :key="'cell' + r + '-' + c">
            <div
              class="board-cell"
              :style="getCellStyle(r, c)"
              @click="handleCellClick(r, c)"
            >
              <!-- 走子落点合法高亮小绿圈 -->
              <div v-if="isLegalMove(r, c)" class="legal-dot"></div>

              <!-- 上一步走棋轨迹标记 -->
              <div v-if="isLastMoveCell(r, c)" class="last-move-highlight"></div>

              <!-- 选中的棋子高亮圈 -->
              <div v-if="isSelectedCell(r, c)" class="selected-ring"></div>

              <!-- 棋子本身 -->
              <div
                v-if="getPiece(r, c)"
                class="chess-piece"
                :class="[getPiece(r, c).color, { selected: isSelectedCell(r, c) }]"
              >
                <div class="piece-inner">
                  <span class="piece-char">{{ getPieceName(getPiece(r, c)) }}</span>
                </div>
              </div>
            </div>
          </template>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getPieceName } from '@/utils/chessEngine'

const props = defineProps({
  board: { type: Array, required: true },
  isFlipped: { type: Boolean, default: false },
  selectedPos: { type: Object, default: null },
  legalMoves: { type: Array, default: () => [] },
  lastMove: { type: Object, default: null }, // { from: {r,c}, to: {r,c} }
  suggestMoveUci: { type: String, default: '' } // 如 b2e2
})

const emit = defineEmits(['cell-click'])

function handleCellClick(r, c) {
  const actualR = props.isFlipped ? r : (9 - r)
  const actualC = props.isFlipped ? (8 - c) : c
  emit('cell-click', { r: actualR, c: actualC })
}

function getPiece(displayR, displayC) {
  const actualR = props.isFlipped ? displayR : (9 - displayR)
  const actualC = props.isFlipped ? (8 - displayC) : displayC
  return props.board[actualR]?.[actualC] || null
}

function isSelectedCell(displayR, displayC) {
  if (!props.selectedPos) return false
  const actualR = props.isFlipped ? displayR : (9 - displayR)
  const actualC = props.isFlipped ? (8 - displayC) : displayC
  return props.selectedPos.r === actualR && props.selectedPos.c === actualC
}

function isLegalMove(displayR, displayC) {
  const actualR = props.isFlipped ? displayR : (9 - displayR)
  const actualC = props.isFlipped ? (8 - displayC) : displayC
  return props.legalMoves.some(m => m.r === actualR && m.c === actualC)
}

function isLastMoveCell(displayR, displayC) {
  if (!props.lastMove) return false
  const actualR = props.isFlipped ? displayR : (9 - displayR)
  const actualC = props.isFlipped ? (8 - displayC) : displayC
  return (
    (props.lastMove.from.r === actualR && props.lastMove.from.c === actualC) ||
    (props.lastMove.to.r === actualR && props.lastMove.to.c === actualC)
  )
}

function getCellStyle(displayR, displayC) {
  return {
    top: `${(displayR * 100) / 10}%`,
    left: `${(displayC * 100) / 9}%`,
    width: `${100 / 9}%`,
    height: `${100 / 10}%`
  }
}

// 计算建议箭头坐标 (在 viewBox 0~900, 0~1000 中)
const suggestMoveCoord = computed(() => {
  if (!props.suggestMoveUci || props.suggestMoveUci.length < 4) return null
  const fc = props.suggestMoveUci.charCodeAt(0) - 97
  const fr = parseInt(props.suggestMoveUci[1], 10)
  const tc = props.suggestMoveUci.charCodeAt(2) - 97
  const tr = parseInt(props.suggestMoveUci[3], 10)

  const getPos = (r, c) => {
    const dispR = props.isFlipped ? r : (9 - r)
    const dispC = props.isFlipped ? (8 - c) : c
    return {
      x: 50 + dispC * 100,
      y: 50 + dispR * 100
    }
  }

  const p1 = getPos(fr, fc)
  const p2 = getPos(tr, tc)
  return { x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y }
})
</script>

<style lang="scss" scoped>
.chess-board-wrapper {
  user-select: none;
  width: 100%;
  max-width: 580px;
  aspect-ratio: 9 / 10;
  padding: 12px;
  background: #deb887;
  border-radius: 8px;
  box-shadow: inset 0 0 10px rgba(0, 0, 0, 0.4), 0 8px 24px rgba(0, 0, 0, 0.25);
  box-sizing: border-box;
  display: flex;
  position: relative;
}

.chess-board {
  position: relative;
  width: 100%;
  height: 100%;
  background: #f4d090;
  border: 3px solid #684a28;
  border-radius: 4px;
}

.river-text {
  position: absolute;
  top: 45%;
  left: 0;
  width: 100%;
  height: 10%;
  display: flex;
  justify-content: space-around;
  align-items: center;
  font-family: 'Kaiti', 'STKaiti', serif;
  font-size: 26px;
  font-weight: 600;
  color: #7a5833;
  pointer-events: none;
  z-index: 1;

  &.flipped {
    transform: rotate(180deg);
  }
}

.board-lines {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.pieces-grid {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.board-cell {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 2;
}

.legal-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #67c23a;
  box-shadow: 0 0 8px #67c23a;
  z-index: 5;
  pointer-events: none;
}

.last-move-highlight {
  position: absolute;
  width: 82%;
  height: 82%;
  border-radius: 50%;
  border: 2px dashed #409eff;
  background: rgba(64, 158, 255, 0.15);
  pointer-events: none;
}

.selected-ring {
  position: absolute;
  width: 90%;
  height: 90%;
  border-radius: 50%;
  border: 3px solid #e6a23c;
  box-shadow: 0 0 10px #e6a23c;
  pointer-events: none;
  z-index: 4;
}

.chess-piece {
  width: 84%;
  height: 84%;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.35), inset 0 2px 3px rgba(255, 255, 255, 0.6);
  transition: transform 0.15s ease;
  z-index: 3;

  &.selected {
    transform: scale(1.08);
  }

  .piece-inner {
    width: 84%;
    height: 84%;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1.5px solid;
  }

  .piece-char {
    font-family: 'Kaiti', 'STKaiti', serif;
    font-size: 24px;
    font-weight: bold;
    user-select: none;
  }

  &.r {
    background: radial-gradient(circle at 35% 35%, #fff0f0, #e64545 65%, #991b1b);
    .piece-inner {
      border-color: #ffe4e4;
    }
    .piece-char {
      color: #fff;
      text-shadow: 0 1px 2px #7f1d1d;
    }
  }

  &.b {
    background: radial-gradient(circle at 35% 35%, #555555, #222222 70%, #000000);
    .piece-inner {
      border-color: #888888;
    }
    .piece-char {
      color: #ffffff;
      text-shadow: 0 1px 2px #000;
    }
  }
}
</style>
