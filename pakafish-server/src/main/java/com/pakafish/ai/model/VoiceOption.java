package com.pakafish.ai.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum VoiceOption {
    XIAOXIAO("zh-CN-XiaoxiaoNeural", "晓晓 (温柔亲切女声 - 推荐)"),
    YUNXI("zh-CN-YunxiNeural", "云希 (阳光开朗男声 - 推荐)"),
    YUNJIAN("zh-CN-YunjianNeural", "云健 (稳重磁性男声 - 适合解说)"),
    XIAOYI("zh-CN-XiaoyiNeural", "晓伊 (多情景甜美女声)"),
    YUNYANG("zh-CN-YunyangNeural", "云扬 (专业新闻播音男声)"),
    LIAONING_XIAOBEI("zh-CN-liaoning-XiaobeiNeural", "小北 (风趣东北女声)"),
    SHAANXI_XIAONI("zh-CN-shaanxi-XiaoniNeural", "小妮 (风情陕西女声)"),
    TAIWAN_HSIAOCHEN("zh-TW-HsiaoChenNeural", "晓臻 (温柔台湾女声)"),
    HONGKONG_HIUGAAT("zh-HK-HiuGaatNeural", "晓佳 (粤语女声)");

    private final String code;
    private final String name;

    public static List<Map<String, String>> toList() {
        List<Map<String, String>> list = new ArrayList<>();
        for (VoiceOption option : values()) {
            Map<String, String> item = new HashMap<>();
            item.put("code", option.getCode());
            item.put("name", option.getName());
            list.add(item);
        }
        return list;
    }
}
