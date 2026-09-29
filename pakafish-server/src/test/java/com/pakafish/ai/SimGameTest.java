package com.pakafish.ai;

import com.pakafish.ai.controller.ChessApiController;
import com.pakafish.ai.model.EngineAnalysisResult;
import com.pakafish.ai.service.ChessCoordinateConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest
public class SimGameTest {

    @Autowired
    private ChessApiController chessApiController;

    @Autowired
    private com.pakafish.ai.service.PikafishEngineService pikafishEngineService;

    @Autowired
    private ChessCoordinateConverter coordinateConverter;

    @Test
    public void testBlackVsRandomRed() {
        // 初始局面
        String fen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
        System.out.println("========== 对局模拟测试开始 ==========");

        // 模拟红方随便走几步，看黑方（引擎）每次出招是什么
        String[] redMoves = new String[] {
            "a3a4", // 红方随便进边兵
            "c3c4", // 红方随便进七兵
            "b0a2", // 红方起边马
            "a0a1", // 红方起边车
            "a1d1"  // 红方乱走车
        };

        for (int i = 0; i < redMoves.length; i++) {
            String redMove = redMoves[i];
            System.out.printf("\n--- 第 %d 回合 ---\n", i + 1);
            System.out.printf("红方（玩家随便走）: %s (%s)\n", redMove, coordinateConverter.uciToChinese(fen, redMove));
            fen = coordinateConverter.applyMove(fen, redMove);
            System.out.printf("红方走完后 FEN: %s\n", fen);

            // 请求引擎执黑出招
            Map<String, Object> req = new HashMap<>();
            req.put("fen", fen);
            req.put("depth", 20);
            req.put("movetime", 1000);
            req.put("immediate", true);
            req.put("useBook", true);

            EngineAnalysisResult res = chessApiController.analyze(req);
            System.out.printf("黑方（AI引擎）应手: %s (%s), 深度: %d, 分值: %d, 来自开局库: %s, 来自缓存: %s\n",
                    res.getBestMove(), res.getBestMoveChinese(), res.getDepth(), res.getScoreCp(),
                    res.getFromBook(), res.getFromCache());
            System.out.printf("局势评估: %s (%s)\n", res.getSideAdvantageText(), res.getAdvantageDescription());
            System.out.printf("PV变例: %s\n", res.getPvMoves());

            if (res.getBestMove() != null) {
                fen = coordinateConverter.applyMove(fen, res.getBestMove());
                System.out.printf("黑方走完后 FEN: %s\n", fen);
            }
        }
    }

    @Test
    public void testWithBackgroundStreamAnalyze() {
        System.out.println("========== 模拟带前端SSE后台推演真实对局 ==========");
        String fen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";

        // 1. 用户还在思考红棋时，前端开启了 SSE streamAnalyze 进行后台推演
        System.out.println("1. 前端开启 SSE streamAnalyze 深度推演 (红方回合)...");
        pikafishEngineService.streamAnalyze(fen, 15, snap -> {
            // 前端收到深度流
        });

        // 2. 用户走了一步红棋（例如进七兵 c3c4）
        String userMove = "c3c4";
        String nextFen = coordinateConverter.applyMove(fen, userMove);
        System.out.println("2. 用户走了红棋: " + userMove + ", 下一局面 FEN: " + nextFen);

        // 3. 轮到黑棋走子，前端触发 /api/chess/analyze
        Map<String, Object> req = new HashMap<>();
        req.put("fen", nextFen);
        req.put("depth", 20);
        req.put("movetime", 1000);
        req.put("immediate", true);
        req.put("useBook", true);

        long start = System.currentTimeMillis();
        EngineAnalysisResult res = chessApiController.analyze(req);
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("3. 黑棋分析返回: 耗时 %d ms, 着法: %s (%s), 深度: %d, 来自缓存: %s, 来自开局库: %s\n",
                elapsed, res.getBestMove(), res.getBestMoveChinese(), res.getDepth(),
                res.getFromCache(), res.getFromBook());
    }

    @Test
    public void testPawnRush() {
        System.out.println("========== 测试红棋单兵乱冲测试 ==========");
        String fen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";

        String[] moves = new String[] {
            "c3c4",
            "c4c5",
            "c5c6"
        };

        for (int i = 0; i < moves.length; i++) {
            String rm = moves[i];
            System.out.printf("\n[回合 %d] 红走: %s (%s)\n", i + 1, rm, coordinateConverter.uciToChinese(fen, rm));
            fen = coordinateConverter.applyMove(fen, rm);

            Map<String, Object> req = new HashMap<>();
            req.put("fen", fen);
            req.put("depth", 20);
            req.put("movetime", 1000);
            req.put("immediate", true);
            req.put("useBook", true);

            EngineAnalysisResult res = chessApiController.analyze(req);
            System.out.printf("[回合 %d] 黑应: %s (%s), 分值: %d, 深度: %d, 来源: %s\n",
                    i + 1, res.getBestMove(), res.getBestMoveChinese(), res.getScoreCp(), res.getDepth(),
                    Boolean.TRUE.equals(res.getFromBook()) ? "开局库" : (Boolean.TRUE.equals(res.getFromCache()) ? "缓存" : "引擎"));
            if (res.getBestMove() != null) {
                fen = coordinateConverter.applyMove(fen, res.getBestMove());
            }
        }
    }
}
