// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import android.content.res.Resources
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.hint.HintEngine
import io.github.ukpratik.lanternsudoku.engine.hint.HintPlan
import io.github.ukpratik.lanternsudoku.engine.hint.HintSession
import io.github.ukpratik.lanternsudoku.engine.hint.Step
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.engine.technique.Technique

/** A legend swatch under the explanation (design .roles): the shape the board uses for that role. */
enum class LegendKind { HOUSE, TARGET, CAUSE, STRUCK, PLACED }

data class LegendItem(val kind: LegendKind, val text: String, val digit: Int = 0)

/** Everything the hint panel says for the current stage (all copy from strings_hint.xml, US-012). */
data class HintText(
    val status: String,
    val eyebrow: String?,
    val title: String,
    val chip: String?,
    val stepLabel: String?,
    val body: String,
    val legend: List<LegendItem>,
    val meta: String,
    val secondary: String,
    val primary: String,
    /** TalkBack: "Hint, Why. Pointing. In box 8, …" (design hint-stage-2 TalkBack notes). */
    val announcement: String,
)

/**
 * Builds the hint panel's copy from the engine's plan and the string resources, parameterised with the real houses
 * and digits. `:engine` never holds user-facing text (LLD §4.2); this is the one place that turns a [Step] into words.
 */
object HintCopy {
    fun of(res: Resources, s: GameState): HintText? {
        val h = s.input.hint ?: return null
        val hints = s.counters.hints1 + s.counters.hints2 + s.counters.hints3
        return when (val plan = h.plan) {
            is HintPlan.WrongDigit -> wrong(res, h, plan, hints)
            is HintPlan.Fallback -> {
                val p = plan.placement
                val title = res.getString(R.string.hint_placed_title, p.digit, cellName(res, p.cell))
                val body = res.getString(R.string.hint_fallback_body)
                HintText(
                    status = res.getString(R.string.hint_status_placed),
                    eyebrow = res.getString(R.string.hint_fallback_eyebrow),
                    title = title,
                    chip = res.getString(R.string.hint_chip_placed),
                    stepLabel = null,
                    body = body,
                    legend = listOf(LegendItem(LegendKind.PLACED, res.getString(R.string.hint_legend_placed), p.digit)),
                    meta = res.getString(R.string.hint_meta_placed, hints),
                    secondary = res.getString(R.string.hint_undo),
                    primary = res.getString(R.string.hint_done),
                    announcement = announce(res, res.getString(R.string.hint_fallback_announce), title, body),
                )
            }
            is HintPlan.Logical -> logical(res, h, plan, hints)
        }
    }

    private fun logical(res: Resources, h: HintSession, plan: HintPlan.Logical, hints: Int): HintText {
        val first = plan.steps[0]
        return when (h.stage) {
            1 -> {
                val houses = HintEngine.whereHouses(first)
                val title = res.getString(R.string.hint_where_title, housesName(res, houses))
                val kind = kindName(res, houses)
                val body = when (first.technique) {
                    Technique.FULL_HOUSE -> res.getString(R.string.hint_where_full_house, kind)
                    Technique.NAKED_SINGLE -> res.getString(R.string.hint_where_cell, kind)
                    Technique.HIDDEN_SINGLE, Technique.POINTING, Technique.BOX_LINE, Technique.X_WING ->
                        res.getString(R.string.hint_where_digit, Masks.lowestDigit(first.digits), kind)
                    else -> res.getString(R.string.hint_where_pattern, kind)
                }
                HintText(
                    status = res.getString(R.string.hint_status_where), eyebrow = null, title = title, chip = null, stepLabel = null,
                    body = body,
                    legend = listOf(LegendItem(LegendKind.HOUSE, res.getString(R.string.hint_legend_where))),
                    meta = res.getString(R.string.hint_meta, hints),
                    secondary = res.getString(R.string.hint_close), primary = res.getString(R.string.hint_why),
                    announcement = announce(res, res.getString(R.string.hint_stage_where), title, body),
                )
            }
            2 -> {
                val k = if (plan.isChain) h.step else 0
                val step = plan.steps[k]
                val body = if (plan.isChain) chainStep(res, plan, k) else explanation(res, plan)
                val title = techniqueName(res, step.technique)
                val legend = buildList {
                    add(LegendItem(LegendKind.TARGET, res.getString(R.string.hint_legend_target)))
                    if (step.causeCells.isNotEmpty()) add(LegendItem(LegendKind.CAUSE, res.getString(R.string.hint_legend_cause)))
                    if (step.eliminations.isNotEmpty()) {
                        add(LegendItem(LegendKind.STRUCK, res.getString(R.string.hint_legend_struck), step.eliminations[0].digit))
                    }
                }
                HintText(
                    status = res.getString(R.string.hint_status_why), eyebrow = null, title = title, chip = chip(res, step.technique),
                    stepLabel = if (plan.isChain) res.getString(R.string.hint_step_of, k + 1, plan.steps.size) else null,
                    body = body, legend = legend,
                    meta = res.getString(R.string.hint_meta, hints),
                    secondary = res.getString(R.string.hint_got_it), primary = res.getString(R.string.hint_show_digit),
                    announcement = announce(res, res.getString(R.string.hint_stage_why), title, body),
                )
            }
            else -> {
                val p = plan.placement
                val title = res.getString(R.string.hint_placed_title, p.digit, cellName(res, p.cell))
                val name = techniqueName(res, first.technique)
                val house = housesName(res, HintEngine.whereHouses(first))
                val notes = h.notesRemoved > 0
                val body = if (plan.isChain) {
                    // "Pointing in box 8 and 3 more steps showed where the 1 goes."
                    val more = plan.steps.size - 1
                    val lead = res.getQuantityString(R.plurals.hint_chain_lead, more, more, name, house)
                    res.getString(if (notes) R.string.hint_done_chain_notes else R.string.hint_done_chain_plain, lead, p.digit)
                } else {
                    res.getString(if (notes) R.string.hint_done_notes else R.string.hint_done_plain, name, house, p.digit)
                }
                val legend = buildList {
                    add(LegendItem(LegendKind.PLACED, res.getString(R.string.hint_legend_placed), p.digit))
                    if (h.notesRemoved > 0) {
                        add(LegendItem(LegendKind.STRUCK, res.getQuantityString(R.plurals.hint_notes_removed, h.notesRemoved, h.notesRemoved), p.digit))
                    }
                }
                HintText(
                    status = res.getString(R.string.hint_status_done), eyebrow = null, title = title, chip = res.getString(R.string.hint_chip_placed),
                    stepLabel = null, body = body, legend = legend,
                    meta = res.getString(R.string.hint_meta_placed, hints),
                    secondary = res.getString(R.string.hint_undo), primary = res.getString(R.string.hint_done),
                    announcement = announce(res, res.getString(R.string.hint_stage_do), title, body),
                )
            }
        }
    }

