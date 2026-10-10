// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.hint.HintPlan
import io.github.ukpratik.lanternsudoku.ui.components.PrimaryButton
import io.github.ukpratik.lanternsudoku.ui.components.SecondaryButton
import io.github.ukpratik.lanternsudoku.ui.components.focusOnAppear
import io.github.ukpratik.lanternsudoku.feature.game.GameChrome.Companion.LARGE_TEXT
import io.github.ukpratik.lanternsudoku.ui.theme.LocalLanternColors

/**
 * The hint panel (US-012; design hint-stage-1/2/3, hint-error-first, hint-fallback): an in-place swap in the pad slot,
 * no drag handle and no swipe; the board stays where it is. Stepper · title (+ chip) · explanation · role legend ·
 * meta line · two buttons (secondary left, primary right). The text cross-fades on stage changes (150 ms).
 */
@Composable
fun HintPanel(game: GameState, onAction: (Action) -> Unit, modifier: Modifier = Modifier) {
    val h = game.input.hint ?: return
    val text = HintCopy.of(LocalResources.current, game) ?: return
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxSize().semantics {
            paneTitle = text.status
            testTag = "hint-panel"
        },
        color = scheme.surfaceContainer, contentColor = scheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 6.dp)) {
            if (h.plan is HintPlan.Fallback) {
                Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_lightbulb), contentDescription = null, tint = LocalLanternColors.current.hintIcon,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(text.eyebrow.orEmpty(), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            } else {
                Stepper(stage = h.stage, onStage = { onAction(Action.HintStage(it)) })
            }
            AnimatedContent(
                targetState = Triple(h.stage, h.step, text),
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                modifier = Modifier.weight(1f),
                label = "hint-stage",
            ) { (_, step, t) ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.Center) {
                        Text(
                            t.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                            modifier = Modifier.align(Alignment.CenterVertically).focusOnAppear().semantics {
                                heading()
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = t.announcement
                            },
                        )
                        if (t.chip != null) {
                            Text(
                                t.chip,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSecondaryContainer,
                                modifier = Modifier.align(Alignment.CenterVertically).clip(RoundedCornerShape(8.dp))
                                    .background(scheme.secondaryContainer).padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (t.stepLabel != null) ChainBar(t.stepLabel, step, h.stepCount, onAction)
                    Text(
                        t.body,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp),
                        modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics { }, // read as part of the announcement
                    )
                    if (t.legend.isNotEmpty()) Legend(t.legend)
                    Text(t.meta, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
            val secondaryAction = if (h.stage == 3) Action.Undo else Action.CloseHint
            val icon = if (h.stage == 2 && h.plan is HintPlan.Logical) R.drawable.ic_lightbulb else null
            if (LocalDensity.current.fontScale >= LARGE_TEXT) {
                // US-021: with large text the buttons stack full width (primary first) and grow with their labels.
                Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(text.primary, { onAction(Action.HintNext) },
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { testTag = "hint-primary" }, icon = icon)
                    SecondaryButton(text.secondary, { onAction(secondaryAction) },
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { testTag = "hint-secondary" })
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(text.secondary, { onAction(secondaryAction) }, Modifier.weight(1f).height(52.dp).semantics { testTag = "hint-secondary" })
                    PrimaryButton(text.primary, { onAction(Action.HintNext) }, Modifier.weight(1f).height(52.dp).semantics { testTag = "hint-primary" }, icon = icon)
                }
            }
        }
    }
}

