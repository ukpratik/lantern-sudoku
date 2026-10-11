// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.settings

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import io.github.ukpratik.lanternsudoku.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One shipped library or asset and its licences, from the AboutLibraries report (ADR-008's licence gate). */
data class LicenceEntry(val name: String, val version: String, val licences: List<Licence>)

data class Licence(val name: String, val text: String)

/**
 * Reads the report the AboutLibraries Gradle plugin generates at build time (`res/raw/aboutlibraries.json`, offline,
 * licence texts included). The same report gates the release build, so the screen lists exactly what ships, per flavour.
 */
object Licences {
    fun parse(json: String): List<LicenceEntry> {
        val root = JSONObject(json)
        val texts = root.optJSONObject("licenses") ?: JSONObject()
        val libs = root.optJSONArray("libraries") ?: return emptyList()
        return (0 until libs.length()).map { i ->
            val lib = libs.getJSONObject(i)
            val ids = lib.optJSONArray("licenses")
            val licences = (0 until (ids?.length() ?: 0)).map { j ->
                val id = ids!!.getString(j)
                val l = texts.optJSONObject(id)
                Licence(l?.optString("name")?.takeIf { it.isNotBlank() } ?: id, l?.optString("content").orEmpty().ifBlank { l?.optString("url").orEmpty() })
            }
            LicenceEntry(lib.optString("name").ifBlank { lib.optString("uniqueId") }, lib.optString("artifactVersion"), licences)
        }.sortedBy { it.name.lowercase() }
    }

    fun read(res: Resources): List<LicenceEntry> = parse(res.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() })
}

/** Settings › Open-source licences: name, version and licence of everything shipped; a tap shows the licence text. */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val res = LocalResources.current
    var entries by remember { mutableStateOf<List<LicenceEntry>?>(null) }
    LaunchedEffect(res) { entries = withContext(Dispatchers.IO) { Licences.read(res) } } // NFR-012: no I/O on the main thread
    var open by remember { mutableStateOf<LicenceEntry?>(null) }
    val scheme = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = scheme.background) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.licences_title), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 4.dp).semantics { heading() })
                }
                LazyColumn(Modifier.fillMaxSize().semantics { testTag = "licences" }) {
                    items(entries.orEmpty(), key = { it.name + it.version }) { e ->
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { open = e }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text(stringResource(R.string.licence_row, e.name, e.version).trim(), style = MaterialTheme.typography.bodyLarge)
                            Text(e.licences.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = scheme.outlineVariant)
                    }
                }
            }
        }
    }
    open?.let { e ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(e.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (l in e.licences) {
                        Text(l.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
                        Text(l.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.licence_close)) } },
            containerColor = scheme.surfaceContainer,
        )
    }
}
