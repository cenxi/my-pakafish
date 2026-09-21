// 中国象棋规则核心与坐标计算器
// 棋盘坐标约定：列 a-i (0-8)，行 0-9 (0为红方底线，9为黑方底线)

export const INITIAL_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"

const DIGITS_ZH = ['一', '二', '三', '四', '五', '六', '七', '八', '九']
const DIGITS_AR = ['1', '2', '3', '4', '5', '6', '7', '8', '9']

export function parseFen(fen = INITIAL_FEN) {
  const board = Array.from({ length: 10 }, () => Array(9).fill(null))
  const parts = fen.split(' ')
  const ranks = parts[0].split('/')

  for (let r = 0; r < ranks.length && r < 10; r++) {
    const row = 9 - r
    let col = 0
    for (const ch of ranks[r]) {
      if (/\d/.test(ch)) {
        col += parseInt(ch, 10)
      } else {
        if (col < 9) {
          board[row][col] = {
            type: ch.toLowerCase(),
            color: ch === ch.toUpperCase() ? 'r' : 'b',
            raw: ch
          }
          col++
        }
      }
    }
  }

  const turn = parts[1] || 'w'
  return { board, turn: turn === 'w' ? 'r' : 'b' }
}

export function boardToFen(board, turn = 'r') {
  const rows = []
  for (let r = 9; r >= 0; r--) {
    let emptyCount = 0
    let rowStr = ''
    for (let c = 0; c < 9; c++) {
      const p = board[r][c]
      if (!p) {
        emptyCount++
      } else {
        if (emptyCount > 0) {
          rowStr += emptyCount
          emptyCount = 0
        }
        rowStr += p.color === 'r' ? p.type.toUpperCase() : p.type.toLowerCase()
      }
    }
    if (emptyCount > 0) rowStr += emptyCount
    rows.push(rowStr)
  }
  return `${rows.join('/')} ${turn === 'r' ? 'w' : 'b'} - - 0 1`
}

export function getPieceName(piece) {
  if (!piece) return ''
  const isRed = piece.color === 'r'
  switch (piece.type) {
    case 'r': return '车'
    case 'n': return '马'
    case 'b': return isRed ? '相' : '象'
    case 'a': return isRed ? '仕' : '士'
    case 'k': return isRed ? '帅' : '将'
    case 'c': return '炮'
    case 'p': return isRed ? '兵' : '卒'
    default: return ''
  }
}

export function uciToChinese(fen, uci) {
  if (!uci || uci.length < 4) return uci
  const { board } = parseFen(fen)
  const fc = uci.charCodeAt(0) - 97
  const fr = parseInt(uci[1], 10)
  const tc = uci.charCodeAt(2) - 97
  const tr = parseInt(uci[3], 10)

  const piece = board[fr]?.[fc]
  if (!piece) return uci

  const isRed = piece.color === 'r'
  const name = getPieceName(piece)

  const srcCol = isRed ? 9 - fc : fc + 1
  const srcColStr = isRed ? DIGITS_ZH[srcCol - 1] : DIGITS_AR[srcCol - 1]

  let action = ''
  let destStr = ''

  if (fr === tr) {
    action = '平'
    const destCol = isRed ? 9 - tc : tc + 1
    destStr = isRed ? DIGITS_ZH[destCol - 1] : DIGITS_AR[destCol - 1]
  } else {
    const isForward = isRed ? tr > fr : tr < fr
    action = isForward ? '进' : '退'
    if (['n', 'b', 'a'].includes(piece.type)) {
      const destCol = isRed ? 9 - tc : tc + 1
      destStr = isRed ? DIGITS_ZH[destCol - 1] : DIGITS_AR[destCol - 1]
    } else {
      const steps = Math.abs(tr - fr)
      destStr = isRed ? DIGITS_ZH[steps - 1] : DIGITS_AR[steps - 1]
    }
  }

  return `${name}${srcColStr}${action}${destStr}`
}

