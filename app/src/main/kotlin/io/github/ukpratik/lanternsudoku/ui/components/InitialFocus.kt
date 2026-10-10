// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Moves focus to this element (a panel or sheet title) when it appears, so TalkBack starts reading there instead of
 * staying on the control that opened it (US-022, BL-015; design hint-stage-1 "Focus starts on the title"). Titles have
 * no focus indication, so sighted players see nothing.
 */
@Composable
fun Modifier.focusOnAppear(): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) { requester.requestFocus() }
    return this.focusRequester(requester).focusable()
}
