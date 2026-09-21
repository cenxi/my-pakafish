package com.pakafish.ai.service;

import com.pakafish.ai.model.OpeningPreset;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 经典中国象棋开局定式库服务
 * 提供常用正宗开局谱，供用户随时选择、一键摆盘练习
 */
@Service
public class OpeningPresetService {

    private final List<OpeningPreset> presets = new ArrayList<>();

    public OpeningPresetService() {
        initPresets();
    }

    private void initPresets() {
        // 1. 中炮对屏风马
        presets.add(OpeningPreset.builder()
                .id("cc-screen-horse")
                .name("中炮对屏风马")
                .category("当头炮类")
                .description("象棋最主流定式。红方当头炮直指中路，黑方双马护中卒，稳如磐石。")
                .movesChinese(List.of("炮二平五", "马8进7", "马二进三", "车9平8", "车一平二", "马2进3"))
                .movesUci(List.of("h2e2", "h9g7", "h0g2", "i9h9", "i0h0", "b9c7"))
                .fen("r1bakabr1/9/1cn3nc1/p1p1p1p1p/9/9/P1P1P1P1P/1C2C1N2/9/RNBAKABR1 w - - 6 4")
                .build());

        // 2. 中炮对反宫马
        presets.add(OpeningPreset.builder()
                .id("cc-reverse-palace-horse")
                .name("中炮对反宫马")
                .category("当头炮类")
                .description("胡荣华特大成名开局。双马向内护心，进退自如，暗藏反击杀机。")
                .movesChinese(List.of("炮二平五", "马2进3", "马二进三", "炮8平6", "车一平二", "马8进7"))
                .movesUci(List.of("h2e2", "b9c7", "h0g2", "h7f7", "i0h0", "h9g7"))
                .fen("r1bakab1r/9/1cn2cn2/p1p1p1p1p/9/9/P1P1P1P1P/1C2C1N2/9/RNBAKABR1 w - - 6 4")
                .build());

        // 3. 顺手炮 (斗炮局)
        presets.add(OpeningPreset.builder()
                .id("shun-shou-pao")
                .name("顺手炮 (大出车对缓开直车)")
                .category("当头炮类")
                .description("刚烈对攻的经典斗炮开局。红架中炮，黑亦架中炮，寸步不让。")
                .movesChinese(List.of("炮二平五", "炮8平5", "马二进三", "马8进7", "车一平二", "车9进1"))
                .movesUci(List.of("h2e2", "h7e7", "h0g2", "h9g7", "i0h0", "i9i8"))
                .fen("rnbakab2/8r/1c2c1n2/p1p1p1p1p/9/9/P1P1P1P1P/1C2C1N2/9/RNBAKABR1 w - - 6 4")
                .build());

        // 4. 列手炮 (逆手炮)
        presets.add(OpeningPreset.builder()
                .id("lie-shou-pao")
                .name("列手炮")
                .category("当头炮类")
                .description("互相对攻的经典逆向炮局，针尖对麦芒，胜负瞬息万变。")
                .movesChinese(List.of("炮二平五", "炮2平5", "马二进三", "马2进3"))
                .movesUci(List.of("h2e2", "b7e7", "h0g2", "b9c7"))
                .fen("r1bakabnr/9/2n1c2c1/p1p1p1p1p/9/9/P1P1P1P1P/1C2C1N2/9/RNBAKAB1R w - - 4 3")
                .build());

        // 5. 飞相局 (相三进五)
        presets.add(OpeningPreset.builder()
                .id("fei-xiang-ju")
                .name("飞相局 (相三进五)")
                .category("飞相起马类")
                .description("柔和持重、厚积薄发之局。巩固中防，静观其变，稳扎稳打。")
                .movesChinese(List.of("相三进五", "卒7进1", "马二进三", "马8进7"))
                .movesUci(List.of("g0e2", "g6g5", "h0g2", "h9g7"))
                .fen("rnbakab1r/9/1c4nc1/p1p1p3p/6p2/9/P1P1P1P1P/1C2B1NC1/9/RNBAKA2R w - - 4 3")
                .build());

        // 6. 仙人指路 (进兵局) 对卒底炮
        presets.add(OpeningPreset.builder()
                .id("xian-ren-zhi-lu")
                .name("仙人指路对卒底炮")
                .category("飞相起马类")
                .description("试探虚实之棋。红投石问路，黑以卒底炮针对，变化极多。")
                .movesChinese(List.of("兵七进一", "炮2平3", "相七进五", "象7进5"))
                .movesUci(List.of("c3c4", "b7c7", "c0e2", "g9e7"))
                .fen("rnbaka1nr/9/2c1b2c1/p1p1p1p1p/9/2P6/P3P1P1P/1C2B2C1/9/RN1AKABNR w - - 4 3")
                .build());

        // 7. 起马局对挺卒
        presets.add(OpeningPreset.builder()
                .id("qi-ma-ju")
                .name("起马局对进卒")
                .category("飞相起马类")
                .description("守中有攻的现代化大师开局。马八进七保护边兵，策动双车。")
                .movesChinese(List.of("马八进七", "卒3进1", "炮二平五", "马2进3"))
                .movesUci(List.of("b0c2", "c6c5", "h2e2", "b9c7"))
                .fen("r1bakabnr/9/1cn4c1/p3p1p1p/2p6/9/P1P1P1P1P/1CN1C4/9/R1BAKABNR w - - 4 3")
                .build());

        // 8. 过宫炮
        presets.add(OpeningPreset.builder()
                .id("guo-gong-pao")
                .name("过宫炮 (炮二平六)")
                .category("其他特殊开局")
                .description("集结重兵于一翼，集中火力猛攻对方侧翼，特色鲜明。")
                .movesChinese(List.of("炮二平六", "马8进7", "马二进三", "车9平8"))
                .movesUci(List.of("h2d2", "h9g7", "h0g2", "i9h9"))
                .fen("rnbakabr1/9/1c4nc1/p1p1p1p1p/9/9/P1P1P1P1P/1C1C2N2/9/RNBAKAB1R w - - 4 3")
                .build());
    }

    public List<OpeningPreset> getAllPresets() {
        return presets;
    }
}