/**
 * 基础伪合法走子探测
 */
export function getPseudoLegalMoves(board, r, c) {
  const piece = board[r][c]
  if (!piece) return []
  const moves = []
  const isRed = piece.color === 'r'

  const canMoveTo = (nr, nc) => {
    if (nr < 0 || nr > 9 || nc < 0 || nc > 8) return false
    const dest = board[nr][nc]
    return !dest || dest.color !== piece.color
  }

  switch (piece.type) {
    case 'k': {
      const minR = isRed ? 0 : 7
      const maxR = isRed ? 2 : 9
      const minC = 3
      const maxC = 5
      const dirs = [[0, 1], [0, -1], [1, 0], [-1, 0]]
      for (const [dr, dc] of dirs) {
        const nr = r + dr
        const nc = c + dc
        if (nr >= minR && nr <= maxR && nc >= minC && nc <= maxC && canMoveTo(nr, nc)) {
          moves.push({ r: nr, c: nc })
        }
      }
      // 将帅照面直接飞将
      const step = isRed ? 1 : -1
      let faceR = r + step
      while (faceR >= 0 && faceR <= 9) {
        const p = board[faceR][c]
        if (p) {
          if (p.type === 'k') moves.push({ r: faceR, c })
          break
        }
        faceR += step
      }
      break
    }
    case 'a': {
      const minR = isRed ? 0 : 7
      const maxR = isRed ? 2 : 9
      const dirs = [[1, 1], [1, -1], [-1, 1], [-1, -1]]
      for (const [dr, dc] of dirs) {
        const nr = r + dr
        const nc = c + dc
        if (nr >= minR && nr <= maxR && nc >= 3 && nc <= 5 && canMoveTo(nr, nc)) {
          moves.push({ r: nr, c: nc })
        }
      }
      break
    }
    case 'b': {
      const minR = isRed ? 0 : 5
      const maxR = isRed ? 4 : 9
      const dirs = [
        { dr: 2, dc: 2, eyeR: 1, eyeC: 1 },
        { dr: 2, dc: -2, eyeR: 1, eyeC: -1 },
        { dr: -2, dc: 2, eyeR: -1, eyeC: 1 },
        { dr: -2, dc: -2, eyeR: -1, eyeC: -1 }
      ]
      for (const { dr, dc, eyeR, eyeC } of dirs) {
        const nr = r + dr
        const nc = c + dc
        if (nr >= minR && nr <= maxR && nc >= 0 && nc <= 8) {
          if (!board[r + eyeR][c + eyeC] && canMoveTo(nr, nc)) {
            moves.push({ r: nr, c: nc })
          }
        }
      }
      break
    }
    case 'n': {
      const jumps = [
        { dr: 2, dc: 1, legR: 1, legC: 0 },
        { dr: 2, dc: -1, legR: 1, legC: 0 },
        { dr: -2, dc: 1, legR: -1, legC: 0 },
        { dr: -2, dc: -1, legR: -1, legC: 0 },
        { dr: 1, dc: 2, legR: 0, legC: 1 },
        { dr: -1, dc: 2, legR: 0, legC: 1 },
        { dr: 1, dc: -2, legR: 0, legC: -1 },
        { dr: -1, dc: -2, legR: 0, legC: -1 }
      ]
      for (const { dr, dc, legR, legC } of jumps) {
        const nr = r + dr
        const nc = c + dc
        if (nr >= 0 && nr <= 9 && nc >= 0 && nc <= 8) {
          if (!board[r + legR][c + legC] && canMoveTo(nr, nc)) {
            moves.push({ r: nr, c: nc })
          }
        }
      }
      break
    }
    case 'r': {
      const dirs = [[0, 1], [0, -1], [1, 0], [-1, 0]]
      for (const [dr, dc] of dirs) {
        let nr = r + dr
        let nc = c + dc
        while (nr >= 0 && nr <= 9 && nc >= 0 && nc <= 8) {
          const dest = board[nr][nc]
          if (!dest) {
            moves.push({ r: nr, c: nc })
          } else {
            if (dest.color !== piece.color) moves.push({ r: nr, c: nc })
            break
          }
          nr += dr
          nc += dc
        }
      }
      break
    }
    case 'c': {
      const dirs = [[0, 1], [0, -1], [1, 0], [-1, 0]]
      for (const [dr, dc] of dirs) {
        let nr = r + dr
        let nc = c + dc
        let jumped = false
        while (nr >= 0 && nr <= 9 && nc >= 0 && nc <= 8) {
          const dest = board[nr][nc]
          if (!jumped) {
            if (!dest) {
              moves.push({ r: nr, c: nc })
            } else {
              jumped = true
            }
          } else {
            if (dest) {
              if (dest.color !== piece.color) moves.push({ r: nr, c: nc })
              break
            }
          }
          nr += dr
          nc += dc
        }
      }
      break
    }
    case 'p': {
      const forward = isRed ? 1 : -1
      const crossedRiver = isRed ? r >= 5 : r <= 4
      if (canMoveTo(r + forward, c)) moves.push({ r: r + forward, c })
      if (crossedRiver) {
        if (canMoveTo(r, c - 1)) moves.push({ r, c: c - 1 })
        if (canMoveTo(r, c + 1)) moves.push({ r, c: c + 1 })
      }
      break
    }
  }

  return moves
}