    private fun wrong(res: Resources, h: HintSession, plan: HintPlan.WrongDigit, hints: Int): HintText {
        val cell = cellName(res, plan.cell)
        val (title, body) = when (h.stage) {
            1 -> res.getString(R.string.hint_wrong_title) to res.getString(R.string.hint_wrong_where)
            2 -> res.getString(R.string.hint_wrong_cell_title, plan.digit) to res.getString(R.string.hint_wrong_cell_body, plan.digit, cell)
            else -> res.getString(R.string.hint_wrong_removed_title, plan.digit, cell) to res.getString(R.string.hint_wrong_removed_body)
        }
        val stageName = res.getString(listOf(R.string.hint_stage_where, R.string.hint_stage_why, R.string.hint_stage_do)[h.stage - 1])
        return HintText(
            status = res.getString(listOf(R.string.hint_status_where, R.string.hint_status_why, R.string.hint_status_done)[h.stage - 1]),
            eyebrow = null,
            title = title,
            chip = if (h.stage == 3) res.getString(R.string.hint_chip_removed) else null,
            stepLabel = null,
            body = body,
            legend = when (h.stage) {
                1 -> listOf(LegendItem(LegendKind.HOUSE, res.getString(R.string.hint_wrong_legend_box)))
                2 -> listOf(LegendItem(LegendKind.TARGET, res.getString(R.string.hint_wrong_legend_cell)))
                else -> emptyList()
            },
            meta = when (h.stage) {
                3 -> res.getString(R.string.hint_meta_plain, hints)
                else -> res.getString(R.string.hint_wrong_meta)
            },
            secondary = res.getString(if (h.stage == 3) R.string.hint_undo else if (h.stage == 2) R.string.hint_got_it else R.string.hint_close),
            primary = res.getString(
                when (h.stage) {
                    1 -> R.string.hint_wrong_show
                    2 -> R.string.hint_wrong_remove
                    else -> R.string.hint_done
                },
            ),
            announcement = announce(res, stageName, title, body),
        )
    }

    /** Stage 2 of a one- or two-step hint: the technique's explanation, then what it unlocks. */
    fun explanation(res: Resources, plan: HintPlan.Logical): String {
        val first = plan.steps[0]
        if (first.technique.isSingle) return single(res, first)
        val single = plan.steps.last()
        return explain(res, first) + " " + unlocks(res, single, chain = false)
    }

    /** Stage 2 of a chain (D-031): each step on its own, like a single-step hint; the last one is the single. */
    fun chainStep(res: Resources, plan: HintPlan.Logical, k: Int): String {
        val step = plan.steps[k]
        return if (step.technique.isSingle) unlocks(res, step, chain = true) else explain(res, step)
    }

