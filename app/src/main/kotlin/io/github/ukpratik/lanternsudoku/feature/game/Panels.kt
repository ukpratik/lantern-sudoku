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
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier.semantics { paneTitle = title },
        color = scheme.surfaceContainer, contentColor = scheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            val scroll = rememberScrollState()
            Column(Modifier.weight(1f, fill = false).scrollCue(scroll).verticalScroll(scroll).semantics { testTag = "panel-text" }) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = null, tint = scheme.primary)
                }
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.focusOnAppear().semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                })
                if (body != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(body, style = MaterialTheme.typography.bodyLarge)
                }
                if (finePrint != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(finePrint, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(primary.first, primary.second, Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { testTag = "panel-primary" })
                SecondaryButton(secondary.first, secondary.second, Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { testTag = "panel-secondary" })
            }
        }
    }
}

/**
 * Shows that a panel's text goes on below (ISS-001): a fade over the last lines while there's more to scroll and a thin
 * scroll thumb. Apply before `verticalScroll` with the same [state], so it draws over the visible window.
 */
@Composable
fun Modifier.scrollCue(state: ScrollState): Modifier {
    val fade = MaterialTheme.colorScheme.surfaceContainer
    val thumb = MaterialTheme.colorScheme.onSurfaceVariant
    return drawWithContent {
        drawContent()
        if (state.maxValue == 0) return@drawWithContent
        val edge = minOf(28.dp.toPx(), size.height / 3)
        if (state.canScrollForward) {
            drawRect(Brush.verticalGradient(listOf(fade.copy(alpha = 0f), fade), startY = size.height - edge, endY = size.height),
                topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge))
        }
        val content = size.height + state.maxValue
        val thumbH = maxOf(size.height * size.height / content, 16.dp.toPx())
        val top = (size.height - thumbH) * state.value / state.maxValue
        val w = 3.dp.toPx()
        drawRoundRect(thumb.copy(alpha = 0.6f), topLeft = Offset(size.width - w, top), size = Size(w, thumbH), cornerRadius = CornerRadius(w / 2))
    }
}
