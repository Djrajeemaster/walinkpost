package com.spicyraja.walinkposter

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class WhatsAppAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        PosterCoordinator.onStatus?.invoke("Accessibility connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != "com.whatsapp") return
        if (!PosterCoordinator.isRunning()) return

        val root = rootInActiveWindow ?: return
        PosterCoordinator.onWhatsAppUiTick(root)
    }

    override fun onInterrupt() {
        PosterCoordinator.onStatus?.invoke("Accessibility interrupted")
    }
}
