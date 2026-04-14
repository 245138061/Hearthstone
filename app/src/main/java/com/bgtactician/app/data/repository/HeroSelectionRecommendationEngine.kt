package com.bgtactician.app.data.repository

import com.bgtactician.app.data.model.BattlegroundHeroNameEntry
import com.bgtactician.app.data.model.BattlegroundHeroNameIndex
import com.bgtactician.app.data.model.BattlegroundCardStatsCatalog
import com.bgtactician.app.data.model.BattlegroundCardStatsEntry
import com.bgtactician.app.data.model.BattlegroundCardTurnStats
import com.bgtactician.app.data.model.BattlegroundHeroStatsCatalog
import com.bgtactician.app.data.model.HeroRecommendationTier
import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.HeroStatsMatchSource
import com.bgtactician.app.data.model.HeroStrategyRecommendation
import com.bgtactician.app.data.model.ResolvedHeroStatOption
import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.Tribe
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import java.util.Locale

object HeroSelectionRecommendationEngine {

    private data class HeroAffinityProfile(
        val preferredTribes: Set<Tribe>
    )

    private data class StrategyFitSnapshot(
        val strategy: StrategyComp,
        val score: Int,
        val overlap: Int,
        val requiredTribes: Set<Tribe>,
        val affinityOverlap: Int,
        val bestLobbyMatches: Boolean,
        val cardProfile: StrategyCardProfile?
    )

    private data class HeroScoreSnapshot(
        val hero: ResolvedHeroStatOption,
        val recommendation: HeroStrategyRecommendation,
        val strategyFit: StrategyFitSnapshot?,
        val heroBaseScore: Int
    )

    private data class FallbackPlan(
        val strategy: StrategyComp,
        val score: Int,
        val sharedRequiredTribes: Int,
        val sharedKeyMinions: Int,
        val sharedUpgradeTurns: Int,
        val cardProfile: StrategyCardProfile?
    )

    private data class StrategyCardProfile(
        val topCoreCards: List<CardSignal>,
        val pivotCoreCards: List<CardSignal>,
        val scoreBonus: Int,
        val stableTurnHint: Int?
    )

    private data class CardSignal(
        val name: String,
        val cardId: String,
        val weight: Double,
        val impactDelta: Double,
        val totalPlayed: Int,
        val stableTurn: Int?
    )

