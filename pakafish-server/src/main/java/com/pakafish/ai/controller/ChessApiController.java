package com.pakafish.ai.controller;

import com.pakafish.ai.model.EngineAnalysisResult;
import com.pakafish.ai.service.ChessCoachChatService;
import com.pakafish.ai.service.ChessCoordinateConverter;
import com.pakafish.ai.service.PikafishEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/chess")
@RequiredArgsConstructor
public class ChessApiController {

    private final PikafishEngineService pikafishEngineService;
    private final ChessCoordinateConverter coordinateConverter;
    private final ChessCoachChatService coachChatService;

    /**
     * 1. 纯引擎分析接口（秒级响应）
     * 传入 FEN，返回最佳着法、分值、胜率、变例
     */
    @PostMapping("/analyze")
    public EngineAnalysisResult analyze(@RequestBody Map<String, Object> request) {
        String fen = (String) request.getOrDefault("fen", "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1");
        Integer movetime = request.containsKey("movetime") ? (Integer) request.get("movetime") : null;
        Integer depth = request.containsKey("depth") ? (Integer) request.get("depth") : null;

        EngineAnalysisResult result = pikafishEngineService.analyzePosition(fen, depth, movetime);
        if (result != null && result.getBestMove() != null) {
            result.setBestMoveChinese(coordinateConverter.uciToChinese(fen, result.getBestMove()));
            result.setPvMovesChinese(coordinateConverter.convertMoveList(fen, result.getPvMoves()));
        }
        return result;
    }

    /**
     * 2. AI 特级大师教练对话 / 局势解说接口（流式 SSE）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam(value = "fen", required = false, defaultValue = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1") String fen,
            @RequestParam(value = "history", required = false, defaultValue = "") String history,
            @RequestParam(value = "question", required = false, defaultValue = "") String question) {

        SseEmitter emitter = new SseEmitter(120_000L); // 2分钟超时
        // 异步执行流式对话
        Thread.startVirtualThread(() -> {
            coachChatService.streamChat(fen, history, question, emitter);
        });
        return emitter;
    }
}
