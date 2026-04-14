package com.bgtactician.app.vision

import com.bgtactician.app.autodetect.CapturedFrame
import com.bgtactician.app.data.model.HeroSelectionVisionResult

enum class VisionRequestSource {
    MANUAL_TRIGGER,
    HERO_SELECTION_AUTOMATION,
    OFFLINE_DEBUG
}

enum class VisionRecognitionScope {
    TRIBES_ONLY,
    FULL
}

data class HeroSelectionVisionRequest(
    val frame: CapturedFrame,
    val source: VisionRequestSource = VisionRequestSource.MANUAL_TRIGGER,
    val localeHint: String = "zh-CN",
    val recognitionScope: VisionRecognitionScope = VisionRecognitionScope.FULL
)

interface HeroSelectionVisionProvider {
    suspend fun analyze(request: HeroSelectionVisionRequest): HeroSelectionVisionResult
}
