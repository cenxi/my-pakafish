package com.pakafish.ai;

import com.pakafish.ai.model.EngineAnalysisResult;
import com.pakafish.ai.service.PikafishEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ScoreDirectionTest {

    @Autowired
    private PikafishEngineService engineService;

    @Test
    public void testBothSidesScore() {
        // 局面1：红方当头炮后，轮到黑方走 (b)
        // 理论上此时是均势微优 (红方稍优 +20~40分)，看引擎返回给黑方的 scoreCp 是正还是负
        String fenBlackTurn = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C2C4/9/RNBAKABNR b - - 0 1";
        EngineAnalysisResult resB = engineService.analyzePosition(fenBlackTurn, 10, 1000);
        System.out.println("====== 黑方回合 (b) 引擎原始输出 ======");
        System.out.println("raw scoreCp: " + resB.getScoreCp());
        System.out.println("sideText: " + resB.getSideAdvantageText());

        // 局面2：红方回合 (w)
        String fenRedTurn = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
        EngineAnalysisResult resR = engineService.analyzePosition(fenRedTurn, 10, 1000);
        System.out.println("====== 红方回合 (w) 引擎原始输出 ======");
        System.out.println("raw scoreCp: " + resR.getScoreCp());
        System.out.println("sideText: " + resR.getSideAdvantageText());
    }
}
