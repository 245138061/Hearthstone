package com.bgtactician.app.data.local

import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.ResolvedHeroStatOption
import com.bgtactician.app.data.model.Tribe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HeroSelectionSessionState(
    val selectedTribes: Set<Tribe> = emptySet(),
    val recognizedHeroOptions: List<HeroSelectionVisionHeroOption> = emptyList(),
    val recognizedHeroes: List<ResolvedHeroStatOption> = emptyList(),
    val selectedHeroCardId: String? = null,
    val selectedHeroName: String? = null,
    val selectedHeroSlot: Int? = null
)

object HeroSelectionSessionStore {

    private val _state = MutableStateFlow(HeroSelectionSessionState())
    val state = _state.asStateFlow()

    fun updateVisionResult(
        selectedTribes: Set<Tribe>,
        recognizedHeroOptions: List<HeroSelectionVisionHeroOption>,
        recognizedHeroes: List<ResolvedHeroStatOption>
    ) {
        _state.update { current ->
            val retainedSelection = recognizedHeroes.firstOrNull { hero ->
                matchesSelection(
                    hero = hero,
                    selectedHeroCardId = current.selectedHeroCardId,
                    selectedHeroName = current.selectedHeroName,
                    selectedHeroSlot = current.selectedHeroSlot
                )
            }
            current.copy(
                selectedTribes = selectedTribes,
                recognizedHeroOptions = recognizedHeroOptions,
                recognizedHeroes = recognizedHeroes,
                selectedHeroCardId = retainedSelection?.heroCardId,
                selectedHeroName = retainedSelection?.displayName ?: retainedSelection?.recognizedName,
                selectedHeroSlot = retainedSelection?.slot
            )
        }
    }

    fun updateManualTribes(
        selectedTribes: Set<Tribe>,
        recognizedHeroes: List<ResolvedHeroStatOption>
    ) {
        _state.update { current ->
            val retainedSelection = recognizedHeroes.firstOrNull { hero ->
                matchesSelection(
                    hero = hero,
                    selectedHeroCardId = current.selectedHeroCardId,
                    selectedHeroName = current.selectedHeroName,
                    selectedHeroSlot = current.selectedHeroSlot
                )
            }
            current.copy(
                selectedTribes = selectedTribes,
                recognizedHeroes = recognizedHeroes,
                selectedHeroCardId = retainedSelection?.heroCardId,
                selectedHeroName = retainedSelection?.displayName ?: retainedSelection?.recognizedName,
                selectedHeroSlot = retainedSelection?.slot
            )
        }
    }

    fun selectHero(hero: ResolvedHeroStatOption) {
        _state.update { current ->
            current.copy(
                selectedHeroCardId = hero.heroCardId,
                selectedHeroName = hero.displayName.ifBlank { hero.recognizedName },
                selectedHeroSlot = hero.slot
            )
        }
    }

    fun clearRecognizedHeroes(selectedTribes: Set<Tribe>? = null) {
        _state.update { current ->
            current.copy(
                selectedTribes = selectedTribes ?: current.selectedTribes,
                recognizedHeroOptions = emptyList(),
                recognizedHeroes = emptyList(),
                selectedHeroCardId = null,
                selectedHeroName = null,
                selectedHeroSlot = null
            )
        }
    }

    private fun matchesSelection(
        hero: ResolvedHeroStatOption,
        selectedHeroCardId: String?,
        selectedHeroName: String?,
        selectedHeroSlot: Int?
    ): Boolean {
        if (!selectedHeroCardId.isNullOrBlank() && hero.heroCardId == selectedHeroCardId) return true
        if (selectedHeroSlot != null && hero.slot == selectedHeroSlot) return true
        if (!selectedHeroName.isNullOrBlank()) {
            return hero.displayName == selectedHeroName || hero.recognizedName == selectedHeroName
        }
        return false
    }
}
