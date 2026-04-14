package com.bgtactician.app.autodetect

import com.bgtactician.app.data.model.AutoDetectStatus
import com.bgtactician.app.data.model.Tribe

data class AutoDetectUpdate(
    val status: AutoDetectStatus,
    val lockedAvailableTribes: Set<Tribe>? = null,
    val nextIntervalMillis: Long
)

class AutoDetectStateMachine(
    private val targetAvailableTribes: Int = 5
) {
    private val allTribes = Tribe.entries.toSet()
    private val availableVotes = mutableMapOf<Tribe, Int>()
    private val bannedVotes = mutableMapOf<Tribe, Int>()

    private var status: AutoDetectStatus = AutoDetectStatus.WAITING
    private var emptySignalCount = 0
    private var lockedMissCount = 0

    fun reset() {
        status = AutoDetectStatus.WAITING
        emptySignalCount = 0
        lockedMissCount = 0
        availableVotes.clear()
        bannedVotes.clear()
    }

    fun step(observation: DetectionObservation): AutoDetectUpdate {
        if (!observation.hasFrame) {
            reset()
            return AutoDetectUpdate(
                status = status,
                nextIntervalMillis = WAITING_INTERVAL_MS
            )
        }

        if (status == AutoDetectStatus.LOCKED) {
            lockedMissCount = if (observation.lobbyVisible) 0 else lockedMissCount + 1
            if (lockedMissCount >= 2) {
                reset()
            }
            return AutoDetectUpdate(
                status = status,
                nextIntervalMillis = intervalFor(status)
            )
        }

        val hasTribeSignal = observation.availableTribes.isNotEmpty() || observation.bannedTribes.isNotEmpty()
        if (!observation.lobbyVisible && !hasTribeSignal) {
            emptySignalCount += 1
            status = if (observation.attentionRequired && emptySignalCount >= 2) {
                AutoDetectStatus.NEEDS_ATTENTION
            } else {
                AutoDetectStatus.WAITING
            }
            decayVotes()
            return AutoDetectUpdate(
                status = status,
                nextIntervalMillis = intervalFor(status)
            )
        }

        emptySignalCount = 0
        status = AutoDetectStatus.SCANNING
        if (!hasTribeSignal) {
            decayVotes()
            return AutoDetectUpdate(
                status = status,
                nextIntervalMillis = intervalFor(status)
            )
        }
        ingestVotes(observation.availableTribes, observation.bannedTribes)

        val stableBanned = bannedVotes.filterValues { it >= 2 }.keys - availableVotes.filterValues { it >= 2 }.keys
        val stableAvailable = availableVotes.filterValues { it >= 2 }.keys - stableBanned
        val inferredAvailable = when {
            stableAvailable.size == targetAvailableTribes -> stableAvailable
            stableBanned.size == allTribes.size - targetAvailableTribes -> allTribes - stableBanned
            else -> emptySet()
        }

        if (inferredAvailable.size == targetAvailableTribes) {
            status = AutoDetectStatus.LOCKED
            lockedMissCount = 0
            return AutoDetectUpdate(
                status = status,
                lockedAvailableTribes = inferredAvailable,
                nextIntervalMillis = intervalFor(status)
            )
        }

        if (observation.attentionRequired && !observation.lobbyVisible) {
            status = AutoDetectStatus.NEEDS_ATTENTION
        }

        return AutoDetectUpdate(
            status = status,
            nextIntervalMillis = intervalFor(status)
        )
    }

    private fun ingestVotes(
        available: Set<Tribe>,
        banned: Set<Tribe>
    ) {
        available.forEach { tribe ->
            availableVotes[tribe] = availableVotes.getOrDefault(tribe, 0) + 2
            bannedVotes[tribe] = (bannedVotes.getOrDefault(tribe, 0) - 1).coerceAtLeast(0)
        }
        banned.forEach { tribe ->
            bannedVotes[tribe] = bannedVotes.getOrDefault(tribe, 0) + 2
            availableVotes[tribe] = (availableVotes.getOrDefault(tribe, 0) - 1).coerceAtLeast(0)
        }
    }

    private fun decayVotes() {
        availableVotes.replaceAll { _, value -> (value - 1).coerceAtLeast(0) }
        bannedVotes.replaceAll { _, value -> (value - 1).coerceAtLeast(0) }
    }

    private fun intervalFor(status: AutoDetectStatus): Long = when (status) {
        AutoDetectStatus.WAITING -> WAITING_INTERVAL_MS
        AutoDetectStatus.SCANNING -> SCANNING_INTERVAL_MS
        AutoDetectStatus.LOCKED -> LOCKED_INTERVAL_MS
        AutoDetectStatus.NEEDS_ATTENTION -> ATTENTION_INTERVAL_MS
    }

    private companion object {
        const val WAITING_INTERVAL_MS = 3_000L
        const val SCANNING_INTERVAL_MS = 1_000L
        const val LOCKED_INTERVAL_MS = 12_000L
        const val ATTENTION_INTERVAL_MS = 2_000L
    }
}
