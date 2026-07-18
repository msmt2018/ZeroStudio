package android.zero.studio.core.presentation.utils

import android.view.HapticFeedbackConstants
import android.view.View

object HapticUtils {

     fun View.weakHaptic() {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun View.strongHaptic() {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}