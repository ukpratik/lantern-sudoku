// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code from launch to an interactive Home, starting an Easy game, and placing a digit, into the Baseline
 * Profile shipped in the APK (NFR-001). Run: `./gradlew :app:generatePlayReleaseBaselineProfile` with a device attached.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("New game")), TIMEOUT_MS)
        device.findObject(By.text("Easy"))?.click()
        device.wait(Until.hasObject(By.desc("Hint")), TIMEOUT_MS)
        // Select the first empty cell and place a digit: the board's Canvas, the reducer and the autosave path.
        device.findObject(By.descContains("empty"))?.click()
        device.findObject(By.descStartsWith("5, "))?.click()
        device.waitForIdle()
        device.pressBack()
        device.pressBack()
    }
}

internal const val PACKAGE = "io.github.ukpratik.lanternsudoku"
internal const val TIMEOUT_MS = 10_000L
