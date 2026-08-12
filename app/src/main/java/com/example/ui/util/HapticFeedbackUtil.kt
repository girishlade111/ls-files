package com.example.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

enum class HapticType {
    LONG_PRESS,
    DELETE,
    MOVE_SUCCESS,
    SELECTION_TOGGLE
}

class AppHapticFeedback(
    private val view: View,
    private val composeHaptic: HapticFeedback
) {
    fun perform(type: HapticType) {
        when (type) {
            HapticType.LONG_PRESS -> performLongPress()
            HapticType.DELETE -> performDelete()
            HapticType.MOVE_SUCCESS -> performMoveSuccess()
            HapticType.SELECTION_TOGGLE -> performSelectionToggle()
        }
    }

    fun performLongPress() {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        } catch (_: Exception) {
            composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun performDelete() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } catch (_: Exception) {
            composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun performMoveSuccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        } catch (_: Exception) {
            composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun performSelectionToggle() {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {
            composeHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}

@Composable
fun rememberAppHapticFeedback(): AppHapticFeedback {
    val view = LocalView.current
    val composeHaptic = LocalHapticFeedback.current
    return remember(view, composeHaptic) {
        AppHapticFeedback(view, composeHaptic)
    }
}