/** Where · Why · Do it: done = ink disc with a tick, current = amber disc with a halo, next = outline (design §5). */
@Composable
private fun Stepper(stage: Int, onStage: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val names = listOf(stringResource(R.string.hint_stage_where), stringResource(R.string.hint_stage_why), stringResource(R.string.hint_stage_do))
    val description = stringResource(R.string.hint_stepper_cd)
    // The stage numbers sit in fixed 24 dp discs, so they're sized in dp; the stage names are read by TalkBack and
    // the explanation below follows the font scale (US-021).
    val discText = with(LocalDensity.current) { 12.dp.toSp() }
    val discStyle = MaterialTheme.typography.labelMedium.copy(fontSize = discText, lineHeight = discText)
    Row(Modifier.fillMaxWidth().semantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        for (n in 1..3) {
            val done = n < stage || stage == 3
            val current = n == stage && stage < 3
            val cd = when {
                done -> stringResource(R.string.hint_stage_done_cd, names[n - 1])
                current -> stringResource(R.string.hint_stage_current_cd, names[n - 1])
                else -> stringResource(R.string.hint_stage_upcoming_cd, names[n - 1])
            }
            // Earlier stages are 44 dp targets to go back, until the digit is placed.
            val tappable = stage < 3 && n < stage
            Row(
                Modifier.heightIn(min = 44.dp)
                    .then(if (tappable) Modifier.clickable(role = Role.Button) { onStage(n) } else Modifier)
                    .clearAndSetSemantics {
                        contentDescription = cd
                        if (tappable) role = Role.Button
                    }
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val discMod = Modifier.size(24.dp).clip(CircleShape)
                when {
                    done -> Box(discMod.background(scheme.onSurface), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = scheme.surface, modifier = Modifier.size(16.dp))
                    }
                    current -> Box(
                        Modifier.size(32.dp).clip(CircleShape).background(LocalLanternColors.current.glow),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(discMod.background(scheme.primaryContainer).border(1.5.dp, scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Text("$n", style = discStyle, color = scheme.onPrimaryContainer)
                        }
                    }
                    else -> Box(discMod.border(1.5.dp, scheme.outline, CircleShape), contentAlignment = Alignment.Center) {
                        Text("$n", style = discStyle.copy(letterSpacing = 0.sp), color = scheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(names[n - 1], style = MaterialTheme.typography.labelMedium, color = if (done || current) scheme.onSurface else scheme.onSurfaceVariant)
            }
            if (n < 3) {
                Box(Modifier.weight(1f).padding(horizontal = 6.dp).height(2.dp).background(if (n < stage || stage == 3) scheme.onSurface else scheme.outlineVariant))
            }
        }
    }
}

/** "Step k of N" with Back and Next for a multi-step hint (D-031). */
@Composable
private fun ChainBar(label: String, step: Int, count: Int, onAction: (Action) -> Unit) {
    val back = stringResource(R.string.hint_step_back_cd)
    val next = stringResource(R.string.hint_step_next_cd)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
        TextButton(onClick = { onAction(Action.HintStep(step - 1)) }, enabled = step > 0,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = back; testTag = "hint-step-back" }) {
            Text(stringResource(R.string.hint_step_back))
        }
        TextButton(onClick = { onAction(Action.HintStep(step + 1)) }, enabled = step < count - 1,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = next; testTag = "hint-step-next" }) {
            Text(stringResource(R.string.hint_step_next))
        }
    }
}

/** Role legend (design .roles): each swatch repeats the board's shape for that role, so colour is never the only cue. */
@Composable
private fun Legend(items: List<LegendItem>) {
    val k = LocalLanternColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (item in items) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (item.kind) {
                    LegendKind.HOUSE -> Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(k.hintHouse)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)))
                    LegendKind.TARGET -> Box(Modifier.size(18.dp).border(k.ringStroke, k.hintTargetRing, RoundedCornerShape(5.dp)))
                    LegendKind.CAUSE -> Canvas(Modifier.size(18.dp).clipToBounds()) {
                        drawRect(k.hintCauseA)
                        var x = -size.width
                        while (x < size.width) {
                            drawLine(k.hintCauseB, Offset(x, 0f), Offset(x + size.width, size.height), 1.5.dp.toPx())
                            x += 4.dp.toPx()
                        }
                        drawRoundRect(k.hintCauseLine, cornerRadius = CornerRadius(4.dp.toPx()),
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
                    }
                    LegendKind.STRUCK -> Text("${item.digit}", color = k.danger, fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodyMedium)
                    LegendKind.PLACED -> Box(Modifier.size(18.dp).border(k.ringStroke, k.hintTargetRing, RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center) {
                        Text("${item.digit}", style = MaterialTheme.typography.labelSmall, color = k.entry)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(item.text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = muted)
            }
        }
    }
}
