package com.pakafish.ai;

import com.pakafish.ai.model.BookMove;
import com.pakafish.ai.service.OpeningBookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class OpeningBookServiceTest {

    @Autowired
    private OpeningBookService openingBookService;

    @Test
    public void testInitialPositionBookMoves() {
        assertTrue(openingBookService.hasBook(), "开局库应该成功加载");

        String initialFen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w";
        List<BookMove> moves = openingBookService.getBookMoves(initialFen);

        assertNotNull(moves);
        assertFalse(moves.isEmpty(), "初始局面必须命中开局库");
        System.out.println("初始局面候选着法数: " + moves.size());
        for (BookMove m : moves) {
            System.out.printf("  %s (%s) 权重: %d\n", m.getUci(), m.getChinese(), m.getWeight());
        }

        // 验证常见主流走法是否存在
        boolean hasB2E2 = moves.stream().anyMatch(m -> "b2e2".equals(m.getUci()));
        boolean hasH2E2 = moves.stream().anyMatch(m -> "h2e2".equals(m.getUci()));
        boolean hasB0C2 = moves.stream().anyMatch(m -> "b0c2".equals(m.getUci()));
        assertTrue(hasB2E2 || hasH2E2, "应当包含炮二平五/炮八平五");
        assertTrue(hasB0C2, "应当包含马二进三/马八进七");

        BookMove picked = openingBookService.pickMoveWeighted(moves);
        assertNotNull(picked);
        System.out.printf("按权重挑选的走法: %s (%s)\n", picked.getUci(), picked.getChinese());
    }

    @Test
    public void testBlackReplyToCentralCannon() {
        // 红方当头炮后的局面
        String fenAfterCentralCannon = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/4C2C1/9/RNBAKABNR b";
        List<BookMove> moves = openingBookService.getBookMoves(fenAfterCentralCannon);

        assertNotNull(moves);
        assertFalse(moves.isEmpty(), "当头炮后黑方应手必须命中开局库");
        System.out.println("当头炮后黑方候选着法数: " + moves.size());
        for (BookMove m : moves) {
            System.out.printf("  %s (%s) 权重: %d\n", m.getUci(), m.getChinese(), m.getWeight());
        }

        // 屏风马/顺手炮等经典应手
        boolean hasHorse = moves.stream().anyMatch(m -> "b9c7".equals(m.getUci()) || "h9g7".equals(m.getUci()));
        assertTrue(hasHorse, "当头炮后黑方应当有马8进7或马2进3应手");
    }

    @Test
    public void testBlackReplyToH2E2Mirrored() {
        // 红方炮二平五 (h2e2) 后的局面 (镜像局面)
        String fenH2E2 = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C2C4/9/RNBAKABNR b";
        List<BookMove> moves = openingBookService.getBookMoves(fenH2E2);

        assertNotNull(moves);
        assertFalse(moves.isEmpty(), "炮二平五后黑方应手必须命中开局库(通过镜像匹配)");
        System.out.println("炮二平五 (h2e2) 后黑方候选着法数: " + moves.size());
        for (BookMove m : moves) {
            System.out.printf("  %s (%s) 权重: %d\n", m.getUci(), m.getChinese(), m.getWeight());
        }
    }
}
