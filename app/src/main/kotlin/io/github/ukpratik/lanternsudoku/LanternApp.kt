// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import kotlinx.coroutines.launch

class LanternApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // NFR-012: no disk or network I/O on the main thread. Debug builds crash on a violation; release does nothing.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().penaltyDeath().build())
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectCleartextNetwork().penaltyLog().penaltyDeath().build(),
            )
        }
        container = AppContainer(this)
        container.settingsState // start reading settings now, off the main thread, for the first frame's theme
        // US-025 "never on the first day": remember the first launch's date (once; DataStore, off the main thread).
        container.appScope.launch { container.settingsRepository.recordFirstLaunch(container.today.refresh().toEpochDay()) }
    }
}
