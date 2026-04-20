package com.bgtactician.app.autodetect

import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption

private const val HERO_SELECTION_SLOT_COUNT = 4

data class HeroSelectionOcrObservation(
    val slot: Int,
    val heroCardId: String? = null,
    val option: HeroSelectionVisionHeroOption? = null,
    val score: Float = 0f
)

data class HeroSelectionOcrState(
    val slotHistories: List<List<HeroSelectionOcrObservation>> = List(HERO_SELECTION_SLOT_COUNT) { emptyList() },
    val stableSlots: List<HeroSelectionOcrStableSlot?> = List(HERO_SELECTION_SLOT_COUNT) { null }
)

data class HeroSelectionOcrStableSlot(
    val slot: Int,
    val heroCardId: String,
    val option: HeroSelectionVisionHeroOption,
    val averageScore: Float,
    val hitCount: Int,
    val recentHitCount: Int,
    val missCount: Int = 0
)

data class HeroSelectionOcrSlotLock(
    val slot: Int,
    val heroCardId: String,
    val option: HeroSelectionVisionHeroOption,
    val averageScore: Float,
    val hitCount: Int,
    val recentHitCount: Int
)

data class HeroSelectionOcrStep(
    val state: HeroSelectionOcrState,
    val stableLocks: List<HeroSelectionOcrSlotLock>,
    val stableOptions: List<HeroSelectionVisionHeroOption>,
    val duplicateHeroCardIds: Set<String>,
    val chaoticSlots: Set<Int>
)

object HeroSelectionOcrStabilizer {
    private const val HISTORY_SIZE = 5
    private const val MIN_TOTAL_HITS = 3
    private const val MIN_RECENT_HITS = 2
    private const val MIN_AVERAGE_SCORE = 0.72f
    private const val MAX_STABLE_MISSES = 2
    private const val SWITCH_MIN_TOTAL_HITS = 4
    private const val SWITCH_MIN_RECENT_HITS = 3
    private const val SWITCH_MIN_AVERAGE_SCORE = 0.78f

    fun advance(
        state: HeroSelectionOcrState,
        observations: List<HeroSelectionOcrObservation>
    ): HeroSelectionOcrStep {
        val normalizedObservations = List(HERO_SELECTION_SLOT_COUNT) { slot ->
            observations.firstOrNull { it.slot == slot } ?: HeroSelectionOcrObservation(slot = slot)
        }
        val nextHistories = state.slotHistories.mapIndexed { slot, history ->
            (history + normalizedObservations[slot]).takeLast(HISTORY_SIZE)
        }
        val nextStableSlots = nextHistories.mapIndexed { slot, history ->
            mergeStableSlot(
                slot = slot,
                previous = state.stableSlots.getOrNull(slot),
                history = history
            )
        }
        val locks = nextStableSlots.mapNotNull { stable ->
            stable?.toSlotLock()
        }
        val duplicateHeroCardIds = locks
            .groupingBy(HeroSelectionOcrSlotLock::heroCardId)
            .eachCount()
            .filterValues { it >= 2 }
            .keys
        val stableOptions = locks
            .filterNot { it.heroCardId in duplicateHeroCardIds }
            .sortedBy(HeroSelectionOcrSlotLock::slot)
            .map(HeroSelectionOcrSlotLock::option)
        val chaoticSlots = nextHistories.mapIndexedNotNull { slot, history ->
            slot.takeIf { isChaotic(history) }
        }.toSet()

        return HeroSelectionOcrStep(
            state = HeroSelectionOcrState(
                slotHistories = nextHistories,
                stableSlots = nextStableSlots
            ),
            stableLocks = locks.sortedBy(HeroSelectionOcrSlotLock::slot),
            stableOptions = stableOptions,
            duplicateHeroCardIds = duplicateHeroCardIds,
            chaoticSlots = chaoticSlots
        )
    }

