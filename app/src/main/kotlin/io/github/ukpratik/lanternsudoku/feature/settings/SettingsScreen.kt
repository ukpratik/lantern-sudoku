// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.settings

import android.content.Intent
import androidx.core.net.toUri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.data.Settings
import io.github.ukpratik.lanternsudoku.data.SettingsRepository
import io.github.ukpratik.lanternsudoku.data.ThemeMode
import io.github.ukpratik.lanternsudoku.engine.game.HapticKind
import io.github.ukpratik.lanternsudoku.feature.game.GameChrome.Companion.LARGE_TEXT
import io.github.ukpratik.lanternsudoku.platform.Haptics
import io.github.ukpratik.lanternsudoku.ui.components.quiet
import io.github.ukpratik.lanternsudoku.ui.components.spokenAs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<Settings> = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { repository.setTheme(mode) }
    }

    fun setHighContrast(on: Boolean) {
        viewModelScope.launch { repository.setHighContrast(on) }
    }

    fun setLargeDigits(on: Boolean) {
        viewModelScope.launch { repository.setLargeDigits(on) }
    }

    fun setShowMistakes(on: Boolean) {
        viewModelScope.launch { repository.setShowMistakes(on) }
    }

    fun setMistakeLimit(limit: Int) {
        viewModelScope.launch { repository.setMistakeLimit(limit) }
    }

    fun setTidyNotes(on: Boolean) {
        viewModelScope.launch { repository.setAutoCleanupNotes(on) }
    }

    fun setHighlightSame(on: Boolean) {
        viewModelScope.launch { repository.setHighlightSameDigit(on) }
    }

    fun setShowTimer(on: Boolean) {
        viewModelScope.launch { repository.setShowTimer(on) }
    }

    fun setHaptics(on: Boolean) {
        viewModelScope.launch { repository.setHaptics(on) }
    }
}

