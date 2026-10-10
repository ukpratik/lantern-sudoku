// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import androidx.test.core.app.ApplicationProvider
import io.github.ukpratik.lanternsudoku.LanternApp
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Closes the real app's database after an activity test. Robolectric makes a new [LanternApp] per test; an unclosed
 * database from one test is finalized during a later one, where the debug build's StrictMode (leaked closables,
 * penalty death) would kill the test JVM.
 */
class AppTeardown : ExternalResource() {
    override fun after() {
        val container = ApplicationProvider.getApplicationContext<LanternApp>().container
        runBlocking { container.saveQueue.flush() }
        container.appScope.cancel()
        container.database.close()
    }
}
