package com.pakafish.ai.service;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 中国象棋坐标与中文着法转换器
 * UCI坐标格式: [a-i][0-9][a-i][0-9]，例如 b2e2, h7e7, h0g2
 */
@Component
public class ChessCoordinateConverter {

    private static final String[] DIGITS_ZH = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
    private static final String[] DIGITS_ARABIC = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};

    /**
     * 将 UCI 坐标 (如 b2e2) 根据棋盘 FEN 转换为中文记谱 (如 炮二平五)
     */
    public String uciToChinese(String fen, String uciMove) {
        if (uciMove == null || uciMove.length() < 4) {
            return uciMove;
        }

        try {
            char[][] board = parseFenToBoard(fen);
            int fromCol = uciMove.charAt(0) - 'a';
            int fromRow = uciMove.charAt(1) - '0';
            int toCol = uciMove.charAt(2) - 'a';
            int toRow = uciMove.charAt(3) - '0';

            char piece = board[fromRow][fromCol];
            if (piece == ' ') {
                return uciMove;
            }

            boolean isRed = Character.isUpperCase(piece);
            String pieceName = getPieceName(piece);

            // 红方从右向左为 一到九 (列号从 8 到 0)
            // 黑方从右向左为 1到9 (列号从 0 到 8)
            int srcFile = isRed ? (9 - fromCol) : (fromCol + 1);
            String srcFileStr = isRed ? DIGITS_ZH[srcFile - 1] : DIGITS_ARABIC[srcFile - 1];

            String action;
            String destStr;

            if (fromRow == toRow) {
                // 平
                action = "平";
                int destFile = isRed ? (9 - toCol) : (toCol + 1);
                destStr = isRed ? DIGITS_ZH[destFile - 1] : DIGITS_ARABIC[destFile - 1];
            } else {
                // 进或退
                boolean isForward = isRed ? (toRow > fromRow) : (toRow < fromRow);
                action = isForward ? "进" : "退";

                // 马、相、士走斜线，第四个字是目标路线；车、炮、兵走直线，第四个字是进退的步数
                char lowerPiece = Character.toLowerCase(piece);
                if (lowerPiece == 'n' || lowerPiece == 'b' || lowerPiece == 'a') {
                    int destFile = isRed ? (9 - toCol) : (toCol + 1);
                    destStr = isRed ? DIGITS_ZH[destFile - 1] : DIGITS_ARABIC[destFile - 1];
                } else {
                    int step = Math.abs(toRow - fromRow);
                    destStr = isRed ? DIGITS_ZH[step - 1] : DIGITS_ARABIC[step - 1];
                }
            }

            return pieceName + srcFileStr + action + destStr;
        } catch (Exception e) {
            return uciMove;
        }
    }

    /**
     * 将一系列 UCI 走法转换为中文走法
     */
    public List<String> convertMoveList(String fen, List<String> uciMoves) {
        if (uciMoves == null || uciMoves.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        // 当前简易版本转换第一步最佳，后续多步做容错处理
        for (String m : uciMoves) {
            list.add(uciToChinese(fen, m));
        }
        return list;
    }

    public char[][] parseFenToBoard(String fen) {
        char[][] board = new char[10][9];
        for (int r = 0; r < 10; r++) {
            Arrays.fill(board[r], ' ');
        }
        String boardPart = fen.split(" ")[0];
        String[] ranks = boardPart.split("/");
        for (int i = 0; i < ranks.length && i < 10; i++) {
            // FEN 第一行对应棋盘行 9 (黑方底线)
            int row = 9 - i;
            String rankStr = ranks[i];
            int col = 0;
            for (char c : rankStr.toCharArray()) {
                if (Character.isDigit(c)) {
                    col += (c - '0');
                } else {
                    if (col < 9) {
                        board[row][col++] = c;
                    }
                }
            }
        }
        return board;
    }

    private String getPieceName(char p) {
        return switch (p) {
            case 'R' -> "车";
            case 'N' -> "马";
            case 'B' -> "相";
            case 'A' -> "仕";
            case 'K' -> "帅";
            case 'C' -> "炮";
            case 'P' -> "兵";
            case 'r' -> "车";
            case 'n' -> "马";
            case 'b' -> "象";
            case 'a' -> "士";
            case 'k' -> "将";
            case 'c' -> "炮";
            case 'p' -> "卒";
            default -> String.valueOf(p);
        };
    }
}
