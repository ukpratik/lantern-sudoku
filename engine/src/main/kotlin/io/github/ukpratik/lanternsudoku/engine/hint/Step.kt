// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.hint

import io.github.ukpratik.lanternsudoku.engine.model.Elim
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.engine.model.Placement
import io.github.ukpratik.lanternsudoku.engine.technique.Technique

/**
 * One logical step (LLD §4.2 step model, US-012). The UI maps it to the three design roles:
 * [placement] → hint target, [causeCells] → hint cause, [eliminations] → eliminated candidates.
 * `:engine` never holds user-facing text; [describe] is for logs and the hand-check report only.
 *
 * @property house primary house for hint stage 1 ("Where").
 * @property houses every house involved (pointing: box + line; X-Wing: 2 base + 2 cover), in that order.
 * @property digits mask of the digits the step is about.
 * @property placement set for singles only.
 */
class Step(
    val technique: Technique,
    val house: Int,
    val houses: IntArray,
    val digits: Int,
    val causeCells: IntArray,
    val eliminations: List<Elim>,
    val placement: Placement?,
) {
    init {
        require(technique.isSingle == (placement != null)) { "Singles place a digit; other techniques only eliminate" }
        require(technique.isSingle || eliminations.isNotEmpty()) { "An elimination step must eliminate something" }
    }

    fun describe(): String = buildString {
        append(technique.label)
        append(" in ").append(houses.joinToString(" + ") { Houses.name(it) })
        if (!technique.isSingle) append(" on {").append(Masks.format(digits)).append('}')
        if (causeCells.isNotEmpty() && !technique.isSingle) append(" cells ").append(causeCells.joinToString(",") { Houses.cellName(it) })
        placement?.let { append(" → ").append(Houses.cellName(it.cell)).append(" = ").append(it.digit) }
        if (eliminations.isNotEmpty()) {
            append(" → remove ")
            append(eliminations.joinToString(", ") { "${it.digit} from ${Houses.cellName(it.cell)}" })
        }
    }

    override fun equals(other: Any?): Boolean = other is Step &&
        technique == other.technique && house == other.house && houses.contentEquals(other.houses) && digits == other.digits &&
        causeCells.contentEquals(other.causeCells) && eliminations == other.eliminations && placement == other.placement

    override fun hashCode(): Int = ((technique.hashCode() * 31 + house) * 31 + digits) * 31 + eliminations.hashCode()

    override fun toString(): String = describe()
}
