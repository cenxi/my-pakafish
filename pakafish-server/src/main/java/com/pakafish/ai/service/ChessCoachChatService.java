package com.pakafish.ai.service;

import com.pakafish.ai.model.EngineAnalysisResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChessCoachChatService {

    private final StreamingChatLanguageModel streamingChatModel;
    private final PikafishEngineService pikafishEngineService;
    private final ChessCoordinateConverter coordinateConverter;

    private static final String SYSTEM_PROMPT = """
        你是一位享有盛誉的中国象棋特级大师和耐心温和的象棋国家队教练。
        你的任务是基于专业引擎（皮卡鱼 Pikafish）的精准运算数据，为学员进行棋局分析、战术复盘和答疑解惑。

        【教学要求】
        1. 避免只报冰冷的数字或代码坐标，多从人类棋理、战术战略（如控中、牵制、抢先、破相、入局、窝心马、沉底炮等）角度讲解。
        2. 说话风格既有大师沉稳权威的气质，又通俗易懂，善于点拨启发学员。
        3. 必须基于皮卡鱼引擎提供的客观事实和走法，绝不编造虚假违规走法。
        4. 回答要言简意赅、条理清晰，可分点阐述核心要点。
        """;

    /**
     * 流式解答用户的棋局提问或局势分析请求
     */
    public void streamChat(String fen, String userQuestion, SseEmitter emitter) {
        // 1. 调用皮卡鱼引擎快速分析局面
        EngineAnalysisResult engineResult = pikafishEngineService.analyzePosition(fen, 1500);

        // 2. 转换坐标为中文记谱
        if (engineResult != null && engineResult.getBestMove() != null) {
            engineResult.setBestMoveChinese(coordinateConverter.uciToChinese(fen, engineResult.getBestMove()));
            engineResult.setPvMovesChinese(coordinateConverter.convertMoveList(fen, engineResult.getPvMoves()));
        }

        // 3. 构建 Prompt 事实上下文
        String context = buildContext(fen, engineResult);
        String promptContent = String.format("""
            %s
            
            【学员提问/诉求】：%s
            
            请作为特级大师教练，结合上述局面特征与引擎客观数据，对学员进行指导与解答：
            """, context, (userQuestion == null || userQuestion.isBlank()) ? "请全面分析当前局势，并为我讲解最佳走法的思路与后续计划。" : userQuestion);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));
        messages.add(UserMessage.from(promptContent));

        // 4. 调用 LangChain4j 流式推流
        streamingChatModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                try {
                    // 转义换行符避免破坏 SSE 的 "data: line\n\n" 协议
                    String encoded = token.replace("\r", "").replace("\n", "\\n");
                    emitter.send(SseEmitter.event().data(encoded));
                } catch (IOException e) {
                    log.warn("SSE 推流写入中断: {}", e.getMessage());
                }
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    emitter.send(SseEmitter.event().name("finish").data("[DONE]"));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("LLM 流式生成出错", error);
                try {
                    emitter.send(SseEmitter.event().name("error").data("导师思考中遇到了问题: " + error.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(error);
            }
        });
    }

    private String buildContext(String fen, EngineAnalysisResult res) {
        boolean isRed = fen.contains(" w ") ? false : true; // 象棋FEN通常用 w表示红(先), b表示黑(后)
        StringBuilder sb = new StringBuilder();
        sb.append("【当前棋盘状态】\n");
        sb.append("- FEN: ").append(fen).append("\n");
        sb.append("- 轮到走子方: ").append(isRed ? "红方" : "黑方").append("\n");

        if (res != null) {
            sb.append("\n【皮卡鱼引擎客观计算】\n");
            sb.append("- 局势评估: ").append(res.getAdvantageDescription()).append("\n");
            sb.append("- 评估分(cp): ").append(res.getScoreCp()).append("，当前胜率估算约: ").append(res.getWinRate()).append("%\n");
            sb.append("- 引擎推荐最佳走法: ").append(res.getBestMoveChinese()).append(" (").append(res.getBestMove()).append(")\n");
            if (res.getPvMovesChinese() != null && !res.getPvMovesChinese().isEmpty()) {
                sb.append("- 引擎推荐后续深度推演: ").append(String.join(" -> ", res.getPvMovesChinese())).append("\n");
            }
        }
        return sb.toString();
    }
}
