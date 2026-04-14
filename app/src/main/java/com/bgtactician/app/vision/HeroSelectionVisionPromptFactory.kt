package com.bgtactician.app.vision

object HeroSelectionVisionPromptFactory {

    fun buildInstruction(
        localeHint: String = "zh-CN",
        recognitionScope: VisionRecognitionScope = VisionRecognitionScope.FULL
    ): String {
        val heroRule = when (recognitionScope) {
            VisionRecognitionScope.TRIBES_ONLY -> {
                "只返回 5 个可用种族，hero_options 固定返回空数组。"
            }

            VisionRecognitionScope.FULL -> {
                "hero_options 返回当前屏幕可见的全部 4 个英雄槽位，按从左到右输出。slot 必须固定使用 0,1,2,3，分别对应最左到最右的 4 个英雄位。每项只包含 slot、name、locked、armor。name 返回当前界面可见名称；locked 表示该槽位当前是否不可点击。"
            }
        }
        return buildString {
            append("只返回 JSON，不要 markdown，不要代码块，不要解释。")
            append("识别酒馆战棋英雄选择页。")
            append("不是该页面时返回 {\"screen_type\":\"non_target\",\"available_tribes\":[],\"hero_options\":[]}。")
            append("是该页面时返回 screen_type、available_tribes、hero_options。")
            append("如果画面中能清楚看到 2 个及以上英雄候选，即使顶部横幅、按钮或种族文字不完整，也按英雄选择页处理并返回 best-effort 结果。")
            append("available_tribes 只允许: Beast,Demon,Dragon,Elemental,Mech,Murloc,Naga,Pirate,Quilboar,Undead。")
            append(heroRule)
            append("locale_hint: ")
            append(localeHint)
        }
    }
}
