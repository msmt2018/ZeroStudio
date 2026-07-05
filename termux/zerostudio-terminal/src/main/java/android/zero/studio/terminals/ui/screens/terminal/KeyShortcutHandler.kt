package com.termix.ui.screens.terminal

import android.view.KeyEvent
import com.blankj.utilcode.util.ClipboardUtils
import com.termix.settings.Settings
import com.termix.ui.activities.terminal.MainActivity
import com.termix.ui.screens.settings.CloseLastSessionBehavior

/**
 * Centralized keyboard shortcut handler for the terminal.
 * Reads configurable bindings from Settings and dispatches actions.
 */
object KeyShortcutHandler {

    /**
     * Handle a key event. Returns true if the key was consumed by a shortcut.
     */
    fun handle(keyCode: Int, event: KeyEvent, activity: MainActivity): Boolean {
        if (!Settings.shortcuts_enabled) return false

        val numberIndex = getNumberKeyIndex(keyCode)
        if (numberIndex != null && matchesNumberModifier(event)) {
            return handleSwitchToSession(activity, numberIndex)
        }

        // Try each action's binding
        for (action in ShortcutAction.entries) {
            val binding = Settings.getShortcutBinding(action)
            if (binding.matches(event)) {
                return dispatch(action, activity)
            }
        }
        return false
    }

    private fun dispatch(action: ShortcutAction, activity: MainActivity): Boolean {
        return when (action) {
            ShortcutAction.PASTE -> handlePaste()
            ShortcutAction.NEW_SESSION -> handleNewSession(activity)
            ShortcutAction.CLOSE_SESSION -> handleCloseSession(activity)
            ShortcutAction.SWITCH_SESSION_PREV -> handleSwitchSession(activity, forward = false)
            ShortcutAction.SWITCH_SESSION_NEXT -> handleSwitchSession(activity, forward = true)
        }
    }

    private fun handlePaste(): Boolean {
        val clip = ClipboardUtils.getText()?.toString() ?: return true
        if (clip.trim().isNotEmpty()) {
            terminalView.get()?.mEmulator?.paste(clip)
        }
        return true
    }

    private fun handleNewSession(activity: MainActivity): Boolean {
        val binder = activity.sessionBinder ?: return true
        val service = binder.getService()

        val sessionId = generateUniqueSessionId(service.sessionOrder.toList())
        terminalView.get()?.let {
            val client = TerminalBackEnd(it, activity)
            binder.createSession(sessionId, client, activity, workingMode = Settings.working_Mode)
        }
        changeSession(activity, session_id = sessionId)
        return true
    }

    private fun handleCloseSession(activity: MainActivity): Boolean {
        val binder = activity.sessionBinder ?: return true
        val service = binder.getService()
        val currentId = service.currentSession.value.first
        val sessionKeys = service.sessionOrder.toList()

        if (sessionKeys.size <= 1) {
            // Last session - check behavior setting
            if (Settings.close_last_session_behavior == CloseLastSessionBehavior.NEW_SESSION) {
                // Create new session BEFORE terminating old one to prevent service stopSelf()
                val newSessionId = generateUniqueSessionId(service.sessionOrder.toList())
                terminalView.get()?.let {
                    val client = TerminalBackEnd(it, activity)
                    binder.createSession(newSessionId, client, activity, workingMode = Settings.working_Mode)
                }
                changeSession(activity, session_id = newSessionId)
                // Now safe to terminate the old session
                binder.terminateSession(currentId)
            } else {
                // Exit app - terminate then finish
                binder.terminateSession(currentId)
                if (service.sessionOrder.isEmpty()) {
                    activity.finish()
                }
            }
        } else {
            val currentIndex = sessionKeys.indexOf(currentId)
            val nextId = if (currentIndex < sessionKeys.size - 1) {
                sessionKeys[currentIndex + 1]
            } else {
                sessionKeys[currentIndex - 1]
            }
            changeSession(activity, session_id = nextId)
            binder.terminateSession(currentId)
        }
        return true
    }

    private fun handleSwitchSession(activity: MainActivity, forward: Boolean): Boolean {
        val binder = activity.sessionBinder ?: return true
        val service = binder.getService()
        val sessionKeys = service.sessionOrder.toList()

        if (sessionKeys.size <= 1) return true

        val currentId = service.currentSession.value.first
        val currentIndex = sessionKeys.indexOf(currentId)

        val nextIndex = if (forward) {
            (currentIndex + 1) % sessionKeys.size
        } else {
            (currentIndex - 1 + sessionKeys.size) % sessionKeys.size
        }

        changeSession(activity, session_id = sessionKeys[nextIndex])
        return true
    }

    private fun getNumberKeyIndex(keyCode: Int): Int? {
        return when (keyCode) {
            KeyEvent.KEYCODE_1 -> 0
            KeyEvent.KEYCODE_2 -> 1
            KeyEvent.KEYCODE_3 -> 2
            KeyEvent.KEYCODE_4 -> 3
            KeyEvent.KEYCODE_5 -> 4
            KeyEvent.KEYCODE_6 -> 5
            KeyEvent.KEYCODE_7 -> 6
            KeyEvent.KEYCODE_8 -> 7
            KeyEvent.KEYCODE_9 -> 8
            else -> null
        }
    }

    /**
     * Checks if the current KeyEvent's modifiers match the user-configured
     * number shortcut binding (only modifier flags are compared).
     */
    private fun matchesNumberModifier(event: KeyEvent): Boolean {
        val binding = Settings.getNumberShortcutBinding()
        if (binding.isEmpty) return false
        return event.isCtrlPressed == binding.ctrl
                && event.isShiftPressed == binding.shift
                && event.isAltPressed == binding.alt
    }

    private fun handleSwitchToSession(activity: MainActivity, index: Int): Boolean {
        val binder = activity.sessionBinder ?: return true
        val service = binder.getService()
        val sessionKeys = service.sessionOrder.toList()

        if (sessionKeys.isEmpty()) return true

        // Strict: only respond if index is within current session count
        if (index >= sessionKeys.size) return true

        val targetId = sessionKeys[index]

        val currentId = service.currentSession.value.first
        if (targetId != currentId) {
            changeSession(activity, session_id = targetId)
        }
        return true
    }

    private fun generateUniqueSessionId(existingIds: List<String>): String {
        var index = 1
        var newId: String
        do {
            newId = "main$index"
            index++
        } while (newId in existingIds)
        return newId
    }
}
