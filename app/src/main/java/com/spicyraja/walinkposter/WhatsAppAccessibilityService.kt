package com.spicyraja.walinkposter

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class WhatsAppAccessibilityService : AccessibilityService() {

    private val uiHandler = Handler(Looper.getMainLooper())
    private var lastWhatsAppPackage: String? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            try {
                if (PosterCoordinator.isRunning()) {
                    val pkg = lastWhatsAppPackage
                    if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
                        rootInActiveWindow?.let { PosterCoordinator.onWhatsAppUiTick(it) }
                    }
                }
            } finally {
                uiHandler.postDelayed(this, 500L)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        PosterCoordinator.onStatus?.invoke("Accessibility connected")
        uiHandler.removeCallbacks(tickRunnable)
        uiHandler.post(tickRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        lastWhatsAppPackage = pkg
        if (!PosterCoordinator.isRunning()) return

        val root = rootInActiveWindow ?: return
        PosterCoordinator.onWhatsAppUiTick(root)
    }

    override fun onInterrupt() {
        uiHandler.removeCallbacks(tickRunnable)
        PosterCoordinator.onStatus?.invoke("Accessibility interrupted")
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(tickRunnable)
        super.onDestroy()
    }
}
