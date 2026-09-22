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

        【复盘与分支演进交互规范】
        1. 当复盘提到历史上的具体某一步棋时，请明确标注回合序号（例如：“第3回合红方走炮二平五”、“在第5步”），以便系统精准联动棋盘跳转到那一刻。
        2. 当给出后续分支演进或复盘建议时，必须给出【红黑双方交替进行】的完整对局序列（必须是：红方招法 → 黑方招法 → 红方招法，或者黑方招法 → 红方招法... 严禁省略中间对方的走步！）。
           例如正确示范：**“车9进1 → 炮二平七 → 车9平3”**（不可跳过红方中间走的“炮二平七”写成“车9进1 → 车9平3”）。
        3. 招法之间使用中文箭头“→”连接，严禁输出带有美元符号的 LaTeX 语法（严禁输出 $\\to$ 或 $\\rightarrow$）。
        4. 棋谱记法（如“炮二平五”、“马8进7”）若加粗，必须完整闭合 `**`，且加粗符号放在引号外面（如 **“炮二平五”**）。
        5. 避免只报冰冷的数字或代码坐标，多从人类棋理、战术战略（如控中、牵制、抢先、破相、入局、窝心马、沉底炮等）角度讲解。
        6. 说话风格既有大师沉稳权威的气质，又通俗易懂，善于点拨启发学员。
        7. 必须基于引擎提供的客观事实和走法，绝不编造虚假违规走法。
        8. 回答要言简意赅、条理清晰，可分点阐述核心要点。
        """;

    /**
     * 流式解答用户的棋局提问或局势分析请求
     */
    public void streamChat(String fen, String history, String userQuestion, SseEmitter emitter) {
        // 1. 调用皮卡鱼引擎快速分析局面
        EngineAnalysisResult engineResult = pikafishEngineService.analyzePosition(fen, 1500);

        // 2. 转换坐标为中文记谱
        if (engineResult != null && engineResult.getBestMove() != null) {
            engineResult.setBestMoveChinese(coordinateConverter.uciToChinese(fen, engineResult.getBestMove()));
            engineResult.setPvMovesChinese(coordinateConverter.convertMoveList(fen, engineResult.getPvMoves()));
        }

        // 3. 构建 Prompt 事实上下文 (注入完整的开局对局棋谱)
        String context = buildContext(fen, history, engineResult);
        String promptContent = String.format("""
            %s
            
            【学员提问/诉求】：%s
            
            请作为特级大师教练，结合上述整盘对局历史、当前盘面与皮卡鱼客观深度算力，为学员进行专业全面的复盘、思路剖析与战术解答：
            """, context, (userQuestion == null || userQuestion.isBlank()) ? "请结合整盘历史与当前盘面，全面复盘局势并讲解最佳应对计划。" : userQuestion);

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

    private String buildContext(String fen, String history, EngineAnalysisResult res) {
        boolean isRed = !fen.contains(" b ");
        StringBuilder sb = new StringBuilder();

        // 注入整盘历史对局谱
        sb.append("【整盘对局历史谱（从第1回合到当前）】\n");
        if (history != null && !history.isBlank()) {
            sb.append(history).append("\n");
        } else {
            sb.append("（刚开局，尚无历史着法）\n");
        }

        sb.append("\n【当前最新棋盘状态】\n");
        sb.append("- 轮到走子方: ").append(isRed ? "红方" : "黑方").append("\n");
        sb.append("- FEN 坐标状态: ").append(fen).append("\n");

        if (res != null) {
            sb.append("\n【皮卡鱼引擎客观计算事实】\n");
            sb.append("- 局势评语: ").append(res.getSideAdvantageText()).append(" (").append(res.getAdvantageDescription()).append(")\n");
            sb.append("- 评估分(cp): ").append(res.getScoreCp()).append("，当前胜率估算约: ").append(res.getWinRate()).append("%\n");
            sb.append("- 引擎推荐当前最佳应对: ").append(res.getBestMoveChinese()).append(" (").append(res.getBestMove()).append(")\n");
            if (res.getPvMovesChinese() != null && !res.getPvMovesChinese().isEmpty()) {
                sb.append("- 引擎推荐后续深度推演: ").append(String.join(" -> ", res.getPvMovesChinese())).append("\n");
            }
        }
        return sb.toString();
    }
}