/**
 * Settings (design `settings`, US-023): Appearance (Theme, High contrast, Larger digits; US-020, US-021), Play (Show
 * mistakes, Mistake limit, Tidy notes, Highlight same digit, Show timer; US-010, US-023), Feel (Vibration) and About
 * (privacy line, licence and source, open-source licences, Learn techniques, version). Every change applies instantly
 * and persists. PRD v1.3: the limit is only available while Show mistakes is on. Sounds stays hidden (D-020, D-044).
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpenLicences: () -> Unit = {}, onOpenLearn: () -> Unit = {}) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val tick: () -> Unit = { if (s.haptics) Haptics.perform(view, HapticKind.TICK) }
    Surface(Modifier.fillMaxSize(), color = scheme.background) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 4.dp).semantics { heading() })
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(R.string.settings_appearance_header)
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    val modes = listOf(
                        ThemeMode.SYSTEM to R.string.settings_theme_system,
                        ThemeMode.LIGHT to R.string.settings_theme_light,
                        ThemeMode.DARK to R.string.settings_theme_dark,
                    )
                    val pick: (ThemeMode) -> Unit = { mode ->
                        tick()
                        vm.setTheme(mode)
                    }
                    if (LocalDensity.current.fontScale >= LARGE_TEXT) {
                        // US-021: three segments can't hold "System" at large text; the same choice as a radio list.
                        Column(Modifier.selectableGroup()) {
                            for ((mode, label) in modes) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .selectable(selected = s.theme == mode, role = Role.RadioButton, onClick = { pick(mode) }),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = s.theme == mode, onClick = null,
                                        colors = RadioButtonDefaults.colors(selectedColor = scheme.primary))
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    } else {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            modes.forEachIndexed { index, (mode, label) ->
                                SegmentedButton(
                                    selected = s.theme == mode,
                                    onClick = { pick(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                    colors = SegmentedButtonDefaults.colors(activeContainerColor = scheme.secondaryContainer),
                                ) { Text(stringResource(label), maxLines = 1) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(R.drawable.ic_contrast, R.string.settings_high_contrast, R.string.settings_high_contrast_summary, s.highContrast) {
                        tick()
                        vm.setHighContrast(it)
                    }
                    SwitchRow(R.drawable.ic_format_size, R.string.settings_larger_digits, R.string.settings_larger_digits_summary, s.largeDigits) {
                        tick()
                        vm.setLargeDigits(it)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = scheme.outlineVariant)

                    SectionHeader(R.string.settings_mistakes_header)
                    SwitchRow(R.drawable.ic_error, R.string.settings_show_mistakes, R.string.settings_show_mistakes_summary, s.showMistakes) {
                        tick()
                        vm.setShowMistakes(it)
                    }
                    val limitEnabled = s.showMistakes
                    Row(Modifier.padding(top = 12.dp).alpha(if (limitEnabled) 1f else 0.42f)) {
                        RowIcon(R.drawable.ic_flag)
                        Column {
                            Text(stringResource(R.string.settings_mistake_limit), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(if (limitEnabled) R.string.settings_mistake_limit_summary else R.string.settings_limit_needs_show),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                Settings.LIMIT_OPTIONS.forEachIndexed { index, option ->
                                    SegmentedButton(
                                        selected = s.mistakeLimit == option,
                                        onClick = { vm.setMistakeLimit(option) },
                                        enabled = limitEnabled,
                                        shape = SegmentedButtonDefaults.itemShape(index, Settings.LIMIT_OPTIONS.size),
                                        colors = SegmentedButtonDefaults.colors(activeContainerColor = scheme.secondaryContainer),
                                    ) {
                                        Text(if (option == 0) stringResource(R.string.settings_limit_off) else option.toString())
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SwitchRow(R.drawable.ic_auto_fix, R.string.settings_tidy_notes, R.string.settings_tidy_notes_summary, s.autoCleanupNotes) {
                        tick()
                        vm.setTidyNotes(it)
                    }
                    SwitchRow(R.drawable.ic_select_all, R.string.settings_highlight_same, null, s.highlightSameDigit) {
                        tick()
                        vm.setHighlightSame(it)
                    }
                    SwitchRow(R.drawable.ic_timer, R.string.settings_show_timer, null, s.showTimer) {
                        tick()
                        vm.setShowTimer(it)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = scheme.outlineVariant)

                    SectionHeader(R.string.settings_feel_header)
                    SwitchRow(R.drawable.ic_vibration, R.string.settings_vibration, null, s.haptics) {
                        // The tick confirms turning it on; turning it off is silent (design §8: gated by this setting).
                        if (it) Haptics.perform(view, HapticKind.TICK)
                        vm.setHaptics(it)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = scheme.outlineVariant)

                    SectionHeader(R.string.settings_about_header)
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(R.drawable.ic_privacy)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.settings_privacy_summary), style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant)
                        }
                    }
                    val context = LocalContext.current
                    val sourceUrl = stringResource(R.string.source_url)
                    val sourceDescription = stringResource(R.string.settings_source_description)
                    LinkRow(R.drawable.ic_code, R.string.settings_licence, R.string.settings_licence_summary, sourceDescription) {
                        // ACTION_VIEW hands the link to the browser; the app itself never touches the network (NFR-004).
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, sourceUrl.toUri())) }
                    }
                    LinkRow(R.drawable.ic_description, R.string.settings_open_source, R.string.settings_open_source_summary, null, onOpenLicences)
                    LinkRow(R.drawable.ic_school, R.string.settings_learn, R.string.settings_learn_summary, null, onOpenLearn)
                    Text(
                        stringResource(R.string.settings_version, appVersion()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 40.dp, top = 4.dp, bottom = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
}

@Composable
private fun SectionHeader(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 12.dp).semantics { heading() })
}

@Composable
private fun RowIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 16.dp).size(24.dp))
}

/** A row that opens something (a screen, or the browser): the whole row is one 64 dp target with a chevron. */
@Composable
private fun LinkRow(@DrawableRes icon: Int, title: Int, summary: Int, description: String?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.spokenAs(description) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        // With its own [description], the row is read once as that (the visible texts stay quiet for TalkBack).
        val textModifier = if (description != null) quiet else Modifier
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = textModifier)
            Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = textModifier)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp).size(24.dp))
    }
}

/** One switch row: the whole row is the target; TalkBack reads "title, summary, switch, on/off". */
@Composable
private fun SwitchRow(@DrawableRes icon: Int, title: Int, summary: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (summary != null) Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = scheme.primaryContainer,
                checkedBorderColor = scheme.primary,
                checkedThumbColor = scheme.onPrimaryContainer,
                uncheckedBorderColor = scheme.outline,
            ),
        )
    }
}
