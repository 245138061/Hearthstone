package com.bgtactician.app.vision

object HeroSelectionVisionPromptFactory {

    fun buildInstruction(
        localeHint: String = "zh-CN",
        recognitionScope: VisionRecognitionScope = VisionRecognitionScope.FULL
    ): String {
        val heroRule = when (recognitionScope) {
            VisionRecognitionScope.TRIBES_ONLY -> {
                "只关注顶部紫色横幅和标题下方的种族文字，尽量补全 5 个可用种族，hero_options 固定返回空数组。即使英雄区域清晰，也不要忽略种族行。"
            }

            VisionRecognitionScope.FULL -> {
                "hero_options 只返回当前可点击、可选择的英雄槽位，按从左到右输出；不可点击或灰掉的槽位不要返回。slot 仍使用 0,1,2,3 对应最左到最右原始位置。每项只包含 slot、name、locked、armor。可选择槽位 locked 必须是 false。"
            }
        }
        return buildString {
            append("只返回 JSON，不要 markdown，不要代码块，不要解释。")
            append("识别酒馆战棋英雄选择页。")
            append("不是该页面时返回 {\"screen_type\":\"non_target\",\"available_tribes\":[],\"hero_options\":[]}。")
            append("是该页面时返回 screen_type、available_tribes、hero_options。")
            append("如果画面中能清楚看到 2 个及以上英雄候选，即使顶部横幅、按钮或种族文字不完整，也按英雄选择页处理并返回 best-effort 结果。")
            append("available_tribes 优先读取顶部标题下方那一行种族文字；界面可能是中文，但返回值必须使用英文枚举。")
            append("available_tribes 只允许: Beast,Demon,Dragon,Elemental,Mech,Murloc,Naga,Pirate,Quilboar,Undead。")
            append(heroRule)
            append("locale_hint: ")
            append(localeHint)
        }
    }
}
