package com.spicyraja.walinkposter

import android.view.accessibility.AccessibilityNodeInfo
import android.content.ClipData
import android.content.ClipboardManager
import java.util.ArrayDeque

object PosterCoordinator {
    private var running = false
    private var waitingForPaste = false
    private var waitingForSend = false
    private var pasteReadyAtMs = 0L
    private var sendReadyAtMs = 0L
    private var previewWaitMs = 8000L
    private var nextDelayMs = 3000L
    private var cooldownUntilMs = 0L
    private var sentCount = 0
    private var totalCount = 0
    private var clipboard: ClipboardManager? = null

    private val queue = ArrayDeque<String>()

    var onStatus: ((String) -> Unit)? = null
    var onBatchComplete: (() -> Unit)? = null

    fun start(urls: List<String>, previewWaitSeconds: Int, nextDelaySeconds: Int) {
        stop()
        queue.clear()
        urls.map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .forEach { queue.add(it) }

        totalCount = queue.size
        sentCount = 0
        previewWaitMs = (previewWaitSeconds.coerceAtLeast(1) * 1000L)
        nextDelayMs = (nextDelaySeconds.coerceAtLeast(1) * 1000L)
        waitingForPaste = false
        waitingForSend = false
        pasteReadyAtMs = 0L
        sendReadyAtMs = 0L
        cooldownUntilMs = 0L
        running = queue.isNotEmpty()

        if (!running) {
            onStatus?.invoke("No valid URLs")
            return
        }

        onStatus?.invoke("Running: 0/$totalCount")
    }

    fun setClipboardManager(cm: ClipboardManager) {
        clipboard = cm
    }

    fun stop() {
        running = false
        waitingForPaste = false
        waitingForSend = false
        pasteReadyAtMs = 0L
        sendReadyAtMs = 0L
        cooldownUntilMs = 0L
    }

    fun stopAndNotify() {
        stop()
        onStatus?.invoke("Stopped")
    }

    fun isRunning(): Boolean = running

    fun onWhatsAppUiTick(root: AccessibilityNodeInfo?): Boolean {
        if (!running || root == null) return false
        val now = System.currentTimeMillis()

        if (now < cooldownUntilMs) return false

        if (waitingForPaste) {
            if (now < pasteReadyAtMs) return false

            val pasteButton = findPasteButton(root) ?: run {
                onStatus?.invoke("Waiting for paste button in context menu...")
                return false
            }

            pasteButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitingForPaste = false
            waitingForSend = true
            sendReadyAtMs = now + 1000
            onStatus?.invoke("Pasting ${sentCount + 1}/$totalCount...")
            return true
        }

        if (waitingForSend) {
            if (now < sendReadyAtMs) return false

            val sendNode = findSendButton(root) ?: run {
                onStatus?.invoke("Waiting for send button...")
                return false
            }

            sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (queue.isNotEmpty()) queue.removeFirst()
            sentCount += 1
            waitingForSend = false
            cooldownUntilMs = now + nextDelayMs

            if (queue.isEmpty()) {
                running = false
                onStatus?.invoke("Completed: $sentCount/$totalCount")
                onBatchComplete?.invoke()
            } else {
                onStatus?.invoke("Sent $sentCount/$totalCount")
            }
            return true
        }

        val nextUrl = queue.firstOrNull() ?: run {
            running = false
            onStatus?.invoke("Completed: $sentCount/$totalCount")
            return false
        }

        clipboard?.let { cm ->
            val clip = ClipData.newPlainText("url", nextUrl)
            cm.setPrimaryClip(clip)
        }

        val input = findMessageInput(root) ?: run {
            onStatus?.invoke("Waiting for message box in WhatsApp chat...")
            return false
        }

        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        input.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        input.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)

        waitingForPaste = true
        pasteReadyAtMs = now + 500
        onStatus?.invoke("Waiting for context menu...")

        return true
    }

    private fun findMessageInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidateIds = listOf(
            "com.whatsapp:id/entry",
            "com.whatsapp.w4b:id/entry",
            "com.whatsapp:id/caption",
            "com.whatsapp.w4b:id/caption"
        )
        for (id in candidateIds) {
            val byId = root.findAccessibilityNodeInfosByViewId(id)
            if (!byId.isNullOrEmpty()) {
                val editable = byId.lastOrNull { it.isEditable }
                if (editable != null) return editable
                return byId.last()
            }
        }

        val editables = mutableListOf<AccessibilityNodeInfo>()
        collectEditTexts(root, editables)
        return editables.lastOrNull()
    }

    private fun collectEditTexts(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.className?.toString()?.contains("EditText") == true && node.isEditable) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectEditTexts(it, out) }
        }
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidateIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp:w4b:id/send",
            "com.whatsapp:id/send_btn",
            "com.whatsapp.w4b:id/send_btn"
        )
        for (id in candidateIds) {
            val byId = root.findAccessibilityNodeInfosByViewId(id)
            if (!byId.isNullOrEmpty()) return byId.first()
        }

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectByContentDesc(root, candidates)
        return candidates.firstOrNull()
    }

    private fun findPasteButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val pasteTexts = listOf("Paste", "Paste as plain text")
        val byText = mutableListOf<AccessibilityNodeInfo>()
        collectNodesByText(root, byText, pasteTexts)
        if (byText.isNotEmpty()) return byText.first()

        val byDesc = mutableListOf<AccessibilityNodeInfo>()
        collectByContentDesc(root, byDesc)
        val pasteCandidates = byDesc.filter { it.contentDescription?.toString()?.contains("paste", true) == true }
        if (pasteCandidates.isNotEmpty()) return pasteCandidates.first()

        val allTextNodes = mutableListOf<AccessibilityNodeInfo>()
        collectTextLeafNodes(root, allTextNodes)
        return allTextNodes.firstOrNull { it.text?.toString()?.equals("Paste", true) == true }
    }

    private fun collectNodesByText(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>, texts: List<String>) {
        val nodeText = node.text?.toString() ?: ""
        if (texts.any { nodeText.contains(it, ignoreCase = true) }) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectNodesByText(it, out, texts) }
        }
    }

    private fun collectTextLeafNodes(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.childCount == 0) {
            val t = node.text?.toString() ?: ""
            if (t.isNotBlank()) out.add(node)
            return
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTextLeafNodes(it, out) }
        }
    }

    private fun collectByContentDesc(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains("send") || desc.contains("reply") || desc.contains("forward") || desc.contains("paste")) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectByContentDesc(it, out) }
        }
    }
}
