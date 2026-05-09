package com.spicyraja.walinkposter

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class WhatsAppAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            tryAutoTick()
            mainHandler.postDelayed(this, 600L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        PosterCoordinator.onStatus?.invoke("Accessibility connected")
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.post(tickRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        if (!PosterCoordinator.isRunning()) return

        val root = rootInActiveWindow ?: return
        PosterCoordinator.onWhatsAppUiTick(root)
    }

    private fun tryAutoTick() {
        if (!PosterCoordinator.isRunning()) return

        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") {
            PosterCoordinator.onStatus?.invoke("Open WhatsApp chat/channel and keep it visible...")
            return
        }

        PosterCoordinator.onWhatsAppUiTick(root)
    }

    override fun onInterrupt() {
        PosterCoordinator.onStatus?.invoke("Accessibility interrupted")
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(tickRunnable)
        super.onDestroy()
    }
}