    /** One explanation per technique (US-012: every technique has stage-2 content). */
    fun explain(res: Resources, step: Step): String {
        val d = Masks.digits(step.digits)
        val house = houseName(res, step.house)
        return when (step.technique) {
            Technique.FULL_HOUSE, Technique.HIDDEN_SINGLE, Technique.NAKED_SINGLE -> single(res, step)
            Technique.POINTING -> res.getString(R.string.tech_expl_pointing, house, d[0], houseName(res, step.houses[1]))
            Technique.BOX_LINE -> res.getString(R.string.tech_expl_box_line, house, d[0], houseName(res, step.houses[1]))
            Technique.NAKED_PAIR -> res.getString(R.string.tech_expl_naked_pair, house, d[0], d[1])
            Technique.HIDDEN_PAIR -> res.getString(R.string.tech_expl_hidden_pair, house, d[0], d[1])
            Technique.NAKED_TRIPLE -> res.getString(R.string.tech_expl_naked_triple, house, d[0], d[1], d[2])
            Technique.HIDDEN_TRIPLE -> res.getString(R.string.tech_expl_hidden_triple, house, d[0], d[1], d[2])
            Technique.X_WING -> {
                val base = housesName(res, step.houses.copyOfRange(0, 2))
                val cover = housesName(res, step.houses.copyOfRange(2, 4))
                val id = if (Houses.isRow(step.houses[0])) R.string.tech_expl_x_wing_rows else R.string.tech_expl_x_wing_cols
                res.getString(id, base, d[0], cover)
            }
        }
    }

    private fun single(res: Resources, step: Step): String {
        val p = requireNotNull(step.placement)
        return when (step.technique) {
            Technique.FULL_HOUSE -> res.getString(R.string.tech_expl_full_house, houseName(res, step.house), p.digit)
            Technique.HIDDEN_SINGLE -> res.getString(R.string.tech_expl_hidden_single, houseName(res, step.house), p.digit)
            else -> res.getString(R.string.tech_expl_naked_single, p.digit)
        }
    }

    /** What an elimination unlocks: the single at the end of the hint. */
    private fun unlocks(res: Resources, single: Step, chain: Boolean): String {
        val p = requireNotNull(single.placement)
        return when (single.technique) {
            Technique.HIDDEN_SINGLE ->
                res.getString(if (chain) R.string.tech_chain_hidden else R.string.tech_then_hidden, p.digit, houseName(res, single.house))
            Technique.NAKED_SINGLE -> res.getString(if (chain) R.string.tech_chain_naked else R.string.tech_then_naked, p.digit)
            else -> single(res, single) // a full house never needs an elimination first, but stay truthful if it does
        }
    }

    fun techniqueName(res: Resources, t: Technique): String = res.getString(
        when (t) {
            Technique.FULL_HOUSE -> R.string.tech_full_house
            Technique.HIDDEN_SINGLE -> R.string.tech_hidden_single
            Technique.NAKED_SINGLE -> R.string.tech_naked_single
            Technique.POINTING -> R.string.tech_pointing
            Technique.BOX_LINE -> R.string.tech_box_line
            Technique.NAKED_PAIR -> R.string.tech_naked_pair
            Technique.HIDDEN_PAIR -> R.string.tech_hidden_pair
            Technique.NAKED_TRIPLE -> R.string.tech_naked_triple
            Technique.HIDDEN_TRIPLE -> R.string.tech_hidden_triple
            Technique.X_WING -> R.string.tech_x_wing
        },
    )

    private fun chip(res: Resources, t: Technique): String =
        res.getString(listOf(R.string.hint_chip_easy, R.string.hint_chip_medium, R.string.hint_chip_hard, R.string.hint_chip_expert)[t.tier - 1])

    fun houseName(res: Resources, house: Int): String = when {
        Houses.isRow(house) -> res.getString(R.string.hint_house_row, house - Houses.ROW0 + 1)
        Houses.isCol(house) -> res.getString(R.string.hint_house_col, house - Houses.COL0 + 1)
        else -> res.getString(R.string.hint_house_box, house - Houses.BOX0 + 1)
    }

    /** One house, or the two base (or cover) lines of an X-Wing: "rows 1 and 9". */
    private fun housesName(res: Resources, houses: IntArray): String = when {
        houses.size == 1 -> houseName(res, houses[0])
        Houses.isRow(houses[0]) -> res.getString(R.string.hint_rows, houses[0] - Houses.ROW0 + 1, houses[1] - Houses.ROW0 + 1)
        else -> res.getString(R.string.hint_cols, houses[0] - Houses.COL0 + 1, houses[1] - Houses.COL0 + 1)
    }

    private fun kindName(res: Resources, houses: IntArray): String = res.getString(
        when {
            houses.size > 1 && Houses.isRow(houses[0]) -> R.string.hint_kind_rows
            houses.size > 1 -> R.string.hint_kind_cols
            Houses.isRow(houses[0]) -> R.string.hint_kind_row
            Houses.isCol(houses[0]) -> R.string.hint_kind_col
            else -> R.string.hint_kind_box
        },
    )

    fun cellName(res: Resources, cell: Int): String = res.getString(R.string.hint_cell, Houses.row(cell) + 1, Houses.col(cell) + 1)

    private fun announce(res: Resources, stage: String, title: String, body: String) = res.getString(R.string.hint_announce, stage, title, body)
}
