package com.pakafish.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * FunASR WebSocket 客户端，负责连接 FunASR 容器 (默认端口 10095) 进行语音流识别与 VAD 处理
 */
@Slf4j
@Service
public class FunAsrClientService {

    @Value("${funasr.ws-url:ws://127.0.0.1:10095}")
    private String funAsrWsUrl;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // 保持长连接不超时断开
            .pingInterval(10, TimeUnit.SECONDS)    // 开启 WebSocket 自动保活心跳
            .connectTimeout(5, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 重连调度器（单线程），负责延迟重连任务。
     */
    private final ScheduledExecutorService reconnectScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "funasr-reconnect");
                t.setDaemon(true);
                return t;
            });

    /**
     * 存储每个会话与其对应的 FunASR 后端 WebSocket 连接
     */
    private final Map<String, WebSocket> sessionWsMap = new ConcurrentHashMap<>();

    /**
     * 存储每个会话的回调（用于重连后恢复）
     */
    private final Map<String, SessionCallbacks> sessionCallbacksMap = new ConcurrentHashMap<>();

    /**
     * 重连失败次数记录（用于指数退避）
     */
    private final Map<String, Integer> reconnectAttempts = new ConcurrentHashMap<>();

    /**
     * 【Fix-B9】会话发送错误通知的回调（由 AiCallWebSocketHandler 注册）
     * key: sessionId, value: 向前端发送错误通知的 Consumer
     */
    private final Map<String, Consumer<String>> errorNotifiers = new ConcurrentHashMap<>();

    private record SessionCallbacks(Consumer<String> onRecognizedText, Consumer<String> onSentenceEnd) {}

    /**
     * 注册该会话的错误通知回调（用于 FunASR 断连时通知前端）
     */
    public void registerErrorNotifier(String sessionId, Consumer<String> notifier) {
        errorNotifiers.put(sessionId, notifier);
    }

    /**
     * 为通话或识别会话开启与 FunASR 的连接
     * @param sessionId           唯一会话标识
     * @param onRecognizedText    回调已识别的文字（包含增量与最终结果）
     * @param onSentenceEnd       句子结束标识 (VAD 检测到用户说完一句话)
     */
    public void startSession(String sessionId, Consumer<String> onRecognizedText, Consumer<String> onSentenceEnd) {
        // 保存回调，以备重连后恢复
        sessionCallbacksMap.put(sessionId, new SessionCallbacks(onRecognizedText, onSentenceEnd));
        reconnectAttempts.put(sessionId, 0);
        closeSession(sessionId);
        doConnect(sessionId, onRecognizedText, onSentenceEnd);
    }

    private void doConnect(String sessionId, Consumer<String> onRecognizedText, Consumer<String> onSentenceEnd) {
        Request request = new Request.Builder().url(funAsrWsUrl).build();

        WebSocket ws = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("【FunASR 连接成功】sessionId={}", sessionId);
                // 重连成功，重置重连次数
                reconnectAttempts.put(sessionId, 0);
                // 发送 FunASR 初始化配置
                try {
                    Map<String, Object> config = Map.of(
                            "mode", "2pass", // 2pass 流式 + 离线矫正模式
                            "chunk_size", new int[]{5, 10, 5},
                            "wav_name", "h5",
                            "is_speaking", true,
                            "wav_format", "pcm"
                    );
                    webSocket.send(objectMapper.writeValueAsString(config));
                } catch (Exception e) {
                    log.error("发送 FunASR 初始化参数异常", e);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonNode root = objectMapper.readTree(text);
                    if (root.has("text")) {
                        String recognized = root.get("text").asText();
                        // 兼容 2pass 流式 final 标记与 offline 单段识别完毕模式
                        boolean isFinal = root.path("is_final").asBoolean(false)
                                || "offline".equalsIgnoreCase(root.path("mode").asText());
                        if (recognized != null && !recognized.isBlank()) {
                            onRecognizedText.accept(recognized);
                        }
                        if (isFinal && recognized != null && !recognized.isBlank()) {
                            onSentenceEnd.accept(recognized);
                        }
                    }
                } catch (Exception e) {
                    log.error("解析 FunASR 返回结果失败: {}", text, e);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.warn("FunASR 连接断开或异常：{}，sessionId={}", t.getMessage(), sessionId);
                sessionWsMap.remove(sessionId);

                // 【Fix-B9】检查会话是否还活着，如果是则触发重连
                if (sessionCallbacksMap.containsKey(sessionId)) {
                    scheduleReconnect(sessionId);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                sessionWsMap.remove(sessionId);
                log.debug("【FunASR 正常关闭】sessionId={}, code={}, reason={}", sessionId, code, reason);
            }
        });

        sessionWsMap.put(sessionId, ws);
    }

    /**
     * 【Fix-B9】指数退避重连策略：第1次延迟1s，第2次2s，第3次4s，最多16s，超过5次通知前端。
     */
    private void scheduleReconnect(String sessionId) {
        int attempt = reconnectAttempts.getOrDefault(sessionId, 0);
        if (attempt >= 5) {
            log.error("【FunASR 多次重连失败】sessionId={}，已达最大重试次数，通知前端", sessionId);
            Consumer<String> notifier = errorNotifiers.get(sessionId);
            if (notifier != null) {
                notifier.accept("语音识别服务连接失败，请检查 FunASR 容器是否正常运行");
            }
            return;
        }

        long delaySeconds = (long) Math.pow(2, attempt); // 1, 2, 4, 8, 16
        reconnectAttempts.put(sessionId, attempt + 1);

        log.info("【FunASR 重连调度】sessionId={}，第 {} 次尝试，{}s 后重连", sessionId, attempt + 1, delaySeconds);
        reconnectScheduler.schedule(() -> {
            // 再次确认会话还存在
            if (!sessionCallbacksMap.containsKey(sessionId)) {
                log.debug("【FunASR 重连取消】sessionId={} 会话已关闭，跳过重连", sessionId);
                return;
            }
            SessionCallbacks callbacks = sessionCallbacksMap.get(sessionId);
            if (callbacks != null) {
                log.info("【FunASR 重连执行】sessionId={}", sessionId);
                doConnect(sessionId, callbacks.onRecognizedText(), callbacks.onSentenceEnd());
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * 发送音频二进制数据 (PCM 16kHz 16bit 单声道) 给 FunASR
     */
    public void sendAudioChunk(String sessionId, byte[] pcmData) {
        WebSocket ws = sessionWsMap.get(sessionId);
        if (ws != null) {
            ws.send(ByteString.of(pcmData));
        }
        // 【Fix-B9】ws 为 null 表示正在重连中，音频块静默丢弃（重连成功后自然恢复）
    }

    /**
     * 发送音频结束标识
     */
    public void endAudio(String sessionId) {
        WebSocket ws = sessionWsMap.get(sessionId);
        if (ws != null) {
            try {
                ws.send("{\"is_speaking\": false}");
            } catch (Exception ignored) {}
        }
    }

    /**
     * 关闭会话（清除重连状态与回调）
     */
    public void closeSession(String sessionId) {
        // 清除重连状态，防止关闭后继续重连
        sessionCallbacksMap.remove(sessionId);
        reconnectAttempts.remove(sessionId);
        errorNotifiers.remove(sessionId);

        WebSocket ws = sessionWsMap.remove(sessionId);
        if (ws != null) {
            try {
                ws.close(1000, "Normal closure");
            } catch (Exception ignored) {}
        }
    }
}
