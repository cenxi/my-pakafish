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

        <!-- 推荐着法高亮指示箭头 (若有皮卡鱼单步分析) -->
        <g v-if="suggestMoveCoord && (!variationArrows || variationArrows.length === 0)" class="suggest-arrow">
          <line :x1="suggestMoveCoord.x1" :y1="suggestMoveCoord.y1"
                :x2="suggestMoveCoord.x2" :y2="suggestMoveCoord.y2"
                stroke="#409EFF" stroke-width="6" stroke-linecap="round" marker-end="url(#arrow)" />
        </g>

        <!-- 连续分支演进箭头组（标有 1, 2, 3... 序号） -->
        <g v-if="variationArrows && variationArrows.length > 0" class="variation-arrows-group">
          <template v-for="(arr, idx) in variationArrowCoords" :key="'var-arr-' + idx">
            <!-- 箭头主体线 -->
            <line
              :x1="arr.x1" :y1="arr.y1"
              :x2="arr.x2" :y2="arr.y2"
              :stroke="arr.color"
              stroke-width="5"
              stroke-linecap="round"
              :marker-end="`url(#var-arrow-${idx % 4})`"
            />
            <!-- 序号气泡底圈 -->
            <circle
              :cx="arr.midX" :cy="arr.midY"
              r="14"
              :fill="arr.color"
              stroke="#ffffff"
              stroke-width="2"
              filter="url(#numberShadow)"
            />
            <!-- 序号数字 -->
            <text
              :x="arr.midX" :y="arr.midY + 5"
              fill="#ffffff"
              font-size="14"
              font-weight="bold"
              text-anchor="middle"
              font-family="sans-serif"
            >
              {{ idx + 1 }}
            </text>
          </template>
        </g>

        <defs>
          <marker id="arrow" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#409EFF" />
          </marker>
          <!-- 变例箭头专用多色 marker -->
          <marker id="var-arrow-0" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#e6a23c" />
          </marker>
          <marker id="var-arrow-1" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#f56c6c" />
          </marker>
          <marker id="var-arrow-2" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#67c23a" />
          </marker>
          <marker id="var-arrow-3" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#409eff" />
          </marker>
          <filter id="numberShadow" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="2" stdDeviation="2" flood-color="#000000" flood-opacity="0.4" />
          </filter>
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

              <!-- 选中的棋子高亮圈 -->
              <div v-if="isSelectedCell(r, c)" class="selected-ring"></div>

              <!-- 最新走棋：起点与终点外发光与脉冲光环 (走动标识) -->
              <div v-if="isLastMoveFrom(r, c)" class="move-from-marker"></div>
              <div v-if="isLastMoveTo(r, c)" class="move-to-marker">
                <div class="pulsing-ring"></div>
              </div>

              <!-- 棋子本身 -->
              <div
                v-if="getPiece(r, c)"
                class="chess-piece"
                :class="[getPiece(r, c).color, { selected: isSelectedCell(r, c), 'last-moved': isLastMoveTo(r, c) }]"
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
  suggestMoveUci: { type: String, default: '' }, // 如 b2e2
  variationArrows: { type: Array, default: () => [] } // [ { from: {r,c}, to: {r,c} } ]
})

const emit = defineEmits(['cell-click'])

function handleCellClick(r, c) {
  const actualR = props.isFlipped ? (9 - r) : r
  const actualC = props.isFlipped ? (8 - c) : c
  emit('cell-click', { r: actualR, c: actualC })
}

function getPiece(displayR, displayC) {
  const actualR = props.isFlipped ? (9 - displayR) : displayR
  const actualC = props.isFlipped ? (8 - displayC) : displayC
  return props.board[actualR]?.[actualC] || null
}

function isSelectedCell(displayR, displayC) {
  if (!props.selectedPos) return false
  const actualR = props.isFlipped ? (9 - displayR) : displayR
  const actualC = props.isFlipped ? (8 - displayC) : displayC
  return props.selectedPos.r === actualR && props.selectedPos.c === actualC
}

