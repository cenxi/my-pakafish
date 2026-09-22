package com.pakafish.ai.service;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 基于微软 Edge 朗读 WebSocket 协议的高质量免费语音合成服务 (Edge-TTS)
 */
@Slf4j
@Service
public class EdgeTtsService {

    private static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String CHROMIUM_FULL_VERSION = "143.0.3650.75";
    private static final String CHROMIUM_MAJOR_VERSION = "143";
    private static final String SEC_MS_GEC_VERSION = "1-" + CHROMIUM_FULL_VERSION;
    private static final long WIN_EPOCH = 11644473600L;
    private static final String WSS_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";
    public static final String DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build();

    public byte[] synthesize(String text) throws Exception {
        return synthesize(text, DEFAULT_VOICE, "+0%");
    }

    public byte[] synthesize(String text, String voice, String rate) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            return new byte[0];
        }
        // 清理 Markdown 或特殊特殊标记，让发音更顺畅
        String cleanText = cleanMarkdownForSpeech(text);
        if (cleanText.isBlank()) {
            return new byte[0];
        }

        String finalVoice = (voice != null && !voice.isBlank()) ? voice.trim() : DEFAULT_VOICE;
        String finalRate = (rate != null && !rate.isBlank()) ? rate.trim() : "+0%";

        CompletableFuture<byte[]> future = new CompletableFuture<>();
        ByteArrayOutputStream audioStream = new ByteArrayOutputStream();

        String connectionId = UUID.randomUUID().toString().replace("-", "");
        String secMsGec = generateSecMsGec();
        String url = WSS_URL + "?TrustedClientToken=" + TRUSTED_CLIENT_TOKEN
                + "&ConnectionId=" + connectionId
                + "&Sec-MS-GEC=" + secMsGec
                + "&Sec-MS-GEC-Version=" + SEC_MS_GEC_VERSION;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + CHROMIUM_MAJOR_VERSION + ".0.0.0 Safari/537.36 Edg/" + CHROMIUM_MAJOR_VERSION + ".0.0.0")
                .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Pragma", "no-cache")
                .addHeader("Cache-Control", "no-cache")
                .build();

        WebSocket ws = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                try {
                    // 1. 发送音频格式配置帧
                    String timestamp = getFormattedDate();
                    String configMsg = "Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n"
                            + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},"
                            + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}";
                    webSocket.send(configMsg);

                    // 2. 发送 SSML 请求文本帧
                    String requestId = UUID.randomUUID().toString().replace("-", "");
                    String escapedText = escapeXml(cleanText);
                    String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>"
                            + "<voice name='" + finalVoice + "'>"
                            + "<prosody rate='" + finalRate + "'>" + escapedText + "</prosody>"
                            + "</voice></speak>";

                    String ssmlMsg = "X-RequestId:" + requestId + "\r\n"
                            + "Content-Type:application/ssml+xml\r\n"
                            + "X-Timestamp:" + timestamp + "Z\r\n"
                            + "Path:ssml\r\n\r\n" + ssml;

                    webSocket.send(ssmlMsg);
                } catch (Exception e) {
                    log.error("发送 SSML 握手异常", e);
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                byte[] raw = bytes.toByteArray();
                if (raw.length >= 2) {
                    int headerLength = ((raw[0] & 0xFF) << 8) | (raw[1] & 0xFF);
                    int audioStart = 2 + headerLength;
                    if (raw.length > audioStart) {
                        audioStream.write(raw, audioStart, raw.length - audioStart);
                    }
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String textMsg) {
                if (textMsg.contains("Path:turn.end")) {
                    webSocket.close(1000, "Done");
                    future.complete(audioStream.toByteArray());
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (!future.isDone()) {
                    future.complete(audioStream.toByteArray());
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.warn("Edge-TTS WebSocket 异常: {}", t.getMessage());
                if (!future.isDone()) {
                    future.completeExceptionally(t);
                }
            }
        });

        // 最多等 12 秒超时
        return future.get(12, TimeUnit.SECONDS);
    }

    private String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

    private static String generateSecMsGec() {
        try {
            long unixSeconds = Instant.now().getEpochSecond();
            long ticks = unixSeconds + WIN_EPOCH;
            ticks -= ticks % 300;
            ticks *= 10_000_000L;
            String strToHash = ticks + TRUSTED_CLIENT_TOKEN;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(strToHash.getBytes(StandardCharsets.US_ASCII));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            log.error("生成 Sec-MS-GEC 失败", e);
            return "";
        }
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * 将棋谱分析中的 Markdown 符号、加粗符号、箭头等洗净为自然口语
     */
    public String cleanMarkdownForSpeech(String markdown) {
        if (markdown == null) return "";
        return markdown
                .replaceAll("(?i)<[^>]*>", "")           // 去除 HTML 标签
                .replaceAll("(?i)\\[([^\\]]+)\\]\\([^)]+\\)", "$1") // 去除链接
                .replaceAll("\\*\\*", "")                  // 去除加粗
                .replaceAll("\\*", "")
                .replaceAll("#+\\s*", "")                 // 去除标题
                .replaceAll("`", "")                      // 去除反引号
                .replaceAll("→", "，下一步走，")          // 将招法箭头口语化
                .replaceAll("->", "，下一步走，")
                .replaceAll("[\\t\\r]+", " ")
                .replaceAll("\\n{2,}", "。")
                .replaceAll("\\n", " ")
                .trim();
    }
}
