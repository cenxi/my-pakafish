package com.pakafish.ai.controller;

import com.pakafish.ai.model.BookMove;
import com.pakafish.ai.model.EngineAnalysisResult;
import com.pakafish.ai.service.ChessCoachChatService;
import com.pakafish.ai.service.ChessCoordinateConverter;
import com.pakafish.ai.service.OpeningBookService;
import com.pakafish.ai.service.OpeningPresetService;
import com.pakafish.ai.service.PikafishEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/chess")
@RequiredArgsConstructor
public class ChessApiController {

    private final PikafishEngineService pikafishEngineService;
    private final ChessCoordinateConverter coordinateConverter;
    private final ChessCoachChatService coachChatService;
    private final OpeningBookService openingBookService;
    private final OpeningPresetService openingPresetService;

    /**
     * 获取所有支持的经典开局定式目录 (用于前端下拉选择开局)
     */
    @GetMapping("/opening-presets")
    public List<com.pakafish.ai.model.OpeningPreset> getOpeningPresets() {
        return openingPresetService.getAllPresets();
    }

    /**
     * 1. 引擎与开局库综合分析接口
     * 优先查询标准开局库 BOOK.DAT（0ms 响应，包含正着和主流变例）；
     * 若未命中开局库（已脱谱），则调用皮卡鱼深度推演。
     */
    @PostMapping("/analyze")
    public EngineAnalysisResult analyze(@RequestBody Map<String, Object> request) {
        String fen = (String) request.getOrDefault("fen", "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1");
        Integer movetime = request.containsKey("movetime") ? (Integer) request.get("movetime") : null;
        Integer depth = request.containsKey("depth") ? (Integer) request.get("depth") : null;
        Boolean useBook = request.containsKey("useBook") ? (Boolean) request.get("useBook") : true;
        Boolean forceEngine = (request.containsKey("forceEngine") && Boolean.TRUE.equals(request.get("forceEngine"))) || !useBook;

        Boolean immediate = request.containsKey("immediate") && Boolean.TRUE.equals(request.get("immediate"));
        if (immediate) {
            pikafishEngineService.stopCurrentSearch();
        }

        log.info("【走棋/分析请求】FEN: {}, depth: {}, movetime: {}, useBook: {}, forceEngine: {}, immediate: {}", fen, depth, movetime, useBook, forceEngine, immediate);

        // 1. 如果启用了开局库且未强制指定纯引擎，先查开局库
        if (!forceEngine) {
            List<BookMove> bookMoves = openingBookService.getBookMoves(fen);
            if (bookMoves != null && !bookMoves.isEmpty()) {
                BookMove topMove = bookMoves.get(0);
                log.info("【开局库命中】选用着法: {} ({}), 共有变例: {} 种", topMove.getUci(), topMove.getChinese(), bookMoves.size());
                return EngineAnalysisResult.builder()
                        .bestMove(topMove.getUci())
                        .bestMoveChinese(topMove.getChinese())
                        .scoreCp(0)
                        .winRate(50.0)
                        .depth(1)
                        .pvMoves(Collections.singletonList(topMove.getUci()))
                        .pvMovesChinese(Collections.singletonList(topMove.getChinese()))
                        .advantageDescription("开局定式（棋逢对手）")
                        .sideAdvantageText("双方均势 (开局库)")
                        .fromBook(true)
                        .fromCache(false)
                        .bookMoves(bookMoves)
                        .build();
            }
        }

        // 2. 检查 L1 快速缓存（上一手推导的预热分析或历史分析，0ms 响应）
        Boolean noCache = request.containsKey("noCache") && Boolean.TRUE.equals(request.get("noCache"));
        if (!forceEngine && !noCache) {
            EngineAnalysisResult cached = pikafishEngineService.getCachedAnalysis(fen);
            if (cached != null && cached.getDepth() != null && cached.getDepth() >= 12) {
                log.info("【L1缓存命中】返回复用分析 (0ms), 着法: {} ({}), 深度: {}, 分值: {}",
                        cached.getBestMove(), cached.getBestMoveChinese(), cached.getDepth(), cached.getScoreCp());
                if (cached.getBestMoveChinese() == null && cached.getBestMove() != null) {
                    cached.setBestMoveChinese(coordinateConverter.uciToChinese(fen, cached.getBestMove()));
                }
                if (cached.getPvMovesChinese() == null && cached.getPvMoves() != null) {
                    cached.setPvMovesChinese(coordinateConverter.convertMoveList(fen, cached.getPvMoves()));
                }
                return cached;
            }
        }

        log.info("【皮卡鱼深度推演】开局库脱谱且无高深度缓存，启动引擎计算...");
        // 3. 脱谱或强制引擎：调用皮卡鱼引擎
        long startTime = System.currentTimeMillis();
        EngineAnalysisResult result = pikafishEngineService.analyzePosition(fen, depth, movetime);
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("【皮卡鱼推演完成】耗时: {} ms, 最佳着法: {}, 深度: {}", elapsed, result != null ? result.getBestMove() : "null", result != null ? result.getDepth() : 0);
        if (result != null) {
            result.setFromBook(false);
            if (result.getBestMove() != null && result.getBestMoveChinese() == null) {
                result.setBestMoveChinese(coordinateConverter.uciToChinese(fen, result.getBestMove()));
            }
            if (result.getPvMoves() != null && result.getPvMovesChinese() == null) {
                result.setPvMovesChinese(coordinateConverter.convertMoveList(fen, result.getPvMoves()));
            }
        }
        return result;
    }

