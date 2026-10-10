// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.ui.theme.DisplayFamily

object TileDefaults {
    /** Inner side padding, also used to work out how many tiles fit a row (Stats, ISS-004). */
    val PADDING = 12.dp
}

/** A stat tile (design §6 "surface card"): a big Fraunces value, its label and an optional second line. */
@Composable
fun Tile(value: String, label: String, modifier: Modifier = Modifier, sub: String? = null, icon: (@Composable () -> Unit)? = null) {
    val description = stringResource(R.string.stat_tile_description, value, label)
    Surface(
        modifier.spokenAs(if (sub == null) description else "$description, $sub"),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = TileDefaults.PADDING, vertical = 10.dp)) {
            if (icon != null) icon() else Text(value, fontFamily = DisplayFamily, fontSize = 24.sp, maxLines = 1, modifier = quiet)
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = quiet)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = quiet)
        }
    }
}