/**
 * 校验某方老将是否正在被将军（或两将对脸）
 */
export function isKingInCheck(board, color) {
  // 1. 找到该方将/帅的位置
  let kr = -1, kc = -1
  for (let r = 0; r < 10; r++) {
    for (let c = 0; c < 9; c++) {
      const p = board[r][c]
      if (p && p.type === 'k' && p.color === color) {
        kr = r
        kc = c
        break
      }
    }
    if (kr !== -1) break
  }

  if (kr === -1) return true // 老将都没了，直接处于死局

  const opponentColor = color === 'r' ? 'b' : 'r'

  // 2. 检查敌方所有棋子的攻击范围是否覆盖了老将 (kr, kc)
  for (let r = 0; r < 10; r++) {
    for (let c = 0; c < 9; c++) {
      const p = board[r][c]
      if (p && p.color === opponentColor) {
        const moves = getPseudoLegalMoves(board, r, c)
        if (moves.some(m => m.r === kr && m.c === kc)) {
          return true
        }
      }
    }
  }
  return false
}

/**
 * 真正合法的走法：伪走后老将不能处于被将军状态
 */
export function getLegalMoves(board, r, c) {
  const piece = board[r][c]
  if (!piece) return []

  const pseudoMoves = getPseudoLegalMoves(board, r, c)
  const legalMoves = []

  for (const mv of pseudoMoves) {
    // 模拟走一步
    const originalDest = board[mv.r][mv.c]
    board[mv.r][mv.c] = piece
    board[r][c] = null

    // 检查走完后自己老将是否依然受攻
    const inCheck = isKingInCheck(board, piece.color)

    // 还原棋盘
    board[r][c] = piece
    board[mv.r][mv.c] = originalDest

    if (!inCheck) {
      legalMoves.push(mv)
    }
  }

  return legalMoves
}

/**
 * 检查当前方是否已经被绝杀（无路可走）
 */
export function isCheckmate(board, turnColor) {
  for (let r = 0; r < 10; r++) {
    for (let c = 0; c < 9; c++) {
      const p = board[r][c]
      if (p && p.color === turnColor) {
        const moves = getLegalMoves(board, r, c)
        if (moves.length > 0) {
          return false // 还有合法的棋能走，未被绝杀
        }
      }
    }
  }
  return true // 该方已无任何合法棋步，判定绝杀（胜负已分）
}
