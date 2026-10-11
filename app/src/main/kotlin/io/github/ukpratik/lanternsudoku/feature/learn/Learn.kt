// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.learn

import android.content.res.Resources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.CellCode
import io.github.ukpratik.lanternsudoku.engine.game.GameReducer
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.Highlights
import io.github.ukpratik.lanternsudoku.engine.game.Rules
import io.github.ukpratik.lanternsudoku.engine.hint.HintView
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.technique.Technique
import io.github.ukpratik.lanternsudoku.feature.game.HintCopy
import io.github.ukpratik.lanternsudoku.feature.game.HintText
import io.github.ukpratik.lanternsudoku.feature.game.Legend
import io.github.ukpratik.lanternsudoku.ui.board.Board
import io.github.ukpratik.lanternsudoku.ui.board.BoardModel
import io.github.ukpratik.lanternsudoku.ui.board.CellLabels
import io.github.ukpratik.lanternsudoku.ui.components.label
import io.github.ukpratik.lanternsudoku.ui.theme.LocalLanternColors

/**
 * One static example per technique (US-026): a real bank position (bank/v1) whose next hint starts with that technique,
 * found by replaying hints over the bank (the same positions `HintCopyTest` checks). [board] is the givens plus the
 * entries at that moment.
 */
data class LearnExample(val technique: Technique, val id: String, val givens: String, val board: String) {
    /** The position with the hint open at stage 2 ("Why"), exactly as the game shows it. */
    fun stage2(): GameState {
        val g = Grid.parse(givens)
        val b = Grid.parse(board)
        val fresh = GameState.new(id, g)
        val cells = fresh.codes()
        for (i in 0 until 81) if (g[i] == 0 && b[i] != 0) cells[i] = CellCode.of(value = b[i], seq = 1)
        val s = fresh.copy(cells = cells, seq = 1)
        val opened = GameReducer.reduce(s, Action.OpenHint, Rules()).state
        return GameReducer.reduce(opened, Action.HintNext, Rules()).state
    }

    fun text(res: Resources): HintText = requireNotNull(HintCopy.of(res, stage2()))

    companion object {
        /** The ladder in its search order (Technique enum order), one example each. */
        val ALL = listOf(
            LearnExample(Technique.FULL_HOUSE, "N-E-0000", ".47...9..91..7.2..6.8..3..4.897....617.624.894....957.7..5..1.2..4.6..97..1...65.",
                ".47...9..91.47.2..6.8..37.4.897..42617.624.894....95717..5..1.2..4.6..97..1...65."),
            LearnExample(Technique.HIDDEN_SINGLE, "N-E-0000", ".47...9..91..7.2..6.8..3..4.897....617.624.894....957.7..5..1.2..4.6..97..1...65.",
                ".47...9..91..7.2..6.8..3..4.897....617.624.894....957.7..5..1.2..4.6..97..1...65."),
            LearnExample(Technique.NAKED_SINGLE, "N-E-0313", "..824.......3.5.8....96.2.4.635.189.7..682..1.214.957.3.2.56....5.8.4.......231..",
                "..8247...2..315.8....9682.4463571892795682..18214395763.2156...15.894.2....7231.."),
            LearnExample(Technique.POINTING, "N-M-0001", ".5.91.....1..73.6.7...2...94.1..8...67.2.5.41...1..8.63...6...5.9.35..7.....89.2.",
                ".5.91.....1..73.6.7...2...94.16.8...6782.5.41...1478.63...6...5.9.35..7.....89.2."),
            LearnExample(Technique.BOX_LINE, "N-M-0014", ".46..1.8.87..3.....326..9......4.56.4..7.5..9.95.8......7..389.....9..56.5.8..27.",
                ".46..1.8.87..3.6...326.89..7...4.5684..7.5..9.95.8......7..389.....97.56.5.8..27."),
            LearnExample(Technique.NAKED_PAIR, "N-M-0007", ".95..6.4.1..5.9.363.2.1..5..2.......8.9...6.5.......8..5..8.1.391.7.3..2.3.1..49.",
                "795..6.411845.9.36362.1..5..23....1.8.9..1625..1...38.456982173918743562237165498"),
            LearnExample(Technique.HIDDEN_PAIR, "N-H-0003", "...384.....3..56.2..7.2..9.1..59...3.........2...43..1.8..1.5..3.64..2.....836...",
                "6..384.....39756.2..762139.1..59...3.3.16....2..743..1.8.21.5363.645.2.....836..."),
            LearnExample(Technique.NAKED_TRIPLE, "N-H-0013", "8...5.3....3..2...6.5...4.157...9.....1...7.....4...653.8...2.4...1..5....4.8...3",
                "8...5.3....3..2.5.6.5...4.1576..9.42481.2.739.3.4...653.8...2.4...1..5....428...3"),
            LearnExample(Technique.HIDDEN_TRIPLE, "N-H-0050", "...9...2.....8.6.9....3.7..3.7....485..4.1..349....5.7..3.6....1.8.5.....7...2...",
                "...9...2.....8.6.9....3.7..3.7.9..4858.471..349..2.5.7..3.6....1.8.5.....7...2..."),
            LearnExample(Technique.X_WING, "N-X-0000", "..825.3...2.6...54.........2.....8.9...9.6...6.7.....5.........85...2.7...3.481..",
                "..825.3...2.68..54.........2....5869.8.9.6...6978....5..2...5..85...2.7...35481.."),
        )

        fun of(t: Technique): LearnExample = ALL.first { it.technique == t }
    }
}