    fun resolveRecognizedHeroes(
        heroOptions: List<HeroSelectionVisionHeroOption>,
        heroStatsCatalog: BattlegroundHeroStatsCatalog,
        cardStatsCatalog: BattlegroundCardStatsCatalog,
        heroNameIndex: BattlegroundHeroNameIndex,
        selectedTribes: Set<Tribe>,
        allStrategies: List<StrategyComp>
    ): List<ResolvedHeroStatOption> {
        if (heroOptions.isEmpty()) return emptyList()

        val statsById = heroStatsCatalog.heroStats.associateBy { it.heroCardId }
        val cardStatsById = cardStatsCatalog.cardStats.associateBy { it.cardId }
        val namesById = heroNameIndex.heroes.associateBy { it.heroCardId }
        val playableStrategies = StrategyEngine.filter(allStrategies, selectedTribes)

        val resolved = heroOptions
            .sortedBy(HeroSelectionVisionHeroOption::slot)
            .map { option ->
                val directCardId = option.heroCardId?.takeIf { statsById.containsKey(it) }
                val matchedCardId = directCardId ?: option.name?.let { resolveHeroCardIdByName(it, heroNameIndex) }
                val stats = matchedCardId?.let(statsById::get)
                val nameEntry = matchedCardId?.let(namesById::get)
                val relevantTribeImpacts = stats?.tribeStats
                    .orEmpty()
                    .mapNotNull { tribeStats ->
                        val tribe = Tribe.fromStatsRaceId(tribeStats.tribe) ?: return@mapNotNull null
                        val impact = tribeStats.impactAveragePositionVsMissingTribe
                            ?: tribeStats.impactAveragePosition
                            ?: return@mapNotNull null
                        tribe to impact
                    }
                    .filter { (tribe, _) -> selectedTribes.isEmpty() || tribe in selectedTribes }
                val synergyTribes = relevantTribeImpacts
                    .filter { it.second <= -0.03 }
                    .map { it.first }
                val bestImpact = relevantTribeImpacts.minByOrNull { it.second }
                val worstImpact = relevantTribeImpacts.maxByOrNull { it.second }
                val displayName = nameEntry?.localizedName
                    ?.takeIf(String::isNotBlank)
                    ?: nameEntry?.name?.takeIf(String::isNotBlank)
                    ?: option.name?.trim()?.takeIf(String::isNotBlank)
                    ?: "未知英雄"

                ResolvedHeroStatOption(
                    slot = option.slot,
                    recognizedName = option.name?.trim(),
                    heroCardId = matchedCardId,
                    displayName = displayName,
                    localizedName = nameEntry?.localizedName?.takeIf(String::isNotBlank),
                    armor = option.armor,
                    matchSource = when {
                        directCardId != null -> HeroStatsMatchSource.HERO_CARD_ID
                        matchedCardId != null -> HeroStatsMatchSource.HERO_NAME_ALIAS
                        else -> HeroStatsMatchSource.NONE
                    },
                    averagePosition = stats?.averagePosition,
                    conservativePositionEstimate = stats?.conservativePositionEstimate,
                    dataPoints = stats?.dataPoints,
                    totalOffered = stats?.totalOffered,
                    totalPicked = stats?.totalPicked,
                    synergyTribes = synergyTribes,
                    bestLobbyTribe = bestImpact?.first,
                    bestLobbyImpact = bestImpact?.second,
                    worstLobbyTribe = worstImpact?.first,
                    worstLobbyImpact = worstImpact?.second
                )
            }

        val scored = resolved.map { hero ->
            buildRecommendation(
                hero = hero,
                selectedTribes = selectedTribes,
                playableStrategies = playableStrategies,
                cardStatsById = cardStatsById
            )
        }
        val ordered = scored.sortedWith(
            compareByDescending<HeroScoreSnapshot> { it.recommendation.score }
                .thenByDescending { it.hero.averagePosition?.let { avg -> -avg } ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { -(it.hero.bestLobbyImpact ?: Double.POSITIVE_INFINITY) }
                .thenByDescending { it.hero.dataPoints ?: 0 }
                .thenBy { it.hero.slot }
        )
        val bestScore = ordered.firstOrNull()?.recommendation?.score ?: 0

        return ordered.map { snapshot ->
            snapshot.hero.copy(
                recommendation = snapshot.recommendation.copy(
                    tier = resolveTier(snapshot.recommendation.score, bestScore)
                )
            )
        }.sortedBy(ResolvedHeroStatOption::slot)
    }

    private fun buildRecommendation(
        hero: ResolvedHeroStatOption,
        selectedTribes: Set<Tribe>,
        playableStrategies: List<StrategyComp>,
        cardStatsById: Map<String, BattlegroundCardStatsEntry>
    ): HeroScoreSnapshot {
        val heroBaseScore = heroBaseScore(hero)
        val affinity = hero.heroCardId?.let(HERO_AFFINITY_PROFILES::get)
        val bestStrategy = playableStrategies
            .map { strategy -> strategyFitSnapshot(hero, strategy, affinity, cardStatsById) }
            .maxByOrNull(StrategyFitSnapshot::score)

        val strategy = bestStrategy?.strategy
        val strategyScore = bestStrategy?.score ?: 0
        val finalScore = (heroBaseScore + strategyScore).coerceAtLeast(0)
        val reason = recommendationReason(
            hero = hero,
            selectedTribes = selectedTribes,
            strategyFit = bestStrategy,
            affinity = affinity
        )
        val fallbackPlan = strategy?.let { primary ->
            resolveFallbackPlan(
                primary = primary,
                primaryCardProfile = bestStrategy.cardProfile,
                playableStrategies = playableStrategies,
                cardStatsById = cardStatsById
            )
        }
        val summary = recommendationSummary(
            hero = hero,
            strategyFit = bestStrategy,
            selectedTribes = selectedTribes,
            affinity = affinity,
            fallbackPlan = fallbackPlan
        )

        return HeroScoreSnapshot(
            hero = hero,
            heroBaseScore = heroBaseScore,
            strategyFit = bestStrategy,
            recommendation = HeroStrategyRecommendation(
                tier = HeroRecommendationTier.GOOD_PICK,
                score = finalScore,
                recommendedCompId = strategy?.id,
                recommendedCompName = strategy?.name,
                fallbackCompId = fallbackPlan?.strategy?.id,
                fallbackCompName = fallbackPlan?.strategy?.name,
                pivotHint = strategy?.let { primary ->
                    fallbackPlan?.let { fallback ->
                        buildPivotHint(
                            primary = primary,
                            primaryCardProfile = bestStrategy.cardProfile,
                            fallback = fallback.strategy,
                            fallbackCardProfile = fallback.cardProfile
                        )
                    }
                },
                reason = reason,
                summary = summary
            )
        )
    }

    private fun heroBaseScore(hero: ResolvedHeroStatOption): Int {
        val avgScore = hero.averagePosition
            ?.let { ((8.5 - it).coerceIn(0.0, 5.0) * 10.0).roundToInt() }
            ?: 16
        val synergyScore = hero.synergyCount * 8
        val impactScore = hero.bestLobbyImpact
            ?.let { (-it * 120).roundToInt().coerceIn(0, 20) }
            ?: 0
        val sampleScore = hero.dataPoints
            ?.takeIf { it > 0 }
            ?.let { (ln(it.toDouble() + 1.0) * 2.6).roundToInt().coerceAtMost(12) }
            ?: 0
        return avgScore + synergyScore + impactScore + sampleScore
    }

    private fun strategyFitSnapshot(
        hero: ResolvedHeroStatOption,
        strategy: StrategyComp,
        affinity: HeroAffinityProfile?,
        cardStatsById: Map<String, BattlegroundCardStatsEntry>
    ): StrategyFitSnapshot {
        val required = strategy.requiredTribes.mapNotNull(Tribe::fromWireName).toSet()
        val cardProfile = buildStrategyCardProfile(strategy, cardStatsById)
        val overlap = required.count { it in hero.synergyTribes }
        val bestLobbyMatches = hero.bestLobbyTribe != null && hero.bestLobbyTribe in required
        val bestLobbyBonus = if (bestLobbyMatches) 8 else 0
        val flexibleBonus = if (required.isEmpty()) 4 else 0
        val tierBonus = when (strategy.tier.uppercase()) {
            "T0", "S" -> 12
            "T1", "A" -> 8
            "T2", "B" -> 5
            else -> 2
        }
        val affinityOverlap = affinity?.preferredTribes.orEmpty().count { it in required }
        val affinityBonus = affinityOverlap * 18
        val affinityFullMatchBonus = if (required.isNotEmpty() && affinityOverlap == required.size) 10 else 0
        val affinityMismatchPenalty = if (
            affinity != null &&
            required.isNotEmpty() &&
            affinityOverlap == 0
        ) {
            10
        } else {
            0
        }
        val score = overlap * 12 +
            bestLobbyBonus +
            flexibleBonus +
            tierBonus +
            affinityBonus +
            affinityFullMatchBonus -
            affinityMismatchPenalty +
            (cardProfile?.scoreBonus ?: 0)
        return StrategyFitSnapshot(
            strategy = strategy,
            score = score,
            overlap = overlap,
            requiredTribes = required,
            affinityOverlap = affinityOverlap,
            bestLobbyMatches = bestLobbyMatches,
            cardProfile = cardProfile
        )
    }

    private fun recommendationReason(
        hero: ResolvedHeroStatOption,
        selectedTribes: Set<Tribe>,
        strategyFit: StrategyFitSnapshot?,
        affinity: HeroAffinityProfile?
    ): String {
        val strategy = strategyFit?.strategy
        val bestTribe = hero.bestLobbyTribe
        val bestImpact = hero.bestLobbyImpact
        val strategyTribesLabel = strategyFit?.requiredTribes
            .orEmpty()
            .joinToString(" / ") { it.label }
        val affinityLabel = affinity?.preferredTribes
            .orEmpty()
            .filter { it in selectedTribes }
            .joinToString(" / ") { it.label }

        return when {
            strategy != null && strategyFit.affinityOverlap > 0 && affinityLabel.isNotBlank() ->
                "英雄机制偏$affinityLabel，能更稳承接${strategy.name}"
            strategy != null && strategyFit.bestLobbyMatches && bestTribe != null && bestImpact != null ->
                "${bestTribe.label}环境收益${formatImpactLabel(bestImpact)}，更适合走${strategy.name}"
            strategy != null && (strategyFit.cardProfile?.scoreBonus ?: 0) >= 7 ->
                "${strategy.name}核心牌样本更稳，成型上限更高"
            strategy != null && strategyFit.overlap >= 2 && strategyTribesLabel.isNotBlank() ->
                "与${strategyTribesLabel}有${strategyFit.overlap}项契合，适合走${strategy.name}"
            strategy != null && strategyFit.overlap == 1 && strategyTribesLabel.isNotBlank() ->
                "能吃到${strategyTribesLabel}的一项关键配合，可转${strategy.name}"
            hero.synergyCount >= 3 ->
                "当前五族契合高，但更偏泛用强势打法"
            hero.bestLobbyImpact != null && hero.bestLobbyImpact <= -0.08 ->
                "当前环境收益高，属于这局的强势英雄"
            hero.averagePosition != null && hero.averagePosition <= 4.1 ->
                "基础强度高，这局拿来保分更稳"
            strategy != null ->
                "当前环境下能承接${strategy.name}"
            selectedTribes.isNotEmpty() ->
                "当前环境可玩，但路线弹性一般"
            else ->
                "数据较少，建议谨慎选择"
        }
    }

    private fun recommendationSummary(
        hero: ResolvedHeroStatOption,
        strategyFit: StrategyFitSnapshot?,
        selectedTribes: Set<Tribe>,
        affinity: HeroAffinityProfile?,
        fallbackPlan: FallbackPlan?
    ): String {
        val strategy = strategyFit?.strategy
        val affinityLabel = affinity?.preferredTribes
            .orEmpty()
            .filter { it in selectedTribes }
            .joinToString(" / ") { it.label }
        val fallbackLabel = fallbackPlan?.strategy?.name

        return when {
            strategy != null && strategyFit.affinityOverlap > 0 && affinityLabel.isNotBlank() ->
                buildSummaryLine(
                    primary = "优先走 ${strategy.name}，英雄和${affinityLabel}更合拍",
                    fallback = fallbackLabel
                )
            strategy != null && strategyFit.bestLobbyMatches && hero.bestLobbyTribe != null ->
                buildSummaryLine(
                    primary = "优先走 ${strategy.name}，当前${hero.bestLobbyTribe.label}环境更吃香",
                    fallback = fallbackLabel
                )
            strategy != null && strategyFit.overlap >= 2 ->
                buildSummaryLine(
                    primary = "建议走 ${strategy.name}，环境契合度更高",
                    fallback = fallbackLabel
                )
            strategy != null && (strategyFit.cardProfile?.scoreBonus ?: 0) >= 7 ->
                buildSummaryLine(
                    primary = "优先走 ${strategy.name}，核心牌数据更扎实",
                    fallback = fallbackLabel
                )
            strategy != null ->
                buildSummaryLine(
                    primary = "可走 ${strategy.name}",
                    fallback = fallbackLabel
                )
            hero.averagePosition != null ->
                "可作为稳健选择"
            else ->
                "信息不足，谨慎选择"
        }
    }

    private fun resolveFallbackPlan(
        primary: StrategyComp,
        primaryCardProfile: StrategyCardProfile?,
        playableStrategies: List<StrategyComp>,
        cardStatsById: Map<String, BattlegroundCardStatsEntry>
    ): FallbackPlan? {
        val primaryRequired = primary.requiredTribes.mapNotNull(Tribe::fromWireName).toSet()
        val primaryKeyMinions = strategyKeyMinionTokens(primary)
        val primaryUpgradeTurns = primary.upgradeTurns.map(::normalizeRuleToken).toSet()

        return playableStrategies
            .asSequence()
            .filter { it.id != primary.id }
            .map { candidate ->
                val candidateRequired = candidate.requiredTribes.mapNotNull(Tribe::fromWireName).toSet()
                val candidateKeyMinions = strategyKeyMinionTokens(candidate)
                val candidateUpgradeTurns = candidate.upgradeTurns.map(::normalizeRuleToken).toSet()
                val candidateCardProfile = buildStrategyCardProfile(candidate, cardStatsById)
                val sharedRequired = primaryRequired.intersect(candidateRequired).size
                val sharedKeys = primaryKeyMinions.intersect(candidateKeyMinions).size
                val sharedTurns = primaryUpgradeTurns.intersect(candidateUpgradeTurns).size
                val sameTrackBonus = when {
                    primaryRequired.isNotEmpty() && primaryRequired == candidateRequired -> 28
                    primaryRequired.isNotEmpty() && sharedRequired > 0 -> 18
                    primaryRequired.isEmpty() && candidateRequired.isEmpty() -> 8
                    else -> 0
                }
                val pivotTimingBonus = timingBonus(
                    primaryTurn = primaryCardProfile?.stableTurnHint,
                    fallbackTurn = candidateCardProfile?.stableTurnHint
                )
                val tierGapPenalty = abs(tierRank(primary.tier) - tierRank(candidate.tier)) * 2
                val score = sharedRequired * 26 +
                    sharedKeys * 14 +
                    sharedTurns * 5 +
                    sameTrackBonus -
                    tierGapPenalty +
                    (candidateCardProfile?.scoreBonus ?: 0) +
                    pivotTimingBonus
                FallbackPlan(
                    strategy = candidate,
                    score = score,
                    sharedRequiredTribes = sharedRequired,
                    sharedKeyMinions = sharedKeys,
                    sharedUpgradeTurns = sharedTurns,
                    cardProfile = candidateCardProfile
                )
            }
            .filter { it.score >= 16 }
            .sortedWith(
                compareByDescending<FallbackPlan> { it.score }
                    .thenByDescending { it.sharedRequiredTribes }
                    .thenByDescending { it.sharedKeyMinions }
                    .thenByDescending { it.sharedUpgradeTurns }
                    .thenBy { it.strategy.name }
            )
            .firstOrNull()
    }

    private fun buildPivotHint(
        primary: StrategyComp,
        primaryCardProfile: StrategyCardProfile?,
        fallback: StrategyComp,
        fallbackCardProfile: StrategyCardProfile?
    ): String {
        val commit = primary.whenToCommit?.trim().orEmpty()
        val coreSignals = primaryCardProfile?.topCoreCards.orEmpty()
        val pivotSignals = primaryCardProfile?.pivotCoreCards.orEmpty()
        val coreNames = pivotSignals
            .map(CardSignal::name)
            .filter(String::isNotBlank)
            .distinct()
            .take(2)
            .ifEmpty {
                primary.keyMinions
                    .filter { it.phase.contains("主核") }
                    .filterNot { isGenericPivotSupport(it.name) }
                    .map { it.name.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(2)
            }
            .ifEmpty {
                coreSignals
                    .map(CardSignal::name)
                    .filter(String::isNotBlank)
                    .distinct()
                    .take(2)
            }
            .ifEmpty {
                primary.keyMinions
                    .filter { it.phase.contains("主核") }
                    .map { it.name.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(2)
            }
        val stableTurnHint = primaryCardProfile?.stableTurnHint?.plus(3)?.coerceIn(7, 10)
        val fallbackTurnHint = fallbackCardProfile?.stableTurnHint?.coerceIn(6, 10)
        return when {
            stableTurnHint != null && coreNames.isNotEmpty() && fallbackTurnHint != null ->
                "到${stableTurnHint}回合还没见到${coreNames.joinToString(" / ")}，就转${fallback.name}（${fallbackTurnHint}回合开始留意核心）"
            stableTurnHint != null && coreNames.isNotEmpty() ->
                "到${stableTurnHint}回合还没见到${coreNames.joinToString(" / ")}，就转${fallback.name}"
            commit.isNotBlank() ->
                "如果迟迟凑不齐$commit，就转${fallback.name}"
            coreNames.isNotEmpty() ->
                "如果一直刷不到${coreNames.joinToString(" / ")}，就转${fallback.name}"
            else ->
                "如果主核一直不来，就转${fallback.name}"
        }
    }

    private fun buildSummaryLine(primary: String, fallback: String?): String {
        return if (fallback.isNullOrBlank()) {
            primary
        } else {
            "$primary；卡核心就转$fallback"
        }
    }

    private fun strategyKeyMinionTokens(strategy: StrategyComp): Set<String> {
        return strategy.keyMinions
            .mapNotNull { minion ->
                when {
                    !minion.cardId.isNullOrBlank() -> normalizeRuleToken(minion.cardId)
                    minion.name.isNotBlank() -> normalizeRuleToken(minion.name)
                    else -> null
                }
            }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun buildStrategyCardProfile(
        strategy: StrategyComp,
        cardStatsById: Map<String, BattlegroundCardStatsEntry>
    ): StrategyCardProfile? {
        val signals = strategy.keyMinions
            .mapNotNull { minion ->
                val cardId = minion.cardId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val stats = cardStatsById[cardId] ?: return@mapNotNull null
                val impactDelta = cardImpactDelta(stats) ?: return@mapNotNull null
                val signal = CardSignal(
                    name = minion.name.trim(),
                    cardId = cardId,
                    weight = phaseWeight(minion.phase),
                    impactDelta = impactDelta,
                    totalPlayed = stats.totalPlayed,
                    stableTurn = resolveStableTurn(stats)
                )
                signal.takeIf { it.totalPlayed >= MIN_CARD_SAMPLE }
            }
        if (signals.isEmpty()) return null

        val topCoreCards = signals
            .filter { it.weight >= 1.0 }
            .ifEmpty { signals }
            .sortedByDescending { it.impactDelta * it.weight * sampleFactor(it.totalPlayed) }
            .take(3)
        val pivotCoreCards = signals
            .filter { it.weight >= 1.0 }
            .filterNot { isGenericPivotSupport(it.name) }
            .ifEmpty { signals.filter { it.weight >= 1.0 } }
            .ifEmpty { signals }
            .take(3)
        val weightedImpact = topCoreCards
            .sumOf { it.impactDelta * it.weight * sampleFactor(it.totalPlayed) } / topCoreCards.size
        val stableTurns = pivotCoreCards.mapNotNull(CardSignal::stableTurn).sorted()

        return StrategyCardProfile(
            topCoreCards = topCoreCards,
            pivotCoreCards = pivotCoreCards,
            scoreBonus = (weightedImpact * 8.0).roundToInt().coerceIn(0, 10),
            stableTurnHint = stableTurns.getOrNull(stableTurns.lastIndex / 2)
        )
    }

    private fun isGenericPivotSupport(name: String): Boolean {
        val trimmed = name.trim()
        return GENERIC_PIVOT_SUPPORT_KEYWORDS.any(trimmed::contains)
    }

    private fun timingBonus(primaryTurn: Int?, fallbackTurn: Int?): Int {
        if (primaryTurn == null || fallbackTurn == null) return 0
        return (primaryTurn - fallbackTurn).coerceIn(-2, 3) * 3
    }

    private fun phaseWeight(phase: String): Double = when {
        phase.contains("主核") -> 1.0
        phase.contains("补强") -> 0.58
        phase.contains("经济") -> 0.42
        phase.contains("过渡") -> 0.34
        else -> 0.45
    }

    private fun sampleFactor(totalPlayed: Int): Double = when {
        totalPlayed >= 120_000 -> 1.0
        totalPlayed >= 30_000 -> 0.86
        totalPlayed >= 8_000 -> 0.72
        else -> 0.58
    }

    private fun cardImpactDelta(entry: BattlegroundCardStatsEntry): Double? {
        val averagePlacement = entry.averagePlacement ?: return null
        val averagePlacementOther = entry.averagePlacementOther ?: return null
        return averagePlacementOther - averagePlacement
    }

    private fun resolveStableTurn(entry: BattlegroundCardStatsEntry): Int? {
        return entry.turnStats
            .asSequence()
            .filter { it.totalPlayed >= MIN_TURN_SAMPLE }
            .mapNotNull { turn ->
                val delta = cardTurnImpactDelta(turn) ?: return@mapNotNull null
                turn.turn.takeIf { delta >= MIN_TURN_DELTA }
            }
            .minOrNull()
    }

    private fun cardTurnImpactDelta(entry: BattlegroundCardTurnStats): Double? {
        val averagePlacement = entry.averagePlacement ?: return null
        val averagePlacementOther = entry.averagePlacementOther ?: return null
        return averagePlacementOther - averagePlacement
    }

    private fun normalizeRuleToken(value: String): String {
        return value.trim().lowercase().replace(HERO_LOOKUP_NOISE_REGEX, "")
    }

    private fun tierRank(tier: String): Int = when (tier.uppercase()) {
        "T0", "S" -> 0
        "T1", "A" -> 1
        "T2", "B" -> 2
        else -> 3
    }

    private fun formatImpactLabel(value: Double): String {
        return String.format(Locale.US, "%+.2f", value)
    }

    private fun resolveTier(score: Int, bestScore: Int): HeroRecommendationTier {
        val gap = bestScore - score
        return when {
            score >= bestScore - 4 -> HeroRecommendationTier.TOP_PICK
            score >= bestScore - 12 -> HeroRecommendationTier.GOOD_PICK
            score >= bestScore - 22 -> HeroRecommendationTier.NICHE
            else -> HeroRecommendationTier.AVOID
        }
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

    private const val MIN_CARD_SAMPLE = 200
    private const val MIN_TURN_SAMPLE = 800
    private const val MIN_TURN_DELTA = 0.45
    private val GENERIC_PIVOT_SUPPORT_KEYWORDS = listOf("布莱恩", "铜须", "提图斯", "瑞文戴尔")

    private val HERO_AFFINITY_PROFILES = mapOf(
        "TB_BaconShop_HERO_17" to HeroAffinityProfile(setOf(Tribe.MECH)),
        "TB_BaconShop_HERO_18" to HeroAffinityProfile(setOf(Tribe.PIRATE)),
        "TB_BaconShop_HERO_37" to HeroAffinityProfile(setOf(Tribe.DEMON)),
        "TB_BaconShop_HERO_53" to HeroAffinityProfile(setOf(Tribe.DRAGON)),
        "TB_BaconShop_HERO_55" to HeroAffinityProfile(setOf(Tribe.MURLOC)),
        "TB_BaconShop_HERO_64" to HeroAffinityProfile(setOf(Tribe.PIRATE)),
        "TB_BaconShop_HERO_67" to HeroAffinityProfile(setOf(Tribe.PIRATE)),
        "TB_BaconShop_HERO_78" to HeroAffinityProfile(setOf(Tribe.ELEMENTAL)),
        "BG22_HERO_007" to HeroAffinityProfile(setOf(Tribe.NAGA))
    )
}
