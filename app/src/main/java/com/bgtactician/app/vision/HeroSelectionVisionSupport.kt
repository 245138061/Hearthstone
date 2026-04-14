package com.bgtactician.app.vision

import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.HeroSelectionVisionResult
import com.bgtactician.app.data.model.VisionScreenType

data class HeroSelectionVisionValidation(
    val isValid: Boolean,
    val errors: List<String>
)

object HeroSelectionVisionValidator {

    fun validate(
        result: HeroSelectionVisionResult,
        requireCompleteTribes: Boolean = true
    ): HeroSelectionVisionValidation {
        val distinctTribes = result.availableTribes.distinct()
        val selectableHeroOptions = result.selectableHeroOptions
        val hasStrongHeroSelectionSignal = distinctTribes.size == 5 || selectableHeroOptions.isNotEmpty()
        val errors = buildList {
            if (result.screenType != VisionScreenType.HERO_SELECTION && !hasStrongHeroSelectionSignal) {
                add("screen_type 不是 hero_selection")
            }

            if (requireCompleteTribes && distinctTribes.size != 5) {
                add("available_tribes 去重后不是 5 个")
            }

            if (selectableHeroOptions.isNotEmpty()) {
                if (selectableHeroOptions.size !in 1..4) {
                    add("hero_options 数量不在 1..4")
                }

                val slotCount = selectableHeroOptions.map(HeroSelectionVisionHeroOption::slot).distinct().size
                if (slotCount != selectableHeroOptions.size) {
                    add("hero_options.slot 存在重复")
                }
            }
        }

        return HeroSelectionVisionValidation(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    fun hasCompleteTribes(result: HeroSelectionVisionResult): Boolean {
        return result.availableTribes.distinct().size == 5
    }
}