@Composable
private fun LearnScaffold(title: String, onBack: () -> Unit, tag: String, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState()).semantics { testTag = tag }) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).padding(start = 4.dp, end = 12.dp).semantics { heading() })
                }
                content()
            }
        }
    }
}

/** Settings › Learn techniques (US-026, a Could): the ladder, Easy to Expert, each opening its page. */
@Composable
fun LearnScreen(onBack: () -> Unit, onOpen: (Technique) -> Unit) {
    val res = LocalResources.current
    val scheme = MaterialTheme.colorScheme
    LearnScaffold(stringResource(R.string.learn_title), onBack, "learn") {
        Text(stringResource(R.string.learn_intro), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Spacer(Modifier.height(8.dp))
        for (ex in LearnExample.ALL) {
            val t = ex.technique
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { onOpen(t) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(HintCopy.techniqueName(res, t), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(t.level.label), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp).size(24.dp))
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = scheme.outlineVariant)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * One technique (US-026): its name and level, the hint's stage-2 explanation on a real position, and that position as
 * a static board with the hint's roles drawn (house, cause, struck candidates, target), plus the role legend.
 */
@Composable
fun LearnPage(technique: Technique, onBack: () -> Unit) {
    val res = LocalResources.current
    val ex = LearnExample.of(technique)
    val state = remember(ex) { ex.stage2() }
    val text = remember(ex, res) { ex.text(res) }
    val k = LocalLanternColors.current
    LearnScaffold(HintCopy.techniqueName(res, technique), onBack, "learn-page") {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(technique.level.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp))
            Text(text.body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp).semantics { testTag = "learn-body" })
            Text(stringResource(R.string.learn_example), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() })
            Spacer(Modifier.height(8.dp))
            val hint = HintView.of(state)
            val described = stringResource(R.string.learn_example_description, HintCopy.techniqueName(res, technique), text.body)
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val size = minOf(maxWidth, 340.dp)
                        // A picture, not a game: TalkBack reads one description instead of 81 cells.
                        Box(Modifier.clearAndSetSemantics { contentDescription = described }) {
                            Board(
                                model = BoardModel(
                                    values = IntArray(81) { state.value(it) },
                                    givens = BooleanArray(81) { state.isGiven(it) },
                                    notes = IntArray(81) { state.notes(it) },
                                    highlights = Highlights.of(state, showMistakes = false),
                                    hint = hint,
                                ),
                                size = size,
                                colors = k,
                                labels = CellLabels({ "" }, { null }),
                                onTapCell = {},
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth()) { Legend(text.legend) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
