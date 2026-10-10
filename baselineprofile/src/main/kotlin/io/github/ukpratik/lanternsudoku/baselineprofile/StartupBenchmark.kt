// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NFR-001: cold start to an interactive Home ≤ 1.5 s on the reference device (Pixel 4a / Galaxy A52 class).
 * `timeToFullDisplayMs` is Home's `reportFullyDrawn` (the Play tab's first database emission). Compares no profile with
 * the shipped Baseline Profile. Run: `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` (add
 * `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR` on an emulator, and record
 * the result as an emulator proxy, PRD v1.3).
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test fun startupWithBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("New game")), TIMEOUT_MS)
    }
}
