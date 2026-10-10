// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import io.github.ukpratik.lanternsudoku.engine.game.HapticKind

/**
 * Design §8: only `View.performHapticFeedback` (no VIBRATE permission, the allowlist is empty). CONFIRM needs API 30 and
 * falls back to CONTEXT_CLICK. Never a buzz for mistakes.
 */
object Haptics {
    fun perform(view: View, kind: HapticKind) {
        when (kind) {
            HapticKind.TICK -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            HapticKind.DOUBLE_TICK -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                Handler(Looper.getMainLooper()).postDelayed({ view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }, 60)
            }
            HapticKind.LONG_PRESS -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            HapticKind.CONFIRM -> view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK,
            )
            HapticKind.CONTEXT_CLICK -> view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }
}
