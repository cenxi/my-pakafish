package com.pakafish.ai;

import com.pakafish.ai.service.ChessCoordinateConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
public class LongPvTest {

    @Autowired
    private ChessCoordinateConverter converter;

    @Test
    public void testFiftyMovesTranslation() {
        String fen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";

        // 构造一个合法的长变例序列（50步推演）
        List<String> longMoves = new ArrayList<>(List.of(
                "h2e2", "h9g7", "h0g2", "g6g5", "i0h0", "i9h9", "c3c4", "b7b3",
                "b0c2", "b9c7", "g3g4", "g5g4", "h0h6", "g4g3", "c2d4", "g3g2",
                "b2g2", "a9b9", "h6g6", "b3i3", "g2g7", "c6c5", "a0b0", "h7g7",
                "g6g7", "b9b2", "d4e6", "i3e3", "g7c7", "e3e6", "b0b2", "e6c6",
                "c7c6", "g9e7", "c6c8", "e7c9", "b2b8", "a8c9", "c8g8", "c9a8",
                "g8g9", "a8c9", "g9e9", "c9a8", "e9f9", "a8c9", "f9f7", "c9a8",
                "f7d7", "a8c9"
        ));

        List<String> chinese = converter.convertMoveList(fen, longMoves);

        System.out.println("====== 50步连续推演翻译测试 ======");
        System.out.println("总步数: " + chinese.size());
        for (int i = 0; i < chinese.size(); i++) {
            System.out.printf("%2d: %-6s -> %s\n", i + 1, longMoves.get(i), chinese.get(i));
            // 确保没有出现未翻译的英文字符
            assertFalse(chinese.get(i).matches("^[a-i][0-9][a-i][0-9]$"), "不应存在未翻译坐标: " + chinese.get(i));
        }
        System.out.println("=================================");

        assertEquals(50, chinese.size());
    }
}
