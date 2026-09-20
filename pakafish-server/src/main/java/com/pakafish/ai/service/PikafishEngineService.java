package com.pakafish.ai.service;

import com.pakafish.ai.model.EngineAnalysisResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

    private final Object lock = new Object();

    @PostConstruct
    public void init() {
        try {
            startEngine();
        } catch (Exception e) {
            log.error("初始化皮卡鱼引擎失败", e);
        }
    }

    private void startEngine() throws IOException {
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

        // 等待 readyok
        String line;
        while ((line = engineReader.readLine()) != null) {
            if ("readyok".equals(line.trim())) {
                log.info("皮卡鱼引擎初始化成功: {}", enginePath);
                break;
            }
        }
    }

    private void sendCommand(String cmd) throws IOException {
        if (engineWriter != null) {
            engineWriter.write(cmd + "\n");
            engineWriter.flush();
        }
    }

    public EngineAnalysisResult analyzePosition(String fen, Integer movetimeLimit) {
        return analyzePosition(fen, null, movetimeLimit);
    }

    /**
     * 同步分析当前 FEN 局面 (支持动态指定深度与思考时间)
     *
     * @param fen FEN 局面串
     * @param depthLimit 目标推演深度（层），若<=0则不限深度或使用默认
     * @param movetimeLimit 思考限时(ms)，若<=0则使用默认
     * @return 引擎分析结果
     */
    public EngineAnalysisResult analyzePosition(String fen, Integer depthLimit, Integer movetimeLimit) {
        synchronized (lock) {
            try {
                if (engineProcess == null || !engineProcess.isAlive()) {
                    log.warn("皮卡鱼引擎进程未存活，正在重新拉起...");
                    startEngine();
                }

                sendCommand("position fen " + fen);

                StringBuilder goCmd = new StringBuilder("go");
                if (depthLimit != null && depthLimit > 0) {
                    goCmd.append(" depth ").append(depthLimit);
                }
                if (movetimeLimit != null && movetimeLimit > 0) {
                    goCmd.append(" movetime ").append(movetimeLimit);
                } else if (depthLimit == null || depthLimit <= 0) {
                    goCmd.append(" movetime ").append(defaultMovetime);
                }

                sendCommand(goCmd.toString());

                EngineAnalysisResult result = new EngineAnalysisResult();
                String line;
                String lastPvLine = null;

                Pattern depthPattern = Pattern.compile("\\bdepth\\s+(\\d+)");
                Pattern scoreCpPattern = Pattern.compile("\\bscore\\s+cp\\s+(-?\\d+)");
                Pattern scoreMatePattern = Pattern.compile("\\bscore\\s+mate\\s+(-?\\d+)");
                Pattern pvPattern = Pattern.compile("\\bpv\\s+(.*)$");

                while ((line = engineReader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("info ")) {
                        Matcher mDepth = depthPattern.matcher(line);
                        if (mDepth.find()) {
                            result.setDepth(Integer.parseInt(mDepth.group(1)));
                        }

                        Matcher mScore = scoreCpPattern.matcher(line);
                        if (mScore.find()) {
                            int cp = Integer.parseInt(mScore.group(1));
                            result.setScoreCp(cp);
                            result.setWinRate(calculateWinRate(cp));
                        } else {
                            Matcher mMate = scoreMatePattern.matcher(line);
                            if (mMate.find()) {
                                int mateIn = Integer.parseInt(mMate.group(1));
                                int cp = mateIn > 0 ? 30000 - mateIn * 100 : -30000 - mateIn * 100;
                                result.setScoreCp(cp);
                                result.setWinRate(mateIn > 0 ? 99.9 : 0.1);
                            }
                        }

                        Matcher mPv = pvPattern.matcher(line);
                        if (mPv.find()) {
                            lastPvLine = mPv.group(1);
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

                // 局势优势描述
                if (result.getScoreCp() != null) {
                    // 引擎返回多少分就是多少分，不做任何阈值拦截
                    int score = result.getScoreCp();
                    String sideText;
                    if (score == 0) {
                        sideText = "均势 (0分)";
                    } else if (score > 0) {
                        sideText = "红优 +" + score + "分";
                    } else {
                        sideText = "黑优 +" + Math.abs(score) + "分";
                    }
                    result.setSideAdvantageText(sideText);
                    result.setAdvantageDescription(formatAdvantage(score));
                }

                return result;
            } catch (Exception e) {
                log.error("皮卡鱼分析异常", e);
                return null;
            }
        }
    }

    private double calculateWinRate(int cp) {
        // 基于经典 Elo 胜率公式映射
        double winRate = 1.0 / (1.0 + Math.pow(10.0, -cp / 400.0));
        return Math.round(winRate * 1000.0) / 10.0;
    }

    private String formatAdvantage(int cp) {
        if (Math.abs(cp) <= 50) {
            return "势均力敌，局势胶着";
        } else if (cp > 50 && cp <= 200) {
            return "当前方稍占主动";
        } else if (cp > 200 && cp <= 600) {
            return "当前方握有明显优势";
        } else if (cp > 600) {
            return "当前方胜券在握 (胜势)";
        } else if (cp < -50 && cp >= -200) {
            return "当前方略处下风";
        } else if (cp < -200 && cp >= -600) {
            return "当前方明显被动";
        } else {
            return "当前方面临败局 (严重劣势)";
        }
    }

    @PreDestroy
    public void destroy() {
        try {
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
