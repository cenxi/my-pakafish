package com.pakafish.ai;

import com.pakafish.ai.model.EngineAnalysisResult;
import com.pakafish.ai.service.PikafishEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class PikafishEngineTest {

    @Autowired
    private PikafishEngineService pikafishEngineService;

    @Autowired
    private com.pakafish.ai.service.ChessCoordinateConverter coordinateConverter;

    @Test
    public void testAnalyzeInitialPosition() {
        // 标准象棋开局FEN: 兵/卒用 P/p
        String startFen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
        EngineAnalysisResult result = pikafishEngineService.analyzePosition(startFen, 1000);
        if (result != null && result.getBestMove() != null) {
            result.setBestMoveChinese(coordinateConverter.uciToChinese(startFen, result.getBestMove()));
            result.setPvMovesChinese(coordinateConverter.convertMoveList(startFen, result.getPvMoves()));
        }
        System.out.println("====== 皮卡鱼引擎分析测试 ======");
        System.out.println("最佳着法 (UCI): " + result.getBestMove());
        System.out.println("最佳着法 (中文): " + result.getBestMoveChinese());
        System.out.println("评分 (cp): " + result.getScoreCp());
        System.out.println("胜率估算: " + result.getWinRate() + "%");
        System.out.println("搜索深度: " + result.getDepth());
        System.out.println("PV 变例 (UCI): " + result.getPvMoves());
        System.out.println("局势描述: " + result.getAdvantageDescription());
        System.out.println("===============================");
        assertNotNull(result.getBestMove());
    }
}