    private fun mergeStableSlot(
        slot: Int,
        previous: HeroSelectionOcrStableSlot?,
        history: List<HeroSelectionOcrObservation>
    ): HeroSelectionOcrStableSlot? {
        val candidates = resolveCandidates(history)
        val qualifiedCandidate = candidates.firstOrNull(::meetsLockThreshold)
        if (previous == null) {
            return qualifiedCandidate?.toStableSlot(slot = slot)
        }

        if (qualifiedCandidate?.heroCardId == previous.heroCardId) {
            return qualifiedCandidate.toStableSlot(slot = slot)
        }

        if (qualifiedCandidate != null && shouldSwitch(previous, qualifiedCandidate)) {
            return qualifiedCandidate.toStableSlot(slot = slot)
        }

        val previousSupport = candidates.firstOrNull { it.heroCardId == previous.heroCardId }
        if (previousSupport != null && previousSupport.recentHitCount > 0) {
            return previousSupport.toStableSlot(slot = slot)
        }

        val nextMissCount = previous.missCount + 1
        if (nextMissCount > MAX_STABLE_MISSES) {
            return qualifiedCandidate?.toStableSlot(slot = slot)
        }

        return previous.copy(
            recentHitCount = previousSupport?.recentHitCount ?: 0,
            hitCount = previousSupport?.hitCount ?: previous.hitCount,
            averageScore = previousSupport?.averageScore ?: previous.averageScore,
            missCount = nextMissCount
        )
    }

    private fun resolveCandidates(
        history: List<HeroSelectionOcrObservation>
    ): List<LockCandidate> {
        val recent = history.takeLast(3)
        return history
            .filter { !it.heroCardId.isNullOrBlank() && it.option != null }
            .groupBy { it.heroCardId!! }
            .mapValues { (_, observations) ->
                val sorted = observations.sortedWith(
                    compareByDescending<HeroSelectionOcrObservation> { it.score }
                        .thenByDescending { history.lastIndexOf(it) }
                )
                val best = sorted.first()
                LockCandidate(
                    heroCardId = best.heroCardId!!,
                    option = best.option!!,
                    averageScore = observations.map(HeroSelectionOcrObservation::score).average().toFloat(),
                    hitCount = observations.size,
                    recentHitCount = recent.count { it.heroCardId == best.heroCardId }
                )
            }
            .values
            .sortedWith(
                compareByDescending<LockCandidate> { it.hitCount }
                    .thenByDescending { it.recentHitCount }
                    .thenByDescending { it.averageScore }
            )
    }

    private fun isChaotic(history: List<HeroSelectionOcrObservation>): Boolean {
        if (history.size < HISTORY_SIZE) return false
        val distinctHeroIds = history
            .mapNotNull(HeroSelectionOcrObservation::heroCardId)
            .distinct()
        return distinctHeroIds.size >= 3
    }

    private fun meetsLockThreshold(candidate: LockCandidate): Boolean {
        return candidate.hitCount >= MIN_TOTAL_HITS &&
            candidate.recentHitCount >= MIN_RECENT_HITS &&
            candidate.averageScore >= MIN_AVERAGE_SCORE
    }

    private fun shouldSwitch(
        previous: HeroSelectionOcrStableSlot,
        candidate: LockCandidate
    ): Boolean {
        return candidate.heroCardId != previous.heroCardId &&
            candidate.hitCount >= SWITCH_MIN_TOTAL_HITS &&
            candidate.recentHitCount >= SWITCH_MIN_RECENT_HITS &&
            candidate.averageScore >= maxOf(SWITCH_MIN_AVERAGE_SCORE, previous.averageScore - 0.04f)
    }

    private fun LockCandidate.toStableSlot(slot: Int): HeroSelectionOcrStableSlot {
        return HeroSelectionOcrStableSlot(
            slot = slot,
            heroCardId = heroCardId,
            option = option,
            averageScore = averageScore,
            hitCount = hitCount,
            recentHitCount = recentHitCount,
            missCount = 0
        )
    }

    private fun HeroSelectionOcrStableSlot.toSlotLock(): HeroSelectionOcrSlotLock {
        return HeroSelectionOcrSlotLock(
            slot = slot,
            heroCardId = heroCardId,
            option = option,
            averageScore = averageScore,
            hitCount = hitCount,
            recentHitCount = recentHitCount
        )
    }

    private data class LockCandidate(
        val heroCardId: String,
        val option: HeroSelectionVisionHeroOption,
        val averageScore: Float,
        val hitCount: Int,
        val recentHitCount: Int
    )
}
