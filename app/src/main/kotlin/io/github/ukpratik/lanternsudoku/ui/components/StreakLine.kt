// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.data.DailyStats

/**
 * "4-day streak · best 9 · 37 days played" (US-015). A lapsed streak simply reads "0-day streak · best 9": never
 * "broken" (design §9). TalkBack: "4-day streak. Best 9. 37 days played."
 */
@Composable
fun StreakLine(stats: DailyStats, modifier: Modifier = Modifier) {
    val res = LocalResources.current
    val played = res.getQuantityString(R.plurals.days_played, stats.daysPlayed, stats.daysPlayed)
    val line = res.getString(R.string.streak_line, stats.current, stats.best, played)
    val description = res.getString(R.string.streak_description, stats.current, stats.best, played)
    val head = line.substringBefore(" · ")
    Row(modifier.clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_lantern), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)) { append(head) }
                append(line.removePrefix(head))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
