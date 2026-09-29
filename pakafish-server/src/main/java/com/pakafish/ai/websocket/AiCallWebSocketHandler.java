package com.pakafish.ai.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakafish.ai.model.EngineAnalysisResult;
import com.pakafish.ai.service.ChessCoachChatService;
import com.pakafish.ai.service.ChessCoordinateConverter;
import com.pakafish.ai.service.EdgeTtsService;
import com.pakafish.ai.service.FunAsrClientService;
import com.pakafish.ai.service.PikafishEngineService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 特级大师打电话（实时全双工语音通话）WebSocket 处理器
 * 职责：
 * 1. 接收前端实时 PCM 语音流 -> 转发给 FunASR 容器实时转文字；
 * 2. 识别到整句后 -> 触发大模型生成教练回复（结合当前棋盘 FEN 与皮卡鱼客观分析）；
 * 3. 大模型回复切句流式送入 Edge-TTS 合成 MP3 -> 推送给前端实时播放；
 * 4. 监听用户说话打断（Barge-in）：一旦用户在 AI 说话时开口，立即重置当前播报状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCallWebSocketHandler extends AbstractWebSocketHandler {

    private final FunAsrClientService funAsrClientService;
    private final StreamingChatLanguageModel streamingChatModel;
    private final PikafishEngineService pikafishEngineService;
    private final ChessCoordinateConverter coordinateConverter;
    private final EdgeTtsService edgeTtsService;

    /**
     * 【Fix-O4】注入 Spring 管理的 ObjectMapper Bean，统一配置，避免各处 new ObjectMapper()
     */
    private final ObjectMapper objectMapper;

    // 记录每个前端会话的状态信息
    private final Map<String, SessionContext> contextMap = new ConcurrentHashMap<>();

    /**
     * 【Fix-B4 优化】将固定 4 线程池改为虚拟线程执行器（JDK 21+）：
     * - 虚拟线程在 TTS 等 I/O 阻塞时挂起但不占用平台线程，并发能力大幅提升。
     * - TTS 合成使用独立的 IO 线程池，与 LLM 回调线程分离，避免互相阻塞。
     */
    private final java.util.concurrent.ExecutorService aiExecutor =
            Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 【Fix-B5 / O5 TTS 有序队列】每个 session 对应一个 TTS 合成任务队列，
     * 确保音频片段严格按顺序提交给前端，即使多个片段并行合成完成也能顺序推送。
     * key: sessionId, value: TTS 序列化推送任务队列
     */
    private final Map<String, BlockingQueue<Runnable>> ttsQueues = new ConcurrentHashMap<>();

    /**
     * 【Fix-B4】每个 session 独立的 WS 发送锁对象，消除全局 synchronized 瓶颈。
     * 锁粒度从"整个 Handler 实例"缩小到"单个 session"。
     */
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

    private static class SessionContext {
        String currentFen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
        String historyMoves = "";
        String voice = EdgeTtsService.DEFAULT_VOICE;
        String rate = "+0%";
        AtomicBoolean isAiSpeaking = new AtomicBoolean(false);
        AtomicBoolean interruptRequested = new AtomicBoolean(false);
    }

    private static final String CALL_SYSTEM_PROMPT = """
        你是一位亲切威严的中国象棋特级大师教练。你正在通过【语音电话】与学员面对面交流。
        由于这是实时语音通话，请务必遵守以下原则：
        1. 回答要【口语化、亲切、自然、生动】，像在电话里跟徒弟聊天一样；
        2. 句子要【精炼短促】（每次回答 50~100 字以内为最佳），避免长篇大论，方便听觉接收；
        3. 严禁输出任何 Markdown 格式符号（严禁使用 **加粗**、# 标题、列表序号、表格、LaTeX 箭头等）；
        4. 招法直接念中文（如"进车压马"、"当头炮"、"马八进七"）；
        5. 多启发学员思考，给出行棋心理和大局观建议；
        6. 【语音识别容错】：学员问题来自麦克风语音实时转写，可能会有同音错别字（如把"当头炮"听成"当头泡/跑"、"车"误读成"彻/撤"、"士角炮"成"视角泡"等），请结合盘面主动纠正理解其实际下棋意图。
        """;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        SessionContext ctx = new SessionContext();
        contextMap.put(sessionId, ctx);
        // 【Fix-B4】为每个 session 创建独立的发送锁
        sessionLocks.put(sessionId, new Object());
        // 【Fix-O5】为每个 session 创建 TTS 有序队列，并启动队列消费线程
        LinkedBlockingQueue<Runnable> ttsQueue = new LinkedBlockingQueue<>();
        ttsQueues.put(sessionId, ttsQueue);
        startTtsQueueConsumer(sessionId, ttsQueue);

        // 注册 FunASR 断连错误通知回调（多次重连失败后通知前端）
        funAsrClientService.registerErrorNotifier(sessionId, errorMsg ->
                sendWsJson(session, Map.of("type", "funasr_error", "message", errorMsg)));

        // 启动 FunASR 会话
        funAsrClientService.startSession(
                sessionId,
                // 实时识别词
                partialText -> {
                    // 当 FunASR 识别到用户开始出声有文本时，且 AI 正在说话，触发打断 (Barge-in)
                    if (ctx.isAiSpeaking.get()) {
                        ctx.interruptRequested.set(true);
                        ctx.isAiSpeaking.set(false);
                        sendWsJson(session, Map.of("type", "interrupted"));
                    }
                    sendWsJson(session, Map.of(
                            "type", "asr_partial",
                            "text", partialText
                    ));
                },
                // 用户说完一句 (VAD 端点触发)
                finalText -> {
                    sendWsJson(session, Map.of(
                            "type", "asr_final",
                            "text", finalText
                    ));
                    // 触发大模型回答
                    handleUserSpokenSentence(session, finalText);
                }
        );

        sendWsJson(session, Map.of(
                "type", "connected",
                "message", "电话已接通，特级大师在线倾听..."
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode json = objectMapper.readTree(message.getPayload());
        String type = json.path("type").asText();
        SessionContext ctx = contextMap.get(session.getId());
        if (ctx == null) return;

        switch (type) {
            case "update_board" -> {
                // 棋盘状态联动：前端下棋后实时更新通话上下文中的 FEN 和历史走步
                if (json.has("fen")) ctx.currentFen = json.get("fen").asText();
                if (json.has("historyMoves")) ctx.historyMoves = json.get("historyMoves").asText();
                if (json.has("voice")) ctx.voice = json.get("voice").asText();
                if (json.has("rate")) ctx.rate = json.get("rate").asText();
            }
            case "manual_text" -> {
                // 也支持前端从输入框直接发送文字让大师在电话里回答
                String text = json.path("text").asText();
                if (!text.isBlank()) {
                    handleUserSpokenSentence(session, text);
                }
            }
            case "interrupt" -> {
                // 用户主动打断大师发音
                ctx.interruptRequested.set(true);
                ctx.isAiSpeaking.set(false);
                sendWsJson(session, Map.of("type", "interrupted"));
            }
            case "end_audio" -> {
                funAsrClientService.endAudio(session.getId());
            }
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        // 接收前端麦克风上报的 PCM 原始音频流数据
        ByteBuffer payload = message.getPayload();
        byte[] bytes = new byte[payload.remaining()];
        payload.get(bytes);
        funAsrClientService.sendAudioChunk(session.getId(), bytes);
    }

    private void handleUserSpokenSentence(WebSocketSession session, String userText) {
        SessionContext ctx = contextMap.get(session.getId());
        if (ctx == null || userText.isBlank()) return;

        ctx.interruptRequested.set(false);
        ctx.isAiSpeaking.set(true);

        sendWsJson(session, Map.of(
                "type", "ai_thinking",
                "prompt", userText
        ));

        // 异步调用皮卡鱼获取客观建议，再让大模型生成口语化指导
        aiExecutor.submit(() -> {
            try {
                EngineAnalysisResult engineResult = pikafishEngineService.analyzePosition(ctx.currentFen, 800);
                String bestMoveZh = "";
                if (engineResult != null && engineResult.getBestMove() != null) {
                    bestMoveZh = coordinateConverter.uciToChinese(ctx.currentFen, engineResult.getBestMove());
                }

                String userPrompt = String.format("""
                    当前盘面FEN：%s
                    对局走步记录：%s
                    皮卡鱼引擎客观计算：建议着法【%s】，当前优势分【%d厘分】
                    
                    学员在电话里问你："%s"
                    请用大师身份电话口语化直接回答他，精简明晰。
                    """, ctx.currentFen, ctx.historyMoves, bestMoveZh,
                        engineResult != null ? engineResult.getScoreCp() : 0, userText);

                List<ChatMessage> chatMessages = List.of(
                        SystemMessage.from(CALL_SYSTEM_PROMPT),
                        UserMessage.from(userPrompt)
                );

                StringBuilder fullReply = new StringBuilder();
                StringBuilder sentenceBuf = new StringBuilder();
                final java.util.concurrent.atomic.AtomicInteger seqCounter = new java.util.concurrent.atomic.AtomicInteger(0);

                streamingChatModel.generate(chatMessages, new StreamingResponseHandler<>() {
                    @Override
                    public void onNext(String token) {
                        if (ctx.interruptRequested.get()) return;
                        fullReply.append(token);
                        sentenceBuf.append(token);

                        sendWsJson(session, Map.of(
                                "type", "ai_text_chunk",
                                "chunk", token
                        ));

                        // 智能断句：遇到强句末标点（。！？\n）或者长度达到20字以上遇到逗号/分号，分段送去TTS
                        String currentBuf = sentenceBuf.toString();
                        boolean isEndPunctuation = token.contains("。") || token.contains("！") || token.contains("？") || token.contains("\n");
                        boolean isCommaBreak = (token.contains("，") || token.contains("；") || token.contains(",")) && currentBuf.length() >= 20;

                        if ((isEndPunctuation && currentBuf.length() >= 6) || isCommaBreak) {
                            String toSpeak = currentBuf.trim();
                            sentenceBuf.setLength(0);
                            if (!toSpeak.isBlank()) {
                                int seq = seqCounter.incrementAndGet();
                                // 【Fix-O5】异步提交到 TTS 有序队列，不阻塞 LLM token 流
                                enqueueTtsSynthesis(session, ctx, toSpeak, seq);
                            }
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        if (!ctx.interruptRequested.get() && sentenceBuf.length() > 0) {
                            String remaining = sentenceBuf.toString().trim();
                            sentenceBuf.setLength(0);
                            if (!remaining.isBlank()) {
                                int seq = seqCounter.incrementAndGet();
                                enqueueTtsSynthesis(session, ctx, remaining, seq);
                            }
                        }
                        ctx.isAiSpeaking.set(false);
                        sendWsJson(session, Map.of(
                                "type", "ai_reply_finished",
                                "fullText", fullReply.toString(),
                                "totalSeq", seqCounter.get()
                        ));
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("大模型对话推流异常", error);
                        ctx.isAiSpeaking.set(false);
                    }
                });

            } catch (Exception e) {
                log.error("处理电话提问异常", e);
                ctx.isAiSpeaking.set(false);
            }
        });
    }

    /**
     * 【Fix-O5】将 TTS 合成任务提交到有序队列，由 session 独立的消费线程按序执行。
     * 这样 LLM onNext 回调不会被 TTS I/O 阻塞，同时保证音频片段有序到达前端。
     */
    private void enqueueTtsSynthesis(WebSocketSession session, SessionContext ctx, String text, int seq) {
        BlockingQueue<Runnable> queue = ttsQueues.get(session.getId());
        if (queue == null) return;
        queue.offer(() -> synthesizeAndSend(session, ctx, text, seq));
    }

    /**
     * 【Fix-O5】每个 session 独立的 TTS 队列消费线程（虚拟线程），
     * 串行执行 TTS 合成，保证音频包顺序，避免乱序播放。
     */
    private void startTtsQueueConsumer(String sessionId, BlockingQueue<Runnable> queue) {
        Thread.ofVirtual().name("tts-consumer-" + sessionId).start(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Runnable task = queue.poll(30, java.util.concurrent.TimeUnit.SECONDS);
                    if (task == null) continue; // 超时继续等（session 可能关闭后队列为空）
                    task.run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void synthesizeAndSend(WebSocketSession session, SessionContext ctx, String text, int seq) {
        if (text.isBlank() || ctx.interruptRequested.get()) return;
        try {
            byte[] mp3 = edgeTtsService.synthesize(text, ctx.voice, ctx.rate);
            if (mp3.length > 0 && !ctx.interruptRequested.get()) {
                String base64 = Base64.getEncoder().encodeToString(mp3);
                sendWsJson(session, Map.of(
                        "type", "ai_audio_clip",
                        "audioBase64", base64,
                        "text", text,
                        "seq", seq
                ));
            }
        } catch (Exception e) {
            log.warn("分句 TTS 合成异常: seq={}, error={}", seq, e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        contextMap.remove(sessionId);
        sessionLocks.remove(sessionId);
        // 清理 TTS 队列（消费线程会因 InterruptedException 或超时退出）
        BlockingQueue<Runnable> ttsQueue = ttsQueues.remove(sessionId);
        if (ttsQueue != null) {
            ttsQueue.clear();
        }
        funAsrClientService.closeSession(sessionId);
    }

    /**
     * 【Fix-B4】sendWsJson 改为 session 级别锁，消除全局 synchronized 瓶颈。
     * 不同 session 的消息发送完全并行，同一 session 内串行（WebSocket 协议要求）。
     */
    private void sendWsJson(WebSocketSession session, Map<String, Object> data) {
        if (session == null || !session.isOpen()) return;
        // 获取该 session 专属的锁对象
        Object lock = sessionLocks.computeIfAbsent(session.getId(), k -> new Object());
        synchronized (lock) {
            if (!session.isOpen()) return;
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
            } catch (IOException e) {
                log.warn("向前端推送 WS 消息失败: {}", e.getMessage());
            }
        }
    }
}
