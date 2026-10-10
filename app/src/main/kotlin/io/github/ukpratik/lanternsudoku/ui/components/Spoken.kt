// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics

/**
 * One TalkBack stop that reads [description] (a stat tile, a table row). Unlike `clearAndSetSemantics`, the texts inside
 * stay in the semantics tree, marked [quiet], so layout tests can check that they fit at 200% text (ISS-004).
 */
fun Modifier.spokenAs(description: String): Modifier = semantics(mergeDescendants = true) { contentDescription = description }

/** A visible text that TalkBack skips because its parent's [spokenAs] description already says it. */
val quiet: Modifier = Modifier.semantics { hideFromAccessibility() }
