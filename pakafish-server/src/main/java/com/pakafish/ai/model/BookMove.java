package com.pakafish.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开局库走法推荐
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookMove {
    /** UCI 坐标格式 (例如 b2e2) */
    private String uci;
    /** 中文纵线招法 (例如 炮二平五) */
    private String chinese;
    /** 权重/频次 */
    private int weight;
}
