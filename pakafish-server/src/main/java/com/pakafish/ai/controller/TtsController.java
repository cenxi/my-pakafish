package com.pakafish.ai.controller;

import com.pakafish.ai.model.VoiceOption;
import com.pakafish.ai.service.EdgeTtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
public class TtsController {

    private final EdgeTtsService ttsService;

    /**
     * 获取支持的音色列表供前端选择
     */
    @GetMapping("/voices")
    public List<Map<String, String>> getVoices() {
        return VoiceOption.toList();
    }

    /**
     * 语音合成接口（返回 audio/mpeg 二进制数据流）
     */
    @GetMapping(value = "/speak", produces = "audio/mpeg")
    public ResponseEntity<byte[]> speak(
            @RequestParam("text") String text,
            @RequestParam(value = "voice", required = false, defaultValue = EdgeTtsService.DEFAULT_VOICE) String voice,
            @RequestParam(value = "rate", required = false, defaultValue = "+0%") String rate) {
        try {
            byte[] mp3Bytes = ttsService.synthesize(text, voice, rate);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
            headers.setContentLength(mp3Bytes.length);
            headers.setCacheControl("public, max-age=86400"); // 缓存一天
            return new ResponseEntity<>(mp3Bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("TTS 合成失败: text={}, error={}", text, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new byte[0]);
        }
    }
}