    /**
     * 单独查询当前局面的开局库推荐招法（用于界面展示开局候选库）
     */
    @PostMapping("/book-moves")
    public List<BookMove> getBookMoves(@RequestBody Map<String, Object> request) {
        String fen = (String) request.getOrDefault("fen", "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1");
        return openingBookService.getBookMoves(fen);
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

    /**
     * 3. AI 特级大师教练同步问答接口（直接返回字符串，供移动端或简单请求使用）
     */
    @PostMapping("/chat")
    public String chat(@RequestBody Map<String, Object> request) {
        String fen = (String) request.getOrDefault("fen", "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1");
        String history = (String) request.getOrDefault("history", "");
        String question = (String) request.getOrDefault("question", "");
        return coachChatService.chat(fen, history, question);
    }

    /**
     * 4. 实时流式深度推演接口 (SSE)
     * 引擎 go depth <maxDepth> 持续向下推演，每深入一层推送一条 event: analysis
     */
    @GetMapping(value = "/stream-analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnalyze(
            @RequestParam(value = "fen") String fen,
            @RequestParam(value = "depth", required = false, defaultValue = "30") Integer depth,
            @RequestParam(value = "useBook", required = false, defaultValue = "true") Boolean useBook) {

        SseEmitter emitter = new SseEmitter(300_000L); // 5分钟

        // 监听客户端主动断开连接或超时，立即 stop 中断引擎推演，释放锁资源！
        emitter.onCompletion(() -> pikafishEngineService.stopCurrentSearch());
        emitter.onTimeout(() -> pikafishEngineService.stopCurrentSearch());
        emitter.onError(e -> pikafishEngineService.stopCurrentSearch());

        Thread.startVirtualThread(() -> {
            try {
                // 1. 先查开局库
                if (Boolean.TRUE.equals(useBook)) {
                    List<BookMove> bookMoves = openingBookService.getBookMoves(fen);
                    if (bookMoves != null && !bookMoves.isEmpty()) {
                        BookMove topMove = bookMoves.get(0);
                        EngineAnalysisResult bookRes = EngineAnalysisResult.builder()
                                .bestMove(topMove.getUci())
                                .bestMoveChinese(topMove.getChinese())
                                .scoreCp(0)
                                .winRate(50.0)
                                .depth(1)
                                .pvMoves(Collections.singletonList(topMove.getUci()))
                                .pvMovesChinese(Collections.singletonList(topMove.getChinese()))
                                .advantageDescription("开局定式（棋逢对手）")
                                .sideAdvantageText("双方均势 (开局库)")
                                .fromBook(true)
                                .bookMoves(bookMoves)
                                .build();
                        emitter.send(SseEmitter.event().name("analysis").data(bookRes));
                        emitter.complete();
                        return;
                    }
                }

                // 2. 检查是否有预热的 L1 缓存。如果有，0ms 先推送一条预热首帧给前端，让界面瞬间显示评分与走法！
                EngineAnalysisResult cached = pikafishEngineService.getCachedAnalysis(fen);
                if (cached != null) {
                    log.info("【流式推演预热】立即先推送 L1 缓存首帧 (0ms), 着法: {}, depth: {}", cached.getBestMove(), cached.getDepth());
                    if (cached.getBestMoveChinese() == null && cached.getBestMove() != null) {
                        cached.setBestMoveChinese(coordinateConverter.uciToChinese(fen, cached.getBestMove()));
                    }
                    if (cached.getPvMovesChinese() == null && cached.getPvMoves() != null) {
                        cached.setPvMovesChinese(coordinateConverter.convertMoveList(fen, cached.getPvMoves()));
                    }
                    if (cached.getPonderMove() != null && cached.getPonderMoveChinese() == null && cached.getBestMove() != null) {
                        String nextFen = coordinateConverter.applyMove(fen, cached.getBestMove());
                        cached.setPonderMoveChinese(coordinateConverter.uciToChinese(nextFen, cached.getPonderMove()));
                    }
                    try {
                        emitter.send(SseEmitter.event().name("analysis").data(cached));
                    } catch (Exception ignored) {}
                }

                // 3. 启动皮卡鱼流式深算（利用引擎底层 TT Hash 记忆继续向下深算）
                pikafishEngineService.streamAnalyze(fen, depth, snapshot -> {
                    try {
                        if (snapshot.getBestMove() != null && snapshot.getBestMoveChinese() == null) {
                            snapshot.setBestMoveChinese(coordinateConverter.uciToChinese(fen, snapshot.getBestMove()));
                        }
                        if (snapshot.getPvMoves() != null && snapshot.getPvMovesChinese() == null) {
                            snapshot.setPvMovesChinese(coordinateConverter.convertMoveList(fen, snapshot.getPvMoves()));
                        }
                        if (snapshot.getPonderMove() != null && snapshot.getPonderMoveChinese() == null && snapshot.getBestMove() != null) {
                            String nextFen = coordinateConverter.applyMove(fen, snapshot.getBestMove());
                            snapshot.setPonderMoveChinese(coordinateConverter.uciToChinese(nextFen, snapshot.getPonderMove()));
                        }
                        emitter.send(SseEmitter.event().name("analysis").data(snapshot));
                    } catch (Exception e) {
                        // 客户端已断开，抛出运行时异常让皮卡鱼引擎立刻终止循环
                        pikafishEngineService.stopCurrentSearch();
                        throw new RuntimeException("SSE_CLIENT_DISCONNECTED", e);
                    }
                });

                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            } catch (Exception e) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        });

        return emitter;
    }

    /**
     * 中断当前推演计算
     */
    @PostMapping("/stop-analyze")
    public Map<String, Object> stopAnalyze() {
        pikafishEngineService.stopCurrentSearch();
        return Map.of("ok", true);
    }
}
