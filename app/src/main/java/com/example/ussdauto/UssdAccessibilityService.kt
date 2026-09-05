package com.example.ussdauto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * New flow:
 * 1. App dials the USSD code
 * 2. User manually types PIN on the USSD screen
 * 3. After user sends PIN, confirmation screen appears
 * 4. This service automatically types "1" and confirms
 */
class UssdAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UssdAutoService"

        @Volatile
        var waitingForConfirmation = false

        @Volatile
        var step = 0   // 0 = waiting for confirmation after PIN
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.d(TAG, "USSD Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!waitingForConfirmation) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val root = rootInActiveWindow ?: return
        val fullText = getAllText(root).lowercase()

        Log.d(TAG, "Event text: $fullText")

        // Detect confirmation screen (after user has entered PIN)
        val isConfirm = fullText.contains("confirm") ||
                fullText.contains("proceed") ||
                fullText.contains("continue") ||
                fullText.contains("1.") ||
                fullText.contains("press 1") ||
                fullText.contains("enter 1") ||
                fullText.contains("yes") ||
                fullText.contains("ok to confirm") ||
                fullText.contains("do you want") ||
                fullText.contains("are you sure") ||
                fullText.contains("confirmation")

        // Also detect if it looks like a menu that expects a number choice
        val looksLikeConfirmation = isConfirm || 
                (fullText.contains("1") && (fullText.contains("yes") || fullText.contains("ok") || fullText.contains("confirm")))

        if (looksLikeConfirmation) {
            Log.d(TAG, "Confirmation screen detected → will auto-enter 1")

            // Small delay so the screen is fully loaded
            handler.postDelayed({
                val currentRoot = rootInActiveWindow ?: return@postDelayed
                if (injectText(currentRoot, "1")) {
                    clickSendOrOk(currentRoot)
                    waitingForConfirmation = false
                    step = 0
                    Log.d(TAG, "Successfully auto-confirmed with 1")
                }
            }, 600)
        }
    }

    private fun injectText(root: AccessibilityNodeInfo, value: String): Boolean {
        // Strategy 1: Currently focused input
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                Log.d(TAG, "Injected via focused node")
                return true
            }
        }

        // Strategy 2: Common view IDs
        val possibleIds = listOf(
            "android:id/input",
            "com.android.phone:id/input_field",
            "com.android.phone:id/input",
            "com.android.server.telecom:id/input",
            "android:id/edit"
        )
        for (id in possibleIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val node = nodes[0]
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    Log.d(TAG, "Injected via viewId $id")
                    return true
                }
            }
        }

        // Strategy 3: Any editable field
        val editTexts = findEditableNodes(root)
        for (node in editTexts) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                Log.d(TAG, "Injected via generic EditText")
                return true
            }
        }

        Log.w(TAG, "Could not find input field")
        return false
    }

    private fun clickSendOrOk(root: AccessibilityNodeInfo) {
        val buttonTexts = listOf(
            "send", "ok", "confirm", "yes", "submit", "continue",
            "envoyer", "oui", "confirmer"
        )
        for (text in buttonTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable || node.isEnabled) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "Clicked button: $text")
                        return
                    }
                }
            }
        }
    }

    private fun findEditableNodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (node == null) return result
        if (node.isEditable) result.add(node)
        for (i in 0 until node.childCount) {
            result.addAll(findEditableNodes(node.getChild(i)))
        }
        return result
    }

    private fun getAllText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        if (!node.text.isNullOrBlank()) sb.append(node.text).append(" ")
        if (!node.contentDescription.isNullOrBlank()) sb.append(node.contentDescription).append(" ")
        for (i in 0 until node.childCount) {
            sb.append(getAllText(node.getChild(i)))
        }
        return sb.toString()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        waitingForConfirmation = false
        step = 0
        super.onDestroy()
    }
}
