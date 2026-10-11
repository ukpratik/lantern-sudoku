// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import io.github.ukpratik.lanternsudoku.ui.components.focusOnAppear
import androidx.compose.ui.unit.dp
import io.github.ukpratik.lanternsudoku.ui.components.PrimaryButton
import io.github.ukpratik.lanternsudoku.ui.components.SecondaryButton

/**
 * Almost!, Limit and completion panels (design §5): an in-place swap in the pad slot, no drag handle, no swipe, no
 * scrim; the board stays visible. Stacked full-width buttons, primary first. The panel takes the height its content
 * needs (the board gives way down to its 30 dp cell floor); text that still doesn't fit scrolls above the buttons,
 * which are always on screen (ISS-002).
 */
@Composable
fun GamePanel(
    @DrawableRes icon: Int,
    title: String,
    body: String?,
    finePrint: String?,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier.semantics { paneTitle = title },
        color = scheme.surfaceContainer, contentColor = scheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        // The end padding is 20 dp in all: [SCROLL_GUTTER] of it belongs to the text area, where the scroll thumb runs (ISS-007).
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp - SCROLL_GUTTER, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            val scroll = rememberScrollState()
            Column(Modifier.weight(1f, fill = false).scrollCue(scroll).verticalScroll(scroll).padding(end = SCROLL_GUTTER)
                .semantics { testTag = "panel-text" }) {
                val disc: @Composable () -> Unit = {
                    Box(Modifier.size(if (compact) 40.dp else 48.dp).clip(CircleShape).background(scheme.secondaryContainer),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), contentDescription = null, tint = scheme.primary)
                    }
                }
                val heading: @Composable (Modifier) -> Unit = { m ->
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = m.focusOnAppear().semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    })
                }
                if (compact) {
                    // Short screens: the icon disc sits beside the title, leaving its 60 dp to the text (ISS-002, D-037).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        disc()
                        Spacer(Modifier.width(12.dp))
                        heading(Modifier.weight(1f))
                    }
                } else {
                    disc()
                    Spacer(Modifier.height(12.dp))
                    heading(Modifier)
                }
                if (body != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(body, style = MaterialTheme.typography.bodyLarge)
                }
                if (finePrint != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(finePrint, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            Column(Modifier.padding(top = 12.dp, end = SCROLL_GUTTER), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(primary.first, primary.second, Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { testTag = "panel-primary" })
                SecondaryButton(secondary.first, secondary.second, Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { testTag = "panel-secondary" })
            }
        }
    }
}

/**
 * The strip at the end of a panel's scrolling text that only the scroll thumb uses (ISS-007): the text column is padded
 * by it inside the scroll and the thumb is drawn in its middle, so no line runs under the thumb. It is taken out of the
 * panel's 20 dp end padding, so the text keeps its width.
 */
val SCROLL_GUTTER = 12.dp

/** The scroll thumb's width; it is centred in [SCROLL_GUTTER]. */
val SCROLL_THUMB_WIDTH = 3.dp

/**
 * Shows that a panel's text goes on below (ISS-001): a fade over the last lines while there's more to scroll and a thin
 * scroll thumb in the [SCROLL_GUTTER]. Apply before `verticalScroll` with the same [state], so it draws over the visible
 * window, and pad the scrolled content's end by [SCROLL_GUTTER].
 */
@Composable
fun Modifier.scrollCue(state: ScrollState): Modifier {
    val fade = MaterialTheme.colorScheme.surfaceContainer
    val thumb = MaterialTheme.colorScheme.onSurfaceVariant
    return drawWithContent {
        drawContent()
        if (state.maxValue == 0) return@drawWithContent
        val edge = minOf(16.dp.toPx(), size.height / 4)
        if (state.canScrollForward) {
            drawRect(Brush.verticalGradient(listOf(fade.copy(alpha = 0f), fade), startY = size.height - edge, endY = size.height),
                topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge))
        }
        val content = size.height + state.maxValue
        val thumbH = maxOf(size.height * size.height / content, 16.dp.toPx())
        val top = (size.height - thumbH) * state.value / state.maxValue
        val w = SCROLL_THUMB_WIDTH.toPx()
        val left = size.width - (SCROLL_GUTTER.toPx() + w) / 2
        drawRoundRect(thumb.copy(alpha = 0.6f), topLeft = Offset(left, top), size = Size(w, thumbH), cornerRadius = CornerRadius(w / 2))
    }
}
