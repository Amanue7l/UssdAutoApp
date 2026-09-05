package com.example.ussdauto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service that:
 * 1. Detects the PIN prompt and injects the PIN the user typed in the app
 * 2. Detects the confirmation prompt and automatically replies with "1"
 *
 * PIN is kept only in memory and cleared immediately after use.
 */
class UssdAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UssdAutoService"

        // Shared state (set by MainActivity)
        @Volatile
        var pendingPin: String? = null

        @Volatile
        var step: Int = 0   // 0 = waiting for PIN prompt, 1 = waiting for confirmation
    }

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

        // Only care about window changes and content changes
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val root = rootInActiveWindow ?: return
        val fullText = getAllText(root).lowercase()

        Log.d(TAG, "Event text: $fullText | step=$step")

        when (step) {
            0 -> handlePinStep(root, fullText)
            1 -> handleConfirmationStep(root, fullText)
        }
    }

    private fun handlePinStep(root: AccessibilityNodeInfo, fullText: String) {
        // Detect common PIN / password prompts
        val isPinPrompt = fullText.contains("pin") ||
                fullText.contains("password") ||
                fullText.contains("enter pin") ||
                fullText.contains("enter your pin") ||
                fullText.contains("password") ||
                fullText.contains("secret")

        if (!isPinPrompt) return

        val pin = pendingPin
        if (pin.isNullOrEmpty()) {
            Log.w(TAG, "PIN prompt detected but no pending PIN")
            return
        }

        Log.d(TAG, "PIN prompt detected → injecting PIN")

        // Inject the PIN
        if (injectText(root, pin)) {
            // Small delay then try to click Send / OK
            root.performAction(AccessibilityNodeInfo.ACTION_CLICK) // sometimes helps
            clickSendOrOk(root)
            step = 1
            pendingPin = null          // clear PIN immediately for security
            Log.d(TAG, "PIN injected and cleared from memory")
        }
    }

    private fun handleConfirmationStep(root: AccessibilityNodeInfo, fullText: String) {
        // Detect confirmation prompts that expect "1"
        val isConfirm = fullText.contains("confirm") ||
                fullText.contains("proceed") ||
                fullText.contains("continue") ||
                fullText.contains("1.") ||
                fullText.contains("press 1") ||
                fullText.contains("enter 1") ||
                fullText.contains("yes") ||
                fullText.contains("ok to confirm")

        if (!isConfirm) return

        Log.d(TAG, "Confirmation prompt detected → injecting 1")

        if (injectText(root, "1")) {
            clickSendOrOk(root)
            step = 0
            Log.d(TAG, "Confirmation 1 injected")
        }
    }

    /**
     * Tries several strategies to put text into the current input field.
     */
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

        // Strategy 2: Common view IDs used by phone USSD dialogs
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

        // Strategy 3: Find any editable EditText
        val editTexts = findEditableNodes(root)
        for (node in editTexts) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                Log.d(TAG, "Injected via generic EditText search")
                return true
            }
        }

        Log.w(TAG, "Could not find any input field to inject text")
        return false
    }

    private fun clickSendOrOk(root: AccessibilityNodeInfo) {
        val buttonTexts = listOf(
            "send", "ok", "confirm", "yes", "submit", "continue",
            "envoyer", "oui", "confirmer" // French fallbacks (common in some African countries)
        )
        for (text in buttonTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable || node.isEnabled) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "Clicked button with text: $text")
                        return
                    }
                }
            }
        }
        // Fallback: try global click on the root or focused node
        root.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findEditableNodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (node == null) return result
        if (node.isEditable) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            result.addAll(findEditableNodes(node.getChild(i)))
        }
        return result
    }

    private fun getAllText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        if (!node.text.isNullOrBlank()) {
            sb.append(node.text).append(" ")
        }
        if (!node.contentDescription.isNullOrBlank()) {
            sb.append(node.contentDescription).append(" ")
        }
        for (i in 0 until node.childCount) {
            sb.append(getAllText(node.getChild(i)))
        }
        return sb.toString()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        pendingPin = null
        step = 0
        super.onDestroy()
    }
}
