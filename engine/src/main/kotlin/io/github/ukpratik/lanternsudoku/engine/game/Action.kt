// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.game

/** Player intents (LLD §4.7). */
sealed interface Action {
    data class TapCell(val cell: Int) : Action
    data class TapDigit(val digit: Int) : Action
    data class LongPressDigit(val digit: Int) : Action
    data object TapErase : Action
    data object ToggleNotes : Action
    data object Undo : Action
    data object Redo : Action
    data object Back : Action
    data object ShowThem : Action
    data object KeepLooking : Action
    data object KeepPlaying : Action
    data object Restart : Action

    /**
     * Fill notes (US-009): every empty cell's notes become the digits no given or entry in its row, column or box already
     * shows. Entries are untouched; one undo step. The screen asks first when notes already exist.
     */
    data object FillNotes : Action

    // Teaching hints (US-012). The panel's stage changes are actions so the counters live in the reducer.
    /** Tap Hint: error first, else the simplest step, else the fallback (which places its digit at once). */
    data object OpenHint : Action

    /** The panel's primary button: Where → Why → Do it (stage 3 places or removes the digit). */
    data object HintNext : Action

    /** Tap an earlier stage in the stepper (1 or 2) before the digit is placed. */
    data class HintStage(val stage: Int) : Action

    /** A chain's "Step k of N" Next/Back (D-031): show step [step] (0-based). */
    data class HintStep(val step: Int) : Action

    /** Close, Got it or Done. Back does the same (design §8.2: the panel closes before anything else). */
    data object CloseHint : Action
}

/** Settings that change how the reducer behaves (US-006, US-010). Read fresh at every action. */
data class Rules(val showMistakes: Boolean = true, val mistakeLimit: Int = 0, val autoCleanup: Boolean = true) {
    init {
        require(mistakeLimit >= 0) { "mistakeLimit >= 0" }
    }
}

enum class HapticKind { TICK, DOUBLE_TICK, LONG_PRESS, CONFIRM, CONTEXT_CLICK }

enum class Panel { LIMIT, ALMOST }

/** Side effects the ViewModel maps to the platform (LLD §4.9). */
sealed interface Effect {
    /** The saved part of the state changed: offer a save. */
    data object Persist : Effect
    data class Haptic(val kind: HapticKind) : Effect
    data object Completed : Effect
    data class OpenPanel(val panel: Panel) : Effect

    /** The board was reset to the dealt puzzle: the play clock restarts from 0 (US-010 Restart). */
    data object Restarted : Effect
}

/** Result of one reduction. `handled = false` only for a Back with nothing to consume (the screen then leaves). */
data class Transition(val state: GameState, val effects: List<Effect> = emptyList(), val handled: Boolean = true)
