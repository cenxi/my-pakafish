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

import java.util.Arrays;
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

    // --------------- 合法 FEN 基础格式校验（防止非法输入送到引擎）---------------
    private static final String DEFAULT_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";

    /**
     * 校验 FEN 格式基本合法性：必须包含 10 行 + 走子方字段
     * 【Fix-B8】防止恶意或错误的 FEN 注入到引擎命令行
     */
    private boolean isValidFen(String fen) {
        if (fen == null || fen.isBlank()) return false;
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 2) return false;
        String[] rows = parts[0].split("/");
        if (rows.length != 10) return false;
        String side = parts[1];
        return "w".equals(side) || "b".equals(side);
    }

    /**
     * 校验单步 UCI 着法格式（如 b0c2, h7e7 等）
     */
    private boolean isValidUciMove(String move) {
        return move != null && move.matches("[a-i][0-9][a-i][0-9]");
    }

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
     *
     * 【Fix-B8】对入参做类型安全提取与格式校验，避免 ClassCastException 和 FEN 注入
     */
    @PostMapping("/analyze")
    public EngineAnalysisResult analyze(@RequestBody Map<String, Object> request) {
        // 安全提取 FEN，校验格式
        String fen = safeGetString(request, "fen", DEFAULT_FEN);
        if (!isValidFen(fen)) {
            log.warn("【校验失败】收到非法 FEN: {}", fen);
            fen = DEFAULT_FEN;
        }

        // 安全提取数值参数
        Integer movetime = safeGetInteger(request, "movetime");
        Integer depth = safeGetInteger(request, "depth");
        boolean useBook = safeGetBoolean(request, "useBook", true);
        boolean forceEngine = safeGetBoolean(request, "forceEngine", false) || !useBook;
        boolean noCache = safeGetBoolean(request, "noCache", false);

        // 安全提取 moves 列表，逐个校验格式
        List<String> moves = safeGetMoves(request, "moves");
        String startFen = safeGetString(request, "startFen", null);
        if (startFen != null && !isValidFen(startFen)) {
            log.warn("【校验失败】非法 startFen，已忽略: {}", startFen);
            startFen = null;
        }

        log.info("【走棋/分析请求】FEN: {}, depth: {}, movetime: {}, useBook: {}, forceEngine: {}, 历史步数: {}",
                fen, depth, movetime, useBook, forceEngine, moves != null ? moves.size() : 0);

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
        if (!forceEngine && !noCache) {
            EngineAnalysisResult cached = pikafishEngineService.getCachedAnalysis(fen);
            // 只有高深度且非预热推导的实际引擎算力缓存才予以复用
            if (cached != null && cached.getDepth() != null && cached.getDepth() >= 15 && !Boolean.TRUE.equals(cached.getFromCache())) {
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
        EngineAnalysisResult result = pikafishEngineService.analyzePosition(fen, depth, movetime, moves, startFen);
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("【皮卡鱼推演完成】耗时: {} ms, 最佳着法: {}, 深度: {}", elapsed,
                result != null ? result.getBestMove() : "null", result != null ? result.getDepth() : 0);
        if (result != null) {
            result.setFromBook(false);
            if (result.getFromCache() == null) {
                result.setFromCache(false);
            }
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
        String fen = safeGetString(request, "fen", DEFAULT_FEN);
        if (!isValidFen(fen)) fen = DEFAULT_FEN;
        return openingBookService.getBookMoves(fen);
    }

    /**
     * 2. AI 特级大师教练对话 / 局势解说接口（流式 SSE）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam(value = "fen", required = false, defaultValue = DEFAULT_FEN) String fen,
            @RequestParam(value = "history", required = false, defaultValue = "") String history,
            @RequestParam(value = "question", required = false, defaultValue = "") String question) {

        if (!isValidFen(fen)) fen = DEFAULT_FEN;
        final String finalFen = fen;

        SseEmitter emitter = new SseEmitter(120_000L); // 2分钟超时
        // 异步执行流式对话
        Thread.startVirtualThread(() -> {
            coachChatService.streamChat(finalFen, history, question, emitter);
        });
        return emitter;
    }

    /**
     * 3. AI 特级大师教练同步问答接口（直接返回字符串，供移动端或简单请求使用）
     */
    @PostMapping("/chat")
    public String chat(@RequestBody Map<String, Object> request) {
        String fen = safeGetString(request, "fen", DEFAULT_FEN);
        if (!isValidFen(fen)) fen = DEFAULT_FEN;
        String history = safeGetString(request, "history", "");
        String question = safeGetString(request, "question", "");
        return coachChatService.chat(fen, history, question);
    }

    /**
     * 4. 实时流式深度推演初始化接口（POST）
     * 【Fix-O7】原来用 GET + URL 参数传递 moves，长对局历史会超过 URL 长度限制。
     * 改为 POST Body 传递所有参数（含 moves 列表），返回 sessionId；
     * 客户端再通过 GET /stream-analyze/events?sessionId=xxx 拉取 SSE 流。
     *
     * 为保持向前兼容，旧的 GET /stream-analyze（不含 moves）仍然保留。
     */
    @PostMapping("/stream-analyze/init")
    public Map<String, Object> initStreamAnalyze(@RequestBody Map<String, Object> request) {
        String fen = safeGetString(request, "fen", DEFAULT_FEN);
        if (!isValidFen(fen)) fen = DEFAULT_FEN;
        Integer depth = safeGetInteger(request, "depth");
        if (depth == null) depth = 30;
        boolean useBook = safeGetBoolean(request, "useBook", true);
        List<String> moves = safeGetMoves(request, "moves");
        String startFen = safeGetString(request, "startFen", null);
        if (startFen != null && !isValidFen(startFen)) startFen = null;

        long sessionId = pikafishEngineService.generateSessionId();
        // 将参数写入临时 Map 供 SSE 接口读取（简单的一次性 session 参数存储）
        streamAnalyzeParamStore.put(sessionId, new StreamAnalyzeParams(fen, depth, useBook, moves, startFen));
        return Map.of("sessionId", sessionId);
    }

    /**
     * 一次性参数存储（生命周期与 SSE 请求绑定，完成后自动清除）
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, StreamAnalyzeParams> streamAnalyzeParamStore =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record StreamAnalyzeParams(String fen, int depth, boolean useBook, List<String> moves, String startFen) {}

    /**
     * 4a. 流式 SSE 推流接口（配合 /stream-analyze/init 使用）
     */
    @GetMapping(value = "/stream-analyze/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnalyzeEvents(@RequestParam("sessionId") long sessionId) {
        StreamAnalyzeParams params = streamAnalyzeParamStore.remove(sessionId);
        if (params == null) {
            // sessionId 不存在或已过期
            SseEmitter errorEmitter = new SseEmitter(0L);
            try {
                errorEmitter.send(SseEmitter.event().name("error").data("无效的 sessionId"));
                errorEmitter.complete();
            } catch (Exception ignored) {}
            return errorEmitter;
        }
        return doStreamAnalyze(sessionId, params.fen(), params.depth(), params.useBook(), params.moves(), params.startFen());
    }

    /**
     * 4b. 保留旧的 GET 接口（向前兼容，不含 moves，短对局使用）
     */
    @GetMapping(value = "/stream-analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnalyze(
            @RequestParam(value = "fen") String fen,
            @RequestParam(value = "depth", required = false, defaultValue = "30") Integer depth,
            @RequestParam(value = "useBook", required = false, defaultValue = "true") Boolean useBook,
            @RequestParam(value = "moves", required = false) String movesStr,
            @RequestParam(value = "startFen", required = false) String startFen) {

        if (!isValidFen(fen)) fen = DEFAULT_FEN;
        if (startFen != null && !isValidFen(startFen)) startFen = null;

        List<String> moves = null;
        if (movesStr != null && !movesStr.isBlank()) {
            moves = Arrays.stream(movesStr.trim().split("\\s+"))
                    .filter(this::isValidUciMove)
                    .toList();
        }

        long sessionId = pikafishEngineService.generateSessionId();
        return doStreamAnalyze(sessionId, fen, depth, useBook, moves, startFen);
    }

    private SseEmitter doStreamAnalyze(long sessionId, String fen, int depth, boolean useBook,
                                        List<String> moves, String startFen) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5分钟

        // 监听客户端主动断开连接或超时，带会话ID精准取消，绝不误杀后续请求！
        emitter.onCompletion(() -> pikafishEngineService.cancelSearch(sessionId));
        emitter.onTimeout(() -> pikafishEngineService.cancelSearch(sessionId));
        emitter.onError(e -> pikafishEngineService.cancelSearch(sessionId));

        final List<String> finalMoves = moves;
        final String finalStartFen = startFen;
        final String finalFen = fen;
        final boolean finalUseBook = useBook;

        Thread.startVirtualThread(() -> {
            try {
                // 1. 先查开局库
                if (finalUseBook) {
                    List<BookMove> bookMoves = openingBookService.getBookMoves(finalFen);
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

                // 2. 检查是否有预热的 L1 缓存。如果有，0ms 先推送一条预热首帧给前端
                EngineAnalysisResult cached = pikafishEngineService.getCachedAnalysis(finalFen);
                if (cached != null) {
                    log.info("【流式推演预热】立即先推送 L1 缓存首帧 (0ms), 着法: {}, depth: {}", cached.getBestMove(), cached.getDepth());
                    if (cached.getBestMoveChinese() == null && cached.getBestMove() != null) {
                        cached.setBestMoveChinese(coordinateConverter.uciToChinese(finalFen, cached.getBestMove()));
                    }
                    if (cached.getPvMovesChinese() == null && cached.getPvMoves() != null) {
                        cached.setPvMovesChinese(coordinateConverter.convertMoveList(finalFen, cached.getPvMoves()));
                    }
                    if (cached.getPonderMove() != null && cached.getPonderMoveChinese() == null && cached.getBestMove() != null) {
                        String nextFen = coordinateConverter.applyMove(finalFen, cached.getBestMove());
                        cached.setPonderMoveChinese(coordinateConverter.uciToChinese(nextFen, cached.getPonderMove()));
                    }
                    try {
                        emitter.send(SseEmitter.event().name("analysis").data(cached));
                    } catch (Exception ignored) {}
                }

                // 3. 启动皮卡鱼流式深算
                pikafishEngineService.streamAnalyze(sessionId, finalFen, depth, snapshot -> {
                    try {
                        if (snapshot.getBestMove() != null && snapshot.getBestMoveChinese() == null) {
                            snapshot.setBestMoveChinese(coordinateConverter.uciToChinese(finalFen, snapshot.getBestMove()));
                        }
                        if (snapshot.getPvMoves() != null && snapshot.getPvMovesChinese() == null) {
                            snapshot.setPvMovesChinese(coordinateConverter.convertMoveList(finalFen, snapshot.getPvMoves()));
                        }
                        if (snapshot.getPonderMove() != null && snapshot.getPonderMoveChinese() == null && snapshot.getBestMove() != null) {
                            String nextFen = coordinateConverter.applyMove(finalFen, snapshot.getBestMove());
                            snapshot.setPonderMoveChinese(coordinateConverter.uciToChinese(nextFen, snapshot.getPonderMove()));
                        }
                        emitter.send(SseEmitter.event().name("analysis").data(snapshot));
                    } catch (Exception e) {
                        // 客户端已断开，精准终止本会话推演
                        pikafishEngineService.cancelSearch(sessionId);
                        throw new RuntimeException("SSE_CLIENT_DISCONNECTED", e);
                    }
                }, finalMoves, finalStartFen);

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

    // ==================== 安全参数提取工具方法 ====================

    /**
     * 从 Map 安全获取 String，类型不匹配时返回默认值
     */
    private String safeGetString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        if (val instanceof String s) return s;
        if (val != null) return val.toString();
        return defaultVal;
    }

    /**
     * 从 Map 安全获取 Integer，支持 Integer/Long/String 类型
     */
    private Integer safeGetInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Integer i) return i;
        if (val instanceof Long l) return l.intValue();
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * 从 Map 安全获取 boolean
     */
    private boolean safeGetBoolean(Map<String, Object> map, String key, boolean defaultVal) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return "true".equalsIgnoreCase(s);
        return defaultVal;
    }

    /**
     * 从 Map 安全获取 moves 列表，过滤非法格式的着法
     */
    @SuppressWarnings("unchecked")
    private List<String> safeGetMoves(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            return list.stream()
                    .filter(o -> o instanceof String)
                    .map(o -> (String) o)
                    .filter(this::isValidUciMove)
                    .toList();
        }
        return null;
    }
}
