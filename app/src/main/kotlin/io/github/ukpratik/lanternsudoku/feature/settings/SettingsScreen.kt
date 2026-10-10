// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<Settings> = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setShowMistakes(on: Boolean) {
        viewModelScope.launch { repository.setShowMistakes(on) }
    }

    fun setMistakeLimit(limit: Int) {
        viewModelScope.launch { repository.setMistakeLimit(limit) }
    }
}

/**
 * M2 carries only the US-010 controls; the full Settings screen (design `settings`) is US-023 in M6. PRD v1.3: the
 * limit is only available while Show mistakes is on.
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = scheme.background) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 4.dp).semantics { heading() })
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.settings_mistakes_header), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 12.dp).semantics { heading() })
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .toggleable(value = s.showMistakes, role = Role.Switch, onValueChange = vm::setShowMistakes),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_show_mistakes), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.settings_show_mistakes_summary), style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = s.showMistakes,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = scheme.primaryContainer,
                                checkedBorderColor = scheme.primary,
                                checkedThumbColor = scheme.onPrimaryContainer,
                                uncheckedBorderColor = scheme.outline,
                            ),
                        )
                    }
                    val limitEnabled = s.showMistakes
                    Column(Modifier.padding(top = 12.dp).alpha(if (limitEnabled) 1f else 0.42f)) {
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
            }
        }
    }
}
