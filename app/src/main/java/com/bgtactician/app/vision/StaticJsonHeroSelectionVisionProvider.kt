package com.bgtactician.app.vision

import com.bgtactician.app.data.model.HeroSelectionVisionResult

class StaticJsonHeroSelectionVisionProvider(
    private val rawJson: String
) : HeroSelectionVisionProvider {

    override suspend fun analyze(request: HeroSelectionVisionRequest): HeroSelectionVisionResult {
        val payload = HeroSelectionVisionResponseParser.extractJsonPayload(rawJson)
        return HeroSelectionVisionJsonCodec.decode(payload)
    }
}
