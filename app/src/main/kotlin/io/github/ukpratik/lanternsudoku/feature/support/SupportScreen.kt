// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.support

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.platform.Tip
import io.github.ukpratik.lanternsudoku.platform.TipCatalog
import io.github.ukpratik.lanternsudoku.platform.TipJar
import io.github.ukpratik.lanternsudoku.platform.TipJarState
import io.github.ukpratik.lanternsudoku.platform.TipOutcome
import io.github.ukpratik.lanternsudoku.ui.components.PrimaryButton
import io.github.ukpratik.lanternsudoku.ui.components.SecondaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SupportViewModel(private val jar: TipJar) : ViewModel() {
    val state: StateFlow<TipJarState> = jar.state

    private val _outcome = MutableStateFlow<TipOutcome?>(null)

    /** The last finished tip, shown as a message until the next tip starts. Cancelling shows nothing. */
    val outcome: StateFlow<TipOutcome?> = _outcome.asStateFlow()

    init {
        jar.refresh()
        viewModelScope.launch { jar.outcomes.collect { _outcome.value = it.takeIf { o -> o != TipOutcome.CANCELLED } } }
    }

    fun retry() = jar.refresh()

    fun give(activity: Activity, tipId: String) {
        _outcome.value = null
        jar.buy(activity, tipId)
    }
}

/**
 * Settings › Support Lantern. play: three one-time tips through Google Play; fdroid: a link to GitHub Sponsors.
 * Tips unlock nothing (every feature stays free); the screen says so before anyone pays.
 */
@Composable
fun SupportScreen(vm: SupportViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val outcome by vm.outcome.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = scheme.background) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.support_title), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 4.dp).semantics { heading() })
                }
                Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = CircleShape, color = scheme.primaryContainer, modifier = Modifier.size(72.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_favorite), contentDescription = null, tint = scheme.primary,
                                modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.support_heading), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.support_body), style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))

                    outcome?.let { OutcomeMessage(it) }

                    when (val s = state) {
                        TipJarState.Loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                        is TipJarState.Ready -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                for (tip in s.tips) TipCard(tip) { context.findActivity()?.let { vm.give(it, tip.id) } }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.support_play_note), style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                        TipJarState.Unavailable -> {
                            Text(stringResource(R.string.support_unavailable), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            SecondaryButton(stringResource(R.string.support_try_again), onClick = vm::retry)
                        }
                        is TipJarState.External -> {
                            Text(stringResource(R.string.support_external), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(stringResource(R.string.support_open_sponsors), icon = R.drawable.ic_favorite, onClick = {
                                // ACTION_VIEW hands the link to the browser; the app itself never touches the network (NFR-004).
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, s.url.toUri())) }
                            })
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun TipCard(tip: Tip, onGive: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(tipTitle(tip.id)), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(tipSubtitle(tip.id)), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            PrimaryButton(tip.price, onClick = onGive, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun OutcomeMessage(outcome: TipOutcome) {
    val text = when (outcome) {
        TipOutcome.THANKS -> R.string.support_thanks
        TipOutcome.PENDING -> R.string.support_pending
        TipOutcome.FAILED -> R.string.support_failed
        TipOutcome.CANCELLED -> return
    }
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (outcome == TipOutcome.FAILED) scheme.errorContainer else scheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = if (outcome == TipOutcome.FAILED) scheme.onErrorContainer else scheme.onSecondaryContainer,
            modifier = Modifier.padding(16.dp))
    }
}

@StringRes
private fun tipTitle(id: String): Int = when (id) {
    TipCatalog.SMALL -> R.string.support_tip_small
    TipCatalog.MEDIUM -> R.string.support_tip_medium
    else -> R.string.support_tip_large
}

@StringRes
private fun tipSubtitle(id: String): Int = when (id) {
    TipCatalog.SMALL -> R.string.support_tip_small_summary
    TipCatalog.MEDIUM -> R.string.support_tip_medium_summary
    else -> R.string.support_tip_large_summary
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
