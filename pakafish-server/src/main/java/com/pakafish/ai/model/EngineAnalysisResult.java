package com.pakafish.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 皮卡鱼引擎分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineAnalysisResult {
    /** 最佳走法 (UCI坐标，例如 b2e2) */
    private String bestMove;
    /** 最佳走法中文记谱 (例如 炮二平五) */
    private String bestMoveChinese;
    /** 评估分 (单位cp，正分为当前走子方优势) */
    private Integer scoreCp;
    /** 胜率百分比 (0~100) */
    private Double winRate;
    /** 搜索深度 */
    private Integer depth;
    /** 最佳后续变例列表 (UCI坐标) */
    private List<String> pvMoves;
    /** 最佳后续变例列表 (中文记谱) */
    private List<String> pvMovesChinese;
    /** 优势描述 (如: 红方大优、局势均势、黑方微优) */
    private String advantageDescription;
}
