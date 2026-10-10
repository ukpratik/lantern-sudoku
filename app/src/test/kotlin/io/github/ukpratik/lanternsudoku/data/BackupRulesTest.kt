// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.datastore.preferences.preferencesDataStoreFile
import org.xmlpull.v1.XmlPullParser

/**
 * NFR-010 (supports TC-213 to TC-215): both backup rule files include exactly the database and the DataStore folder, for cloud backup and
 * device transfer alike, and the names match what the app really writes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupRulesTest {
    private val res = ApplicationProvider.getApplicationContext<Application>().resources

    /** (section, domain, path) of every include/exclude element. */
    private fun rules(xml: Int): List<Triple<String, String, String>> {
        val p = res.getXml(xml)
        val out = mutableListOf<Triple<String, String, String>>()
        var section = ""
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "include", "exclude" -> out += Triple("$section/${p.name}", p.getAttributeValue(null, "domain"), p.getAttributeValue(null, "path"))
                else -> section = p.name
            }
        }
        return out
    }

    private val expected = listOf("database" to "lantern.db", "file" to "datastore")

    @Test
    fun dataExtractionRules_cloudAndTransfer_includeDbAndSettings() {
        val r = rules(R.xml.data_extraction_rules)
        for (section in listOf("cloud-backup", "device-transfer")) {
            assertThat(r.filter { it.first == "$section/include" }.map { it.second to it.third }).containsExactlyElementsIn(expected)
        }
        assertThat(r.filter { it.first.endsWith("/exclude") }).isEmpty()
    }

    @Test
    fun fullBackupContent_forApi30AndBelow_isTheSame() {
        assertThat(rules(R.xml.backup_rules).map { it.second to it.third }).containsExactlyElementsIn(expected)
    }

    @Test
    fun theNamesAreTheFilesTheAppWrites() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        assertThat(ctx.getDatabasePath(io.github.ukpratik.lanternsudoku.data.db.LanternDatabase.NAME).name).isEqualTo("lantern.db")
        assertThat(ctx.preferencesDataStoreFile("settings").parentFile!!.name).isEqualTo("datastore")
    }
}
