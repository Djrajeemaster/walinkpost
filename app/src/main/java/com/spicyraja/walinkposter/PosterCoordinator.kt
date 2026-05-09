package com.spicyraja.walinkposter

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

object PosterCoordinator {
    private var running = false
    private var waitingForPreview = false
    private var previewWaitMs = 8000L
    private var nextDelayMs = 3000L
    private var previewReadyAtMs = 0L
    private var cooldownUntilMs = 0L
    private var sentCount = 0
    private var totalCount = 0

    private val queue = ArrayDeque<String>()

    var onStatus: ((String) -> Unit)? = null

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
        waitingForPreview = false
        previewReadyAtMs = 0L
        cooldownUntilMs = 0L
        running = queue.isNotEmpty()

        if (!running) {
            onStatus?.invoke("No valid URLs")
            return
        }

        onStatus?.invoke("Running: 0/$totalCount")
    }

    fun stop() {
        running = false
        waitingForPreview = false
        previewReadyAtMs = 0L
        cooldownUntilMs = 0L
        onStatus?.invoke("Stopped")
    }

    fun isRunning(): Boolean = running

    fun onWhatsAppUiTick(root: AccessibilityNodeInfo?): Boolean {
        if (!running || root == null) return false
        val now = System.currentTimeMillis()

        if (now < cooldownUntilMs) return false

        if (waitingForPreview) {
            if (now < previewReadyAtMs) {
                return false
            }

            val sendNode = findSendButton(root) ?: run {
                onStatus?.invoke("Preview ready, waiting for send button...")
                return false
            }

            sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (queue.isNotEmpty()) {
                queue.removeFirst()
            }
            sentCount += 1
            waitingForPreview = false
            cooldownUntilMs = now + nextDelayMs

            if (queue.isEmpty()) {
                running = false
                onStatus?.invoke("Completed: $sentCount/$totalCount")
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

        val input = findMessageInput(root) ?: run {
            onStatus?.invoke("Waiting for message box in WhatsApp chat...")
            return false
        }

        if (!setNodeText(input, nextUrl)) {
            onStatus?.invoke("Failed to paste. Retrying...")
            return false
        }

        waitingForPreview = true
        previewReadyAtMs = now + previewWaitMs
        onStatus?.invoke("Pasted ${sentCount + 1}/$totalCount. Waiting preview...")

        return true
    }

    private fun findMessageInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
        if (!byId.isNullOrEmpty()) return byId.last()

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

    private fun setNodeText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (!byId.isNullOrEmpty()) return byId.first()

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectByContentDesc(root, candidates)
        return candidates.firstOrNull()
    }

    private fun collectByContentDesc(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains("send") || desc.contains("పంపు") || desc.contains("enviar")) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectByContentDesc(it, out) }
        }
    }
}