function isLegalMove(displayR, displayC) {
  const actualR = props.isFlipped ? (9 - displayR) : displayR
  const actualC = props.isFlipped ? (8 - displayC) : displayC
  return props.legalMoves.some(m => m.r === actualR && m.c === actualC)
}

function isLastMoveFrom(displayR, displayC) {
  if (!props.lastMove || !props.lastMove.from) return false
  const actualR = props.isFlipped ? (9 - displayR) : displayR
  const actualC = props.isFlipped ? (8 - displayC) : displayC
  return props.lastMove.from.r === actualR && props.lastMove.from.c === actualC
}

function isLastMoveTo(displayR, displayC) {
  if (!props.lastMove || !props.lastMove.to) return false
  const actualR = props.isFlipped ? (9 - displayR) : displayR
  const actualC = props.isFlipped ? (8 - displayC) : displayC
  return props.lastMove.to.r === actualR && props.lastMove.to.c === actualC
}

function getCellStyle(displayR, displayC) {
  // board offsets: 50px top (5% of 1000), 50px left (5.5556% of 900)
  const topOffset = 5
  const leftOffset = 5.5555555556
  return {
    top: `${topOffset + (displayR * 100) / 10}%`,
    left: `${leftOffset + (displayC * 100) / 9}%`,
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

// 计算连续分支箭头坐标列表 (标号 1, 2, 3...)
const colorPalette = ['#e6a23c', '#f56c6c', '#67c23a', '#409eff', '#909399']
const variationArrowCoords = computed(() => {
  if (!props.variationArrows || props.variationArrows.length === 0) return []
  const getPos = (r, c) => {
    const dispR = props.isFlipped ? r : (9 - r)
    const dispC = props.isFlipped ? (8 - c) : c
    return {
      x: 50 + dispC * 100,
      y: 50 + dispR * 100
    }
  }

  return props.variationArrows.map((mv, idx) => {
    const p1 = getPos(mv.from.r, mv.from.c)
    const p2 = getPos(mv.to.r, mv.to.c)
    const midX = Math.round((p1.x + p2.x) / 2)
    const midY = Math.round((p1.y + p2.y) / 2)
    const color = colorPalette[idx % colorPalette.length]
    return {
      x1: p1.x,
      y1: p1.y,
      x2: p2.x,
      y2: p2.y,
      midX,
      midY,
      color
    }
  })
})
</script>

<style lang="scss" scoped>
.chess-board-wrapper {
  user-select: none;
  width: 100%;
  max-width: 580px;
  aspect-ratio: 9 / 10;
  padding: 10px;
  background-color: #d69f62;
  background-image: 
    radial-gradient(ellipse at 50% 50%, rgba(255,255,255,0.15) 0%, rgba(0,0,0,0.12) 100%),
    repeating-linear-gradient(0deg, rgba(140, 85, 35, 0.05) 0px, rgba(140, 85, 35, 0.05) 1px, transparent 1px, transparent 4px),
    linear-gradient(180deg, #c99355 0%, #deb887 50%, #bd8748 100%);
  border-radius: 12px;
  box-shadow: 
    inset 0 1px 2px rgba(255, 255, 255, 0.6),
    inset 0 -3px 6px rgba(80, 45, 15, 0.4),
    0 12px 36px rgba(0, 0, 0, 0.5),
    0 2px 6px rgba(0, 0, 0, 0.3);
  border: 3.5px solid #75441d;
  box-sizing: border-box;
  display: flex;
  position: relative;
}

.chess-board {
  position: relative;
  width: 100%;
  height: 100%;
  background-color: #ebb87c;
  background-image: 
    radial-gradient(ellipse at 50% 50%, rgba(255,255,255,0.2) 0%, rgba(0,0,0,0.06) 100%),
    repeating-linear-gradient(90deg, rgba(140, 90, 40, 0.03) 0px, rgba(140, 90, 40, 0.03) 1px, transparent 1px, transparent 6px),
    linear-gradient(180deg, #e5b376 0%, #f0c388 50%, #dea968 100%);
  border: 2px solid #825027;
  border-radius: 6px;
  box-shadow: inset 0 0 12px rgba(110, 65, 20, 0.25);
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
  font-family: 'STKaiti', 'Kaiti', 'KaiTi_GB2312', serif;
  font-size: 28px;
  font-weight: 700;
  color: rgba(115, 68, 30, 0.65);
  text-shadow: 1px 1px 1px rgba(255, 255, 255, 0.5);
  pointer-events: none;
  z-index: 1;
  letter-spacing: 16px;

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
  position: absolute;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #67c23a;
  box-shadow: 0 0 8px #67c23a;
  z-index: 5;
  pointer-events: none;
}

.move-from-marker {
  position: absolute;
  width: 82%;
  height: 82%;
  border-radius: 50%;
  border: 2px dashed #e6a23c;
  background: rgba(230, 162, 60, 0.2);
  pointer-events: none;
  z-index: 2;
}

.move-to-marker {
  position: absolute;
  width: 90%;
  height: 90%;
  border-radius: 50%;
  pointer-events: none;
  z-index: 4;

  .pulsing-ring {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    border: 3px solid #409eff;
    box-shadow: 0 0 12px #409eff;
    animation: movePulse 1.8s infinite;
  }
}

@keyframes movePulse {
  0% {
    transform: scale(0.96);
    opacity: 1;
  }
  50% {
    transform: scale(1.06);
    opacity: 0.6;
  }
  100% {
    transform: scale(0.96);
    opacity: 1;
  }
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

/* 拟真实木雕刻棋子 (高保真还原实木立体质感) */
.chess-piece {
  width: 86%;
  height: 86%;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 拟真实木纹理：斜切高光 + 沉重木质软阴影 */
  background: radial-gradient(circle at 35% 30%, #fffbf2 0%, #f6dfb8 42%, #d6a76c 82%, #b58040 100%);
  box-shadow: 
    0 4px 10px rgba(0, 0, 0, 0.45),
    0 1px 3px rgba(0, 0, 0, 0.3),
    inset 0 1.5px 2px rgba(255, 255, 255, 0.85),
    inset 0 -2px 3px rgba(100, 55, 15, 0.5);
  transition: transform 0.15s cubic-bezier(0.34, 1.56, 0.64, 1);
  z-index: 3;

  &.selected {
    transform: scale(1.12);
    box-shadow: 
      0 0 0 2px #ffffff,
      0 0 16px rgba(255, 255, 255, 0.9),
      0 8px 18px rgba(0, 0, 0, 0.55);
  }

  &.last-moved {
    box-shadow: 
      0 0 14px rgba(59, 130, 246, 0.8),
      0 5px 12px rgba(0, 0, 0, 0.4);
  }

  /* 棋子表面凹雕同心圆圈线 */
  .piece-inner {
    width: 80%;
    height: 80%;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px solid rgba(139, 90, 43, 0.4);
    box-shadow: 
      inset 0 0.5px 1px rgba(255, 255, 255, 0.5),
      0 0.5px 1px rgba(0, 0, 0, 0.2);
  }

  .piece-char {
    font-family: 'STKaiti', 'Kaiti', 'KaiTi_GB2312', serif;
    font-size: 26px;
    font-weight: 700;
    user-select: none;
    line-height: 1;
  }

  /* 红棋：朱砂红刻字 + 阴影 */
  &.r {
    .piece-char {
      color: #b91c1c;
      text-shadow: 0 1px 1px rgba(255, 255, 255, 0.65), 0 -0.5px 1px rgba(127, 29, 29, 0.3);
    }
  }

  /* 黑棋：金石苍劲墨黑 + 阴影 */
  &.b {
    .piece-char {
      color: #1c1917;
      text-shadow: 0 1px 1px rgba(255, 255, 255, 0.65), 0 -0.5px 1px rgba(0, 0, 0, 0.4);
    }
  }
}
</style>
