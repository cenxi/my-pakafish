package com.pakafish.ai.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pakafish.ai.model.EngineAnalysisResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PikafishEngineService {

    @Value("${pikafish.engine-path}")
    private String enginePath;

    @Value("${pikafish.work-dir}")
    private String workDir;

    @Value("${pikafish.default-depth:15}")
    private int defaultDepth;

    @Value("${pikafish.default-movetime:1500}")
    private int defaultMovetime;

    @Value("${pikafish.threads:2}")
    private int threads;

    @Value("${pikafish.hash:64}")
    private int hashSize;

    private Process engineProcess;
    private BufferedWriter engineWriter;

    /**
     * 【Fix-B2】引擎输出异步读取队列：独立读线程将引擎每行输出放入队列，
     * 分析方法通过 poll(timeout) 消费，彻底避免 readLine() 阻塞导致超时检测失效。
     */
    private final BlockingQueue<String> engineOutputQueue = new LinkedBlockingQueue<>();
    private Thread engineReaderThread;

    /**
     * 【Fix-B1】统一使用 ReentrantLock，废弃所有 synchronized 修饰符，
     * 彻底消除 synchronized + ReentrantLock 混用导致的死锁风险。
     */
    private final ReentrantLock lock = new ReentrantLock();

    private volatile boolean isSearching = false;
    private final AtomicLong sessionSequence = new AtomicLong(0);

    /**
     * 【Fix-B3】activeSessionId 改为 volatile long（原始类型），
     * 用 -1L 表示无活动会话，彻底避免 Long 装箱 == 比较超过 127 后失效的 Bug。
     */
    private volatile long activeSessionId = -1L;

    public long generateSessionId() {
        return sessionSequence.incrementAndGet();
    }

    private final java.util.concurrent.ScheduledExecutorService watchdogExecutor =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "pikafish-watchdog");
                t.setDaemon(true);
                return t;
            });

    @org.springframework.beans.factory.annotation.Autowired
    private ChessCoordinateConverter coordinateConverter;

    /**
     * 【Fix-O1】L1 内存高速缓存：改用 Caffeine 替代 synchronizedMap(LinkedHashMap)。
     * Caffeine 提供并发安全、更低锁竞争的 LRU 实现，且 get/put 是原子操作，
     * 消除原来先 get 再 put 的 TOCTOU 竞态条件。
     * LRU 淘汰策略，最大保留 2000 个局面。
     */
    private final Cache<String, EngineAnalysisResult> analysisCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .build();

    /**
     * 规范化 FEN 串：去除半步计数与总回合数，保留局面布局与走子方，最大化同型局面缓存命中
     */
    public static String normalizeFen(String fen) {
        if (fen == null) return "";
        String[] parts = fen.trim().split("\\s+");
        if (parts.length >= 2) {
            return parts[0] + " " + parts[1];
        }
        return fen.trim();
    }

    /**
     * 从 L1 高速缓存中获取局面分析结果
     */
    public EngineAnalysisResult getCachedAnalysis(String fen) {
        if (fen == null) return null;
        String key = normalizeFen(fen);
        EngineAnalysisResult cached = analysisCache.getIfPresent(key);
        if (cached != null) {
            return copyResult(cached);
        }
        return null;
    }

    /**
     * 将分析结果存入 L1 缓存，并自动基于 PV 主要变例推导后续子局面进行预热
     */
    public void cacheAnalysis(String fen, EngineAnalysisResult result) {
        if (fen == null || result == null || result.getBestMove() == null) return;
        cacheResult(fen, result);
        deriveAndCacheChildPositions(fen, result);
    }

    /**
     * 基于 PV 变例树向下预热推导 1~2 步子局面：
     * P0 (红) 算出 PV = [m1, m2, m3, m4]
     * -> P1 = applyMove(P0, m1) (黑方走子，其最佳应手为 m2，PV 为 [m2, m3, m4]，深度为 D-1)
     * -> P2 = applyMove(P1, m2) (红方走子，最佳应手为 m3，PV 为 [m3, m4]，深度为 D-2)
     *
     * 【Fix-B5】修正预热子局面的评分方向：
     * P0 的 scoreCp 是红方绝对分（已经过 applyAbsoluteScore 翻转）。
     * P1 是对方视角，其绝对分 = -P0.scoreCp（对方优势取反）。
     * P2 回到同方视角，其绝对分 = P0.scoreCp（与 P0 一致）。
     * 同理 winRate = 100 - P0.winRate（对方）。
     * 不能把 P0 的绝对分直接赋给 P1，否则下次取出时 applyAbsoluteScore 会再翻转一次，变成错误值。
     */
    private void deriveAndCacheChildPositions(String fen, EngineAnalysisResult result) {
        if (coordinateConverter == null || result == null || result.getPvMoves() == null || result.getPvMoves().size() < 2) {
            return;
        }
        List<String> pv = result.getPvMoves();
        int currentDepth = result.getDepth() != null ? result.getDepth() : 10;
        if (currentDepth <= 2) return;
        // 只预热有实际引擎算力的高深度结果，避免缓存覆盖更优结果
        if (currentDepth < 10) return;

        // P0 的红方绝对分
        int p0RedScore = result.getScoreCp() != null ? result.getScoreCp() : 0;

        try {
            // 推导 P1: 对方回合 (下出 m1 后的局面)
            String m1 = pv.get(0);
            String m2 = pv.get(1);
            String fen1 = coordinateConverter.applyMove(fen, m1);
            List<String> pv1 = new java.util.ArrayList<>(pv.subList(1, pv.size()));
            String m3 = pv.size() > 2 ? pv.get(2) : null;

            // 【Fix-B5】P1 是对方（黑方）视角：红方绝对分取反
            int p1RedScore = -p0RedScore;
            double p1WinRate = result.getWinRate() != null ? (100.0 - result.getWinRate()) : 50.0;
            String p1AdvantageDesc = formatAdvantageText(Math.abs(p1RedScore), p1RedScore >= 0 ? "红方" : "黑方");
            String p1SideText = p1RedScore == 0 ? "均势 (0分)"
                    : (p1RedScore > 0 ? "红优 +" + p1RedScore + "分" : "黑优 +" + Math.abs(p1RedScore) + "分");

            EngineAnalysisResult r1 = EngineAnalysisResult.builder()
                    .bestMove(m2)
                    .bestMoveChinese(coordinateConverter.uciToChinese(fen1, m2))
                    .ponderMove(m3)
                    .scoreCp(p1RedScore)
                    .winRate(p1WinRate)
                    .depth(Math.max(1, currentDepth - 1))
                    .pvMoves(pv1)
                    .pvMovesChinese(coordinateConverter.convertMoveList(fen1, pv1))
                    .advantageDescription(p1AdvantageDesc)
                    .sideAdvantageText(p1SideText)
                    .fromBook(false)
                    .fromCache(true)
                    .build();

            if (m3 != null) {
                String fen2ForZh = coordinateConverter.applyMove(fen1, m2);
                r1.setPonderMoveChinese(coordinateConverter.uciToChinese(fen2ForZh, m3));
            }
            cacheResult(fen1, r1);

            // 推导 P2: 如果 PV 长度 >= 3 且深度 >= 4，进一步推导再下一回合
            if (pv.size() >= 3 && currentDepth >= 4) {
                String fen2 = coordinateConverter.applyMove(fen1, m2);
                List<String> pv2 = new java.util.ArrayList<>(pv.subList(2, pv.size()));
                String m4 = pv.size() > 3 ? pv.get(3) : null;

                // 【Fix-B5】P2 回到同方（红方）视角，红方绝对分与 P0 一致
                String p2AdvantageDesc = formatAdvantageText(Math.abs(p0RedScore), p0RedScore >= 0 ? "红方" : "黑方");
                String p2SideText = p0RedScore == 0 ? "均势 (0分)"
                        : (p0RedScore > 0 ? "红优 +" + p0RedScore + "分" : "黑优 +" + Math.abs(p0RedScore) + "分");

                EngineAnalysisResult r2 = EngineAnalysisResult.builder()
                        .bestMove(m3)
                        .bestMoveChinese(coordinateConverter.uciToChinese(fen2, m3))
                        .ponderMove(m4)
                        .scoreCp(p0RedScore)
                        .winRate(result.getWinRate())
                        .depth(Math.max(1, currentDepth - 2))
                        .pvMoves(pv2)
                        .pvMovesChinese(coordinateConverter.convertMoveList(fen2, pv2))
                        .advantageDescription(p2AdvantageDesc)
                        .sideAdvantageText(p2SideText)
                        .fromBook(false)
                        .fromCache(true)
                        .build();

                if (m4 != null) {
                    String fen3ForZh = coordinateConverter.applyMove(fen2, m3);
                    r2.setPonderMoveChinese(coordinateConverter.uciToChinese(fen3ForZh, m4));
                }
                cacheResult(fen2, r2);
            }
        } catch (Exception e) {
            log.debug("子节点PV预热解析跳过: {}", e.getMessage());
        }
    }

    private void cacheResult(String fen, EngineAnalysisResult result) {
        if (fen == null || result == null || result.getBestMove() == null) return;
        String key = normalizeFen(fen);
        // 【Fix-O1】Caffeine 的 get-if-absent 模式：原子性地只在深度更高时才更新
        analysisCache.asMap().merge(key, copyResult(result), (existing, incoming) -> {
            if (existing.getDepth() != null && incoming.getDepth() != null
                    && existing.getDepth() >= incoming.getDepth()) {
                return existing; // 保留深度更高的结果
            }
            return incoming;
        });
    }

    private EngineAnalysisResult copyResult(EngineAnalysisResult src) {
        if (src == null) return null;
        return EngineAnalysisResult.builder()
                .bestMove(src.getBestMove())
                .bestMoveChinese(src.getBestMoveChinese())
                .ponderMove(src.getPonderMove())
                .ponderMoveChinese(src.getPonderMoveChinese())
                .scoreCp(src.getScoreCp())
                .winRate(src.getWinRate())
                .depth(src.getDepth())
                .pvMoves(src.getPvMoves() != null ? new java.util.ArrayList<>(src.getPvMoves()) : null)
                .pvMovesChinese(src.getPvMovesChinese() != null ? new java.util.ArrayList<>(src.getPvMovesChinese()) : null)
                .advantageDescription(src.getAdvantageDescription())
                .sideAdvantageText(src.getSideAdvantageText())
                .fromBook(src.getFromBook())
                .fromCache(src.getFromCache() != null ? src.getFromCache() : true)
                .bookMoves(src.getBookMoves())
                .build();
    }

    /**
     * 强行中断当前正在运行的推演（仅当处于推演中时发送 stop）
     * 【Fix-B1】去掉 synchronized，改用 ReentrantLock 保护
     */
    public void stopCurrentSearch() {
        boolean needStop = false;
        lock.lock();
        try {
            if (isSearching) {
                needStop = true;
                // optional: reset searching flag here if appropriate
            }
        } finally {
            lock.unlock();
        }
        if (needStop) {
            try {
                sendCommandUnsafe("stop");
                log.info("【皮卡鱼指令】发送 stop 中断当前活动计算 (会话 #{})", activeSessionId);
            } catch (Exception e) {
                log.warn("发送 stop 异常", e);
            }
        }
    }

    /**
     * 带会话 ID 的精准取消：仅当活动会话匹配指定 session 时才执行 stop
     * 【Fix-B1 + Fix-B3】去掉 synchronized，使用 long 原始类型比较
     */
    public void cancelSearch(long sessionId) {
        lock.lock();
        try {
            // 【Fix-B3】activeSessionId 现为 long 原始类型，直接 == 比较安全
            if (activeSessionId == sessionId && isSearching) {
                log.info("【皮卡鱼取消会话】当前会话 #{} 被取消，发送 stop", sessionId);
                try {
                    sendCommandUnsafe("stop");
                } catch (Exception e) {
                    log.warn("取消会话发送 stop 异常", e);
                }
            } else {
                log.debug("【皮卡鱼忽略过期中断】会话 #{} 已结束或不匹配当前活动会话 #{}", sessionId, activeSessionId);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 确保引擎停止当前任何搜索，并清空管道直到引擎确认就绪 (isready -> readyok)
     * 调用此方法必须已持有 lock
     *
     * 【Fix-B2】改用 engineOutputQueue.poll(timeout) 替代阻塞 readLine()，
     * 超时检测真实有效；引擎读取线程独立运行，不会因阻塞卡死整个业务线程。
     */
    private void ensureEngineReady() {
        try {
            ensureEngine();
            if (isSearching) {
                sendCommandUnsafe("stop");
            }
            // 排空队列中的遗留输出
            engineOutputQueue.clear();
            sendCommandUnsafe("isready");

            long startTime = System.currentTimeMillis();
            while (true) {
                // 【Fix-B2】poll 带超时，不会永远阻塞
                String line = engineOutputQueue.poll(200, TimeUnit.MILLISECONDS);
                if (line == null) {
                    // 无数据，检查超时
                    if (System.currentTimeMillis() - startTime > 2500) {
                        log.warn("【皮卡鱼异常】等待 readyok 超时 (>2.5s)，强杀并重启引擎进程");
                        restartEngineProcess();
                        return;
                    }
                    continue;
                }
                line = line.trim();
                if ("readyok".equals(line)) {
                    log.debug("【皮卡鱼就绪】引擎已同步排空并返回 readyok");
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("【皮卡鱼异常】等待 readyok 被中断");
            restartEngineProcess();
        } catch (Exception e) {
            log.error("【皮卡鱼异常】同步 readyok 发生异常，重启引擎", e);
            restartEngineProcess();
        } finally {
            isSearching = false;
        }
    }

    public void restartEngineProcess() {
        // 【Fix-B1】不加 synchronized，调用方已持有 lock
        stopEngineReaderThread();
        try {
            if (engineWriter != null) {
                try { engineWriter.close(); } catch (Exception ignored) {}
                engineWriter = null;
            }
            if (engineProcess != null) {
                engineProcess.destroyForcibly();
                engineProcess.waitFor(1, TimeUnit.SECONDS);
                engineProcess = null;
            }
        } catch (Exception ignored) {
        }
        engineOutputQueue.clear();
        try {
            startEngine();
        } catch (Exception e) {
            log.error("重新启动皮卡鱼引擎失败", e);
        }
    }

    @PostConstruct
    public void init() {
        try {
            startEngine();
        } catch (Exception e) {
            log.error("初始化皮卡鱼引擎失败", e);
        }
    }

    /**
     * 【Fix-B2】启动引擎后，同时启动独立的异步读取线程，
     * 将引擎所有输出行放入 engineOutputQueue，业务线程通过 poll 消费。
     */
    private void startEngine() throws IOException {
        // 【Fix-B1】调用方（init 或 restartEngineProcess）不加锁，此处不用 synchronized
        File engineFile = new File(enginePath);
        if (!engineFile.exists()) {
            log.error("皮卡鱼引擎可执行文件不存在: {}", enginePath);
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(enginePath);
        pb.directory(new File(workDir));
        pb.redirectErrorStream(true);

        this.engineProcess = pb.start();
        this.engineWriter = new BufferedWriter(
                new OutputStreamWriter(engineProcess.getOutputStream(), StandardCharsets.UTF_8));

        // 【Fix-B2】启动异步读取线程
        startEngineReaderThread(
                new BufferedReader(new InputStreamReader(engineProcess.getInputStream(), StandardCharsets.UTF_8)));

        sendCommandUnsafe("uci");
        sendCommandUnsafe("setoption name Threads value " + threads);
        sendCommandUnsafe("setoption name Hash value " + hashSize);
        sendCommandUnsafe("isready");

        // 等待 readyok，用 poll 真实超时保护
        long start = System.currentTimeMillis();
        while (true) {
            try {
                String line = engineOutputQueue.poll(200, TimeUnit.MILLISECONDS);
                if (line == null) {
                    if (System.currentTimeMillis() - start > 5000) {
                        log.error("皮卡鱼引擎初始化等待 readyok 超时");
                        break;
                    }
                    continue;
                }
                if ("readyok".equals(line.trim())) {
                    log.info("皮卡鱼引擎初始化成功: {}", enginePath);
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 【Fix-B2】启动独立守护线程持续读取引擎 stdout，放入 BlockingQueue
     */
    private void startEngineReaderThread(BufferedReader reader) {
        // Ensure any previous reader thread is stopped before starting a new one
        stopEngineReaderThread();
        engineReaderThread = new Thread(() -> {
            try {
                String line;
                while (!Thread.currentThread().isInterrupted()) {
                    // Use ready() to avoid blocking readLine on interrupt
                    if (reader.ready()) {
                        line = reader.readLine();
                        if (line == null) {
                            // End of stream, exit loop
                            break;
                        }
                        // Offer to queue with timeout; if full, discard oldest entry
                        if (!engineOutputQueue.offer(line, 100, TimeUnit.MILLISECONDS)) {
                            engineOutputQueue.poll();
                            engineOutputQueue.offer(line);
                        }
                    } else {
                        // No data available, sleep briefly to avoid busy-wait
                        Thread.sleep(50);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    log.warn("引擎读取线程异常退出: {}", e.getMessage());
                }
            } finally {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }, "pikafish-reader");
        engineReaderThread.setDaemon(true);
        engineReaderThread.start();
    }

    private void stopEngineReaderThread() {
        if (engineReaderThread != null && engineReaderThread.isAlive()) {
            engineReaderThread.interrupt();
            try {
                engineReaderThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            engineReaderThread = null;
        }
    }

    /**
     * 发送命令到引擎（不加锁，调用方负责持有 lock 或在安全上下文中调用）
     */
    private void sendCommandUnsafe(String cmd) throws IOException {
        if (engineWriter != null) {
            engineWriter.write(cmd + "\n");
            engineWriter.flush();
        }
    }

    // --------------- 编译模式常量 ---------------
    private static final Pattern DEPTH_PATTERN = Pattern.compile("\\bdepth\\s+(\\d+)");
    private static final Pattern SCORE_CP_PATTERN = Pattern.compile("\\bscore\\s+cp\\s+(-?\\d+)");
    private static final Pattern SCORE_MATE_PATTERN = Pattern.compile("\\bscore\\s+mate\\s+(-?\\d+)");
    private static final Pattern PV_PATTERN = Pattern.compile("\\bpv\\s+(.*)$");

    public EngineAnalysisResult analyzePosition(String fen, Integer movetimeLimit) {
        return analyzePosition(fen, null, movetimeLimit, null, null);
    }

    public EngineAnalysisResult analyzePosition(String fen, Integer depthLimit, Integer movetimeLimit) {
        return analyzePosition(fen, depthLimit, movetimeLimit, null, null);
    }

    /**
     * 同步分析（单次出招计算，带严格超时防护与管道排空，支持对局着法历史注入以准确判定重复/长将/长捉）
     * 【Fix-B1】全部改用 ReentrantLock，无 synchronized
     */
    public EngineAnalysisResult analyzePosition(String fen, Integer depthLimit, Integer movetimeLimit, List<String> moves, String startFen) {
        long sessionId = generateSessionId();
        log.info("【皮卡鱼单次分析】准备启动会话 #{}, FEN: {}, 历史步数: {}", sessionId, fen, moves != null ? moves.size() : 0);

        // 抢占式：如果正在进行后台推演，先通知当前推演尽快停下
        stopCurrentSearch();

        try {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                log.warn("【会话 #{}】获取皮卡鱼引擎锁超时，强行 stop 并重试", sessionId);
                stopCurrentSearch();
                if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                    log.error("【会话 #{}】二次获取锁失败，强杀重启引擎并最后尝试", sessionId);
                    restartEngineProcess();
                    if (!lock.tryLock(2, TimeUnit.SECONDS)) {
                        return null;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        java.util.concurrent.ScheduledFuture<?> watchdog = null;
        try {
            // 拿到锁后，确保管道排空且引擎彻底就绪
            ensureEngineReady();

            // 【Fix-B3】直接赋值 long，无装箱
            activeSessionId = sessionId;
            isSearching = true;

            // 构建 position 指令
            if (moves != null && !moves.isEmpty()) {
                if (startFen != null && !startFen.isBlank() && !startFen.startsWith("rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR")) {
                    sendCommandUnsafe("position fen " + startFen + " moves " + String.join(" ", moves));
                } else {
                    sendCommandUnsafe("position startpos moves " + String.join(" ", moves));
                }
            } else {
                sendCommandUnsafe("position fen " + fen);
            }

            // 安全深度与思考时间约束
            int safeDepth = (depthLimit != null && depthLimit > 0) ? Math.min(depthLimit, 30) : defaultDepth;
            int safeMovetime = (movetimeLimit != null && movetimeLimit > 0) ? movetimeLimit : defaultMovetime;

            StringBuilder goCmd = new StringBuilder("go");
            goCmd.append(" depth ").append(safeDepth);
            goCmd.append(" movetime ").append(safeMovetime);

            log.info("【皮卡鱼指令】【会话 #{}】单次分析发送: {}", sessionId, goCmd);
            sendCommandUnsafe(goCmd.toString());

            // 超时防护：超过期望时间 + 5s 强制杀进程解卡
            int watchdogTimeout = safeMovetime + 5000;
            watchdog = watchdogExecutor.schedule(() -> {
                log.error("【皮卡鱼严重超时】【会话 #{}】单次分析超过 {} ms 未能返回 bestmove，强制重启引擎解除阻塞", sessionId, watchdogTimeout);
                lock.lock();
                try {
                    restartEngineProcess();
                } finally {
                    lock.unlock();
                }
            }, watchdogTimeout, TimeUnit.MILLISECONDS);

            return readUntilBestmove(sessionId, fen, null, safeMovetime + 6000);
        } catch (Exception e) {
            log.error("【皮卡鱼分析异常】【会话 #" + sessionId + "】", e);
            return null;
        } finally {
            if (watchdog != null) {
                watchdog.cancel(false);
            }
            // 【Fix-B3】long 比较无装箱问题
            if (activeSessionId == sessionId) {
                activeSessionId = -1L;
            }
            isSearching = false;
            lock.unlock();
        }
    }

    public EngineAnalysisResult streamAnalyze(String fen, int maxDepth, Consumer<EngineAnalysisResult> onProgress) {
        return streamAnalyze(generateSessionId(), fen, maxDepth, onProgress, null, null);
    }

    public EngineAnalysisResult streamAnalyze(long sessionId, String fen, int maxDepth, Consumer<EngineAnalysisResult> onProgress) {
        return streamAnalyze(sessionId, fen, maxDepth, onProgress, null, null);
    }

    /**
     * 流式分析（SSE 模式）：发送 go depth <safeDepth>，每推深一层通过 callback 推送中间结果
     * 【Fix-B1】全部改用 ReentrantLock，无 synchronized
     */
    public EngineAnalysisResult streamAnalyze(long sessionId, String fen, int maxDepth, Consumer<EngineAnalysisResult> onProgress, List<String> moves, String startFen) {
        log.info("【皮卡鱼流式推演】准备启动会话 #{}, FEN: {}, 历史步数: {}", sessionId, fen, moves != null ? moves.size() : 0);

        // 抢占式：如果正在运行前一个推演，立刻 stop 中断它
        stopCurrentSearch();

        try {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                log.warn("【会话 #{}】获取流式推演锁超时，强行 stop 并重试", sessionId);
                stopCurrentSearch();
                if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                    log.error("【会话 #{}】二次获取流式推演锁失败", sessionId);
                    return null;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        try {
            // 拿到锁后，确保管道排空且引擎彻底就绪
            ensureEngineReady();

            // 【Fix-B3】直接赋值 long
            activeSessionId = sessionId;
            isSearching = true;

            // 限制流式推演最大深度在 15~35 层区间，杜绝 128 层天文运算
            int safeDepth = Math.min(Math.max(maxDepth, 15), 35);
            if (moves != null && !moves.isEmpty()) {
                if (startFen != null && !startFen.isBlank() && !startFen.startsWith("rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR")) {
                    sendCommandUnsafe("position fen " + startFen + " moves " + String.join(" ", moves));
                } else {
                    sendCommandUnsafe("position startpos moves " + String.join(" ", moves));
                }
            } else {
                sendCommandUnsafe("position fen " + fen);
            }
            log.info("【皮卡鱼指令】【会话 #{}】流式推演启动: depth {}", sessionId, safeDepth);
            sendCommandUnsafe("go depth " + safeDepth);

            // 流式推演超时：深度 35 最多给 5 分钟
            return readUntilBestmove(sessionId, fen, onProgress, 300_000);
        } catch (Exception e) {
            log.error("【皮卡鱼流式分析异常】【会话 #" + sessionId + "】", e);
            return null;
        } finally {
            // 【Fix-B3】long 比较
            if (activeSessionId == sessionId) {
                activeSessionId = -1L;
            }
            isSearching = false;
            lock.unlock();
        }
    }

    /**
     * 【Fix-B2】从 engineOutputQueue 持续消费引擎输出，解析并回调，直到遇到 bestmove 或超时。
     * @param sessionId   当前会话 ID
     * @param onProgress  若非 null，每次 info depth 行解析后回调推送中间结果
     * @param timeoutMs   整体超时毫秒数（防止流式推演永久运行）
     */
    private EngineAnalysisResult readUntilBestmove(long sessionId, String fen,
                                                    Consumer<EngineAnalysisResult> onProgress,
                                                    long timeoutMs) throws InterruptedException {
        EngineAnalysisResult result = new EngineAnalysisResult();
        result.setFromCache(false);
        result.setFromBook(false);
        String lastPvLine = null;
        int lastReportedDepth = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                log.warn("【皮卡鱼超时】【会话 #{}】readUntilBestmove 超过 {} ms，强制返回当前结果", sessionId, timeoutMs);
                break;
            }
            // 【Fix-B2】poll 带真实超时，不会永远阻塞
            String line = engineOutputQueue.poll(Math.min(remaining, 500), TimeUnit.MILLISECONDS);
            if (line == null) continue; // 超时间隙，继续等

            line = line.trim();
            if (line.startsWith("info ")) {
                // 跳过 upperbound/lowerbound 的非最终行
                if (line.contains(" upperbound") || line.contains(" lowerbound")) continue;

                Matcher mDepth = DEPTH_PATTERN.matcher(line);
                if (mDepth.find()) {
                    result.setDepth(Integer.parseInt(mDepth.group(1)));
                }

                Matcher mScore = SCORE_CP_PATTERN.matcher(line);
                if (mScore.find()) {
                    int cp = Integer.parseInt(mScore.group(1));
                    result.setScoreCp(cp);
                    result.setWinRate(calculateWinRate(cp));
                } else {
                    Matcher mMate = SCORE_MATE_PATTERN.matcher(line);
                    if (mMate.find()) {
                        int mateIn = Integer.parseInt(mMate.group(1));
                        int cp = mateIn > 0 ? 30000 - mateIn * 100 : -30000 - mateIn * 100;
                        result.setScoreCp(cp);
                        result.setWinRate(mateIn > 0 ? 99.9 : 0.1);
                    }
                }

                Matcher mPv = PV_PATTERN.matcher(line);
                if (mPv.find()) {
                    lastPvLine = mPv.group(1);
                    result.setPvMoves(Arrays.asList(lastPvLine.split("\\s+")));
                    if (!result.getPvMoves().isEmpty()) {
                        result.setBestMove(result.getPvMoves().get(0));
                    }
                }

                // 有新的完整深度数据时回调
                if (onProgress != null && result.getDepth() != null && result.getDepth() > lastReportedDepth
                        && result.getScoreCp() != null) {
                    lastReportedDepth = result.getDepth();
                    // 构建中间快照（含红黑绝对分值转换）
                    EngineAnalysisResult snapshot = buildSnapshot(result, fen);
                    if (snapshot.getDepth() != null && snapshot.getDepth() >= 12) {
                        cacheAnalysis(fen, snapshot);
                    }
                    try {
                        onProgress.accept(snapshot);
                    } catch (Exception e) {
                        // 客户端断开连接（Broken pipe），发送 stop 中断本会话推演并继续读完 bestmove 排空管道！
                        log.info("【SSE客户端已断开】【会话 #{}】发送 stop 中断推演并排空管道", sessionId);
                        cancelSearch(sessionId);
                    }
                }
            } else if (line.startsWith("bestmove ")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    result.setBestMove(parts[1]);
                }
                if (parts.length >= 4 && "ponder".equalsIgnoreCase(parts[2])) {
                    result.setPonderMove(parts[3]);
                }
                break;
            }
        }

        if (lastPvLine != null) {
            log.debug("【皮卡鱼推演】【会话 #{}】深度 {} 最终主要变例: {}", sessionId, result.getDepth(), lastPvLine);
            result.setPvMoves(Arrays.asList(lastPvLine.split("\\s+")));
        } else if (result.getBestMove() != null) {
            result.setPvMoves(List.of(result.getBestMove()));
        }

        if (result.getPonderMove() == null && result.getPvMoves() != null && result.getPvMoves().size() >= 2) {
            result.setPonderMove(result.getPvMoves().get(1));
        }

        // 转换为红方绝对分值
        applyAbsoluteScore(result, fen);

        // 填充中文记谱
        if (coordinateConverter != null) {
            if (result.getBestMove() != null) {
                result.setBestMoveChinese(coordinateConverter.uciToChinese(fen, result.getBestMove()));
            }
            if (result.getPvMoves() != null && !result.getPvMoves().isEmpty()) {
                result.setPvMovesChinese(coordinateConverter.convertMoveList(fen, result.getPvMoves()));
            }
            if (result.getPonderMove() != null && result.getBestMove() != null) {
                String nextFen = coordinateConverter.applyMove(fen, result.getBestMove());
                result.setPonderMoveChinese(coordinateConverter.uciToChinese(nextFen, result.getPonderMove()));
            }
        }

        // 存入 L1 快速缓存并预热推导子局面
        cacheAnalysis(fen, result);
        return result;
    }

    /** 构建中间快照用于 SSE 推送 */
    private EngineAnalysisResult buildSnapshot(EngineAnalysisResult src, String fen) {
        EngineAnalysisResult snap = new EngineAnalysisResult();
        snap.setDepth(src.getDepth());
        snap.setScoreCp(src.getScoreCp());
        snap.setWinRate(src.getWinRate());
        snap.setBestMove(src.getBestMove());
        snap.setPvMoves(src.getPvMoves());
        if (src.getPvMoves() != null && src.getPvMoves().size() >= 2) {
            snap.setPonderMove(src.getPvMoves().get(1));
        }
        snap.setFromBook(false);
        snap.setFromCache(false);
        applyAbsoluteScore(snap, fen);
        return snap;
    }

    /** 将引擎返回的相对分值转换为红方绝对分值并填充文字描述 */
    private void applyAbsoluteScore(EngineAnalysisResult result, String fen) {
        if (result.getScoreCp() == null) return;
        boolean isRedTurn = !fen.contains(" b ");
        int rawScore = result.getScoreCp();
        int redScore = isRedTurn ? rawScore : -rawScore;
        result.setScoreCp(redScore);
        result.setWinRate(calculateWinRate(redScore));

        if (redScore == 0) {
            result.setSideAdvantageText("均势 (0分)");
            result.setAdvantageDescription("局势胶着，均势抗衡");
        } else if (redScore > 0) {
            result.setSideAdvantageText("红优 +" + redScore + "分");
            result.setAdvantageDescription(formatAdvantageText(redScore, "红方"));
        } else {
            result.setSideAdvantageText("黑优 +" + Math.abs(redScore) + "分");
            result.setAdvantageDescription(formatAdvantageText(Math.abs(redScore), "黑方"));
        }
    }

    private void ensureEngine() throws IOException {
        if (engineProcess == null || !engineProcess.isAlive()) {
            log.warn("皮卡鱼引擎进程未存活，正在重新拉起...");
            startEngine();
        }
    }

    private double calculateWinRate(int cp) {
        double winRate = 1.0 / (1.0 + Math.pow(10.0, -cp / 400.0));
        return Math.round(winRate * 1000.0) / 10.0;
    }

    private String formatAdvantageText(int score, String dominantSide) {
        if (score <= 50) return dominantSide + "稍占主动";
        else if (score <= 200) return dominantSide + "握有微弱优势";
        else if (score <= 600) return dominantSide + "握有明显优势";
        else return dominantSide + "胜券在握 (胜势)";
    }

    @PreDestroy
    public void destroy() {
        try {
            watchdogExecutor.shutdownNow();
            stopEngineReaderThread();
            if (engineWriter != null) {
                try {
                    engineWriter.write("quit\n");
                    engineWriter.flush();
                } catch (Exception ignored) {}
            }
            if (engineProcess != null) {
                engineProcess.waitFor(1, TimeUnit.SECONDS);
                engineProcess.destroyForcibly();
            }
        } catch (Exception e) {
            log.error("关闭皮卡鱼引擎出错", e);
        }
    }
}
