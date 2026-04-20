package com.bgtactician.app.autodetect

data class TavernTierMonitorState(
    val candidateDetection: TavernTierDetection? = null,
    val candidateHits: Int = 0,
    val stableDetection: TavernTierDetection? = null
)

data class TavernTierMonitorStep(
    val state: TavernTierMonitorState,
    val changed: Boolean,
    val updatedStableDetection: TavernTierDetection?
)

object TavernTierMonitorStabilizer {

    fun advance(
        state: TavernTierMonitorState,
        detection: TavernTierDetection
    ): TavernTierMonitorStep {
        val nextCandidate = detection
        val nextHits = if (matches(state.candidateDetection, nextCandidate)) {
            state.candidateHits + 1
        } else {
            1
        }
        val requiredHits = if (nextCandidate.tier != null) 2 else 3
        val changed = nextHits >= requiredHits && !matches(state.stableDetection, nextCandidate)
        val nextStable = if (changed) nextCandidate else state.stableDetection
        return TavernTierMonitorStep(
            state = TavernTierMonitorState(
                candidateDetection = nextCandidate,
                candidateHits = nextHits,
                stableDetection = nextStable
            ),
            changed = changed,
            updatedStableDetection = if (changed) nextCandidate else null
        )
    }

    private fun matches(
        left: TavernTierDetection?,
        right: TavernTierDetection?
    ): Boolean {
        val leftTier = left?.tier
        val rightTier = right?.tier
        return if (leftTier != null || rightTier != null) {
            leftTier == rightTier
        } else {
            left != null && right != null
        }
    }
}
