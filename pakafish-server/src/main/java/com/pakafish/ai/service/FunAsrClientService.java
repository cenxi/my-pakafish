package com.pakafish.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS) // 保持长连接不超时断开
            .pingInterval(10, java.util.concurrent.TimeUnit.SECONDS)    // 开启 WebSocket 自动保活心跳
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 存储每个会话与其对应的 FunASR 后端 WebSocket 连接
    private final Map<String, WebSocket> sessionWsMap = new ConcurrentHashMap<>();

    /**
     * 为通话或识别会话开启与 FunASR 的连接
     * @param sessionId 唯一会话标识
     * @param onRecognizedText 回调已识别的文字（包含增量与最终结果）
     * @param onSentenceEnd 句子结束标识 (VAD 检测到用户说完一句话)
     */
    public void startSession(String sessionId, Consumer<String> onRecognizedText, Consumer<String> onSentenceEnd) {
        closeSession(sessionId);

        Request request = new Request.Builder().url(funAsrWsUrl).build();

        WebSocket ws = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("【FunASR 连接成功】sessionId={}", sessionId);
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
                        boolean isFinal = root.path("is_final").asBoolean(false);
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
                log.warn("FunASR 连接断开或异常（若未启动 FunASR Docker 容器则属正常）：{}", t.getMessage());
                sessionWsMap.remove(sessionId);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                sessionWsMap.remove(sessionId);
            }
        });

        sessionWsMap.put(sessionId, ws);
    }

    /**
     * 发送音频二进制数据 (PCM 16kHz 16bit 单声道) 给 FunASR
     */
    public void sendAudioChunk(String sessionId, byte[] pcmData) {
        WebSocket ws = sessionWsMap.get(sessionId);
        if (ws != null) {
            ws.send(ByteString.of(pcmData));
        }
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
     * 关闭会话
     */
    public void closeSession(String sessionId) {
        WebSocket ws = sessionWsMap.remove(sessionId);
        if (ws != null) {
            try {
                ws.close(1000, "Normal closure");
            } catch (Exception ignored) {}
        }
    }
}
