// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode

class LanternApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // NFR-012: no disk or network I/O on the main thread. Debug builds crash on a violation; release does nothing.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().penaltyDeath().build())
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectCleartextNetwork().penaltyLog().penaltyDeath().build(),
            )
        }
    }
}
