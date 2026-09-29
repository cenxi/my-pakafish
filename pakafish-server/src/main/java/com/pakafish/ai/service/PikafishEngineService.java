package com.pakafish.ai.service;

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
import java.util.concurrent.TimeUnit;
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
    private BufferedReader engineReader;

    private final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
    private volatile boolean isSearching = false;

    private final java.util.concurrent.ScheduledExecutorService watchdogExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pikafish-watchdog");
        t.setDaemon(true);
        return t;
    });

    /**
     * 强行中断当前正在运行的推演（使引擎立即吐出当前深度的 bestmove）
     */
    public void stopCurrentSearch() {
        try {
            sendCommand("stop");
            log.debug("【皮卡鱼指令】已发送 stop 中断计算");
        } catch (Exception e) {
            log.warn("发送 stop 异常", e);
        }
    }

    /**
     * 确保引擎停止当前任何搜索，并清空管道直到引擎确认就绪 (isready -> readyok)
     * 调用此方法必须持有 lock
     */
    public synchronized void ensureEngineReady() {
        try {
            ensureEngine();
            sendCommand("stop");
            sendCommand("isready");

            long startTime = System.currentTimeMillis();
            String line;
            while ((line = engineReader.readLine()) != null) {
                line = line.trim();
                if ("readyok".equals(line)) {
                    log.debug("【皮卡鱼就绪】引擎已同步排空并返回 readyok");
                    return;
                }
                // 超时保护：如果 2.5 秒内未等到 readyok，说明引擎进程可能已异常卡死，直接强杀重启
                if (System.currentTimeMillis() - startTime > 2500) {
                    log.warn("【皮卡鱼异常】等待 readyok 超时 (>2.5s)，强杀并重启引擎进程");
                    restartEngineProcess();
                    return;
                }
            }
            log.warn("【皮卡鱼异常】读取到 EOF，重启引擎进程");
            restartEngineProcess();
        } catch (Exception e) {
            log.error("【皮卡鱼异常】同步 readyok 发生异常，重启引擎", e);
            restartEngineProcess();
        } finally {
            isSearching = false;
        }
    }

    public synchronized void restartEngineProcess() {
        try {
            if (engineProcess != null) {
                engineProcess.destroyForcibly();
                engineProcess.waitFor(1, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
        }
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

    private synchronized void startEngine() throws IOException {
        File engineFile = new File(enginePath);
        if (!engineFile.exists()) {
            log.error("皮卡鱼引擎可执行文件不存在: {}", enginePath);
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(enginePath);
        pb.directory(new File(workDir));
        pb.redirectErrorStream(true);

        this.engineProcess = pb.start();
        this.engineWriter = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream(), StandardCharsets.UTF_8));
        this.engineReader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream(), StandardCharsets.UTF_8));

        sendCommand("uci");
        sendCommand("setoption name Threads value " + threads);
        sendCommand("setoption name Hash value " + hashSize);
        sendCommand("isready");

        long start = System.currentTimeMillis();
        String line;
        while ((line = engineReader.readLine()) != null) {
            if ("readyok".equals(line.trim())) {
                log.info("皮卡鱼引擎初始化成功: {}", enginePath);
                break;
            }
            if (System.currentTimeMillis() - start > 5000) {
                log.error("皮卡鱼引擎初始化等待 readyok 超时");
                break;
            }
        }
    }

    private synchronized void sendCommand(String cmd) throws IOException {
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
    private static final Pattern SELDEPTH_PATTERN = Pattern.compile("\\bseldepth\\s+(\\d+)");

    public EngineAnalysisResult analyzePosition(String fen, Integer movetimeLimit) {
        return analyzePosition(fen, null, movetimeLimit);
    }

    /**
     * 同步分析（单次出招计算，带严格超时防护与管道排空）
     */
    public EngineAnalysisResult analyzePosition(String fen, Integer depthLimit, Integer movetimeLimit) {
        // 抢占式：如果正在进行后台推演，先发 stop 唤醒并抢锁
        stopCurrentSearch();

        try {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                log.warn("获取皮卡鱼引擎锁超时，强行 stop 并重试");
                stopCurrentSearch();
                if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                    log.error("二次获取锁失败，强杀重启引擎并最后尝试");
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

            sendCommand("position fen " + fen);

            // 安全深度与思考时间约束
            int safeDepth = (depthLimit != null && depthLimit > 0) ? Math.min(depthLimit, 30) : defaultDepth;
            int safeMovetime = (movetimeLimit != null && movetimeLimit > 0) ? movetimeLimit : defaultMovetime;

            StringBuilder goCmd = new StringBuilder("go");
            goCmd.append(" depth ").append(safeDepth);
            goCmd.append(" movetime ").append(safeMovetime);

            log.info("【皮卡鱼指令】单次分析发送: {}", goCmd);
            sendCommand(goCmd.toString());
            isSearching = true;

            // 超时防护：超过期望时间 + 5s 强制杀进程解卡
            int watchdogTimeout = safeMovetime + 5000;
            watchdog = watchdogExecutor.schedule(() -> {
                log.error("【皮卡鱼严重超时】单次分析超过 {} ms 未能返回 bestmove，强制重启引擎解除阻塞", watchdogTimeout);
                restartEngineProcess();
            }, watchdogTimeout, TimeUnit.MILLISECONDS);

            EngineAnalysisResult result = readUntilBestmove(fen, null);
            isSearching = false;
            return result;
        } catch (Exception e) {
            isSearching = false;
            log.error("皮卡鱼分析异常", e);
            return null;
        } finally {
            if (watchdog != null) {
                watchdog.cancel(false);
            }
            isSearching = false;
            lock.unlock();
        }
    }

    /**
     * 流式分析（SSE 模式）：发送 go depth <safeDepth>，每推深一层通过 callback 推送中间结果，
     * 具备抢占式中断与排空能力，新请求进入时自动打断旧推演释放锁。
     */
    public EngineAnalysisResult streamAnalyze(String fen, int maxDepth, Consumer<EngineAnalysisResult> onProgress) {
        // 抢占式：如果正在运行前一个推演，立刻 stop 中断它
        stopCurrentSearch();

        try {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                log.warn("获取流式推演锁超时，强行 stop 并重试");
                stopCurrentSearch();
                if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                    log.error("二次获取流式推演锁失败");
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

            // 限制流式推演最大深度在 15~35 层区间，杜绝 128 层天文运算
            int safeDepth = Math.min(Math.max(maxDepth, 15), 35);
            sendCommand("position fen " + fen);
            log.info("【皮卡鱼指令】流式推演启动: depth {}", safeDepth);
            sendCommand("go depth " + safeDepth);
            isSearching = true;

            EngineAnalysisResult result = readUntilBestmove(fen, onProgress);
            isSearching = false;
            return result;
        } catch (Exception e) {
            isSearching = false;
            log.error("皮卡鱼流式分析异常", e);
            return null;
        } finally {
            isSearching = false;
            lock.unlock();
        }
    }

    /**
     * 从引擎输出流中持续读取 info 行，解析并回调，直到遇到 bestmove。
     * @param onProgress 若非 null，每次 info depth 行解析后回调推送中间结果
     */
    private EngineAnalysisResult readUntilBestmove(String fen, Consumer<EngineAnalysisResult> onProgress) throws IOException {
        EngineAnalysisResult result = new EngineAnalysisResult();
        String lastPvLine = null;
        int lastReportedDepth = 0;

        String line;
        while ((line = engineReader.readLine()) != null) {
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
                    try {
                        onProgress.accept(snapshot);
                    } catch (Exception e) {
                        // 客户端断开连接（Broken pipe），立刻中断引擎计算并彻底排空管道！
                        log.info("【SSE客户端已断开】立即中断并排空皮卡鱼推演管道");
                        ensureEngineReady();
                        break;
                    }
                }
            } else if (line.startsWith("bestmove ")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    result.setBestMove(parts[1]);
                }
                break;
            }
        }

        if (lastPvLine != null) {
            result.setPvMoves(Arrays.asList(lastPvLine.split("\\s+")));
        } else if (result.getBestMove() != null) {
            result.setPvMoves(List.of(result.getBestMove()));
        }

        // 转换为红方绝对分值
        applyAbsoluteScore(result, fen);
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
        snap.setFromBook(false);
        applyAbsoluteScore(snap, fen);
        return snap;
    }

    /** 将引擎返回的相对分值转换为红方绝对分值并填充文字描述 */
    private void applyAbsoluteScore(EngineAnalysisResult result, String fen) {
        if (result.getScoreCp() == null) return;
        boolean isRedTurn = !fen.contains(" b ");
        int redScore = isRedTurn ? result.getScoreCp() : -result.getScoreCp();
        result.setScoreCp(redScore);
        result.setWinRate(calculateWinRate(isRedTurn ? result.getScoreCp() : -result.getScoreCp()));

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
            if (engineWriter != null) {
                engineWriter.write("quit\n");
                engineWriter.flush();
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
