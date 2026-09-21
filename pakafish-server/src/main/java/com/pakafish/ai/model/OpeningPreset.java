package com.pakafish.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 经典中国象棋开局定式信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpeningPreset {
    /** 开局英文标识 (如 central-cannon-vs-screen-horse) */
    private String id;
    /** 开局中文名 (如 中炮对屏风马) */
    private String name;
    /** 分类 (如 当头炮类、飞相起马类等) */
    private String category;
    /** 定式推演至该局面的 FEN */
    private String fen;
    /** 描述/特点 */
    private String description;
    /** 形成该定式的前序招法列表 (如 ["炮二平五", "马8进7", "马二进三", "车9平8"...]) */
    private List<String> movesChinese;
    /** 前序招法的 UCI 坐标 (如 ["b2e2", "b9c7", "b0c2", "i9h9"...]) */
    private List<String> movesUci;
}
