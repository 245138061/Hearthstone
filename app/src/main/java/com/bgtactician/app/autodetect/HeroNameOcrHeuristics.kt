package com.bgtactician.app.autodetect

import java.util.Locale

internal object HeroNameOcrHeuristics {

    fun normalize(value: String): String {
        return value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(HERO_NAME_NOISE_REGEX, "")
            .replace("地守护者欧穆", "欧穆")
            .replace("林地守护者欧穆", "欧穆")
            .replace("恐龙大师布莱恩", "布莱恩")
            .replace("恐尤大师布莱恩", "布莱恩")
            .replace("乔治堕圣盾", "乔治")
            .replace("沃恩", "沃恩王")
            .replace("摇滚教父沃恩王", "沃恩王")
            .replace("摇滚教父沃恩", "沃恩王")
            .replace("托瓦格尔", "托瓦格尔")
            .replace("伊瑟拉拉", "伊瑟拉")
            .replace("∞", "")
            .replace("|", "l")
    }

    fun isLikelyNoise(raw: String, normalized: String): Boolean {
        if (normalized.isBlank()) return true
        if (normalized.length == 1 && normalized.single().isDigit()) return true
        if (normalized.count(Char::isDigit) >= 2) return true
        if (raw.any(Char::isDigit) && normalized.length <= 2) return true
        if (NOISE_EXACT.contains(normalized)) return true
        if (NOISE_PARTS.any(normalized::contains)) return true
        return false
    }

    private val NOISE_EXACT = setOf(
        "重掷",
        "确认",
        "未识别",
        "酒馆",
        "战棋",
        "英雄",
        "选择",
        "一个",
        "bg",
        "b",
        "g"
    )

    private val NOISE_PARTS = setOf(
        "重掷",
        "确认",
        "选择一个英雄",
        "选择英雄",
        "野兽",
        "恶魔",
        "海盗",
        "龙",
        "元素",
        "机械",
        "鱼人",
        "娜迦",
        "野猪人",
        "亡灵",
        "护甲",
        "armor"
    )

    private val HERO_NAME_NOISE_REGEX =
        Regex("[\\s·•'’`\"“”\\-—_()（）,，.:：!！?？\\[\\]【】]+")
}
