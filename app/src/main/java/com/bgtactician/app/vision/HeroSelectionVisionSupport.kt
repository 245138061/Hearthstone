package com.bgtactician.app.vision

import com.bgtactician.app.data.model.BattlegroundHeroNameEntry
import com.bgtactician.app.data.model.BattlegroundHeroNameIndex
import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.HeroSelectionVisionResult
import com.bgtactician.app.data.model.VisionScreenType
import kotlin.math.abs

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

data class HeroSelectionVisionSemanticValidation(
    val isReliable: Boolean,
    val errors: List<String>
)

object HeroSelectionVisionSemanticValidator {

    fun validate(
        result: HeroSelectionVisionResult,
        heroNameIndex: BattlegroundHeroNameIndex
    ): HeroSelectionVisionSemanticValidation {
        val heroOptions = result.selectableHeroOptions
        if (heroOptions.isEmpty()) {
            return HeroSelectionVisionSemanticValidation(
                isReliable = true,
                errors = emptyList()
            )
        }

        val knownHeroIds = heroNameIndex.heroes.map(BattlegroundHeroNameEntry::heroCardId).toHashSet()
        val analyses = heroOptions.map { option ->
            analyzeHeroOption(option, heroNameIndex, knownHeroIds)
        }
        val resolvedIds = analyses.map(OptionAnalysis::resolvedHeroCardId)
        val resolvedCount = resolvedIds.count { it != null }
        val uniqueResolvedCount = resolvedIds.filterNotNull().distinct().size
        val providedIds = heroOptions.mapNotNull { it.heroCardId?.trim()?.takeIf(String::isNotBlank) }
        val invalidProvidedIds = providedIds.filterNot(knownHeroIds::contains)
        val duplicateRawNames = heroOptions
            .mapNotNull { option ->
                option.name
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let(::normalizeHeroLookup)
                    ?.takeIf(String::isNotBlank)
            }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= 2 }
            .keys

        val errors = buildList {
            if (resolvedCount == 0) {
                add("英雄未命中本地索引")
            }
            if (duplicateRawNames.isNotEmpty()) {
                add("多个候选英雄名称重复")
            }
            if (heroOptions.size >= 2 && resolvedCount >= 2 && uniqueResolvedCount == 1) {
                add("多个候选英雄命中同一英雄")
            }
            if (providedIds.isNotEmpty() && invalidProvidedIds.size == providedIds.size) {
                add("hero_card_id 未命中本地索引")
            }
            if (analyses.any(OptionAnalysis::hasNameIdConflict)) {
                add("hero_card_id 与英雄名称冲突")
            }
        }

        return HeroSelectionVisionSemanticValidation(
            isReliable = errors.isEmpty(),
            errors = errors
        )
    }

    private fun analyzeHeroOption(
        option: HeroSelectionVisionHeroOption,
        heroNameIndex: BattlegroundHeroNameIndex,
        knownHeroIds: Set<String>
    ): OptionAnalysis {
        val directCardId = option.heroCardId?.trim()?.takeIf(String::isNotBlank)
        val validDirectCardId = directCardId?.takeIf { it in knownHeroIds }
        val resolvedByName = option.name
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { heroName -> resolveHeroCardIdByName(heroName, heroNameIndex) }

        return OptionAnalysis(
            resolvedHeroCardId = validDirectCardId ?: resolvedByName,
            hasNameIdConflict = validDirectCardId != null &&
                resolvedByName != null &&
                validDirectCardId != resolvedByName
        )
    }

    private fun resolveHeroCardIdByName(
        heroName: String,
        heroNameIndex: BattlegroundHeroNameIndex
    ): String? {
        val normalizedQuery = normalizeHeroLookup(heroName)
        if (normalizedQuery.isBlank()) return null

        heroNameIndex.heroes.firstOrNull { entry ->
            entry.searchAliases().any { normalizeHeroLookup(it) == normalizedQuery }
        }?.let { return it.heroCardId }

        return heroNameIndex.heroes
            .mapNotNull { entry ->
                val bestAlias = entry.searchAliases()
                    .map(::normalizeHeroLookup)
                    .filter(String::isNotBlank)
                    .filter { alias ->
                        alias.contains(normalizedQuery) || normalizedQuery.contains(alias)
                    }
                    .minByOrNull { alias -> abs(alias.length - normalizedQuery.length) }
                    ?: return@mapNotNull null
                entry.heroCardId to abs(bestAlias.length - normalizedQuery.length)
            }
            .minByOrNull { it.second }
            ?.first
    }

    private fun BattlegroundHeroNameEntry.searchAliases(): List<String> {
        return buildList {
            addAll(aliases)
            add(name)
            localizedName.takeIf(String::isNotBlank)?.let(::add)
        }.distinct()
    }

    private fun normalizeHeroLookup(value: String): String {
        return value
            .trim()
            .lowercase()
            .replace(HERO_LOOKUP_NOISE_REGEX, "")
    }

    private val HERO_LOOKUP_NOISE_REGEX =
        Regex("[\\s·•'’`\"“”\\-—_()（）,，.:：!！?？]+")

    private data class OptionAnalysis(
        val resolvedHeroCardId: String?,
        val hasNameIdConflict: Boolean
    )
}
