package com.spicyraja.walinkposter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class ScheduleWorker(private val ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    companion object {
        // Held in companion so they survive worker instance GC
        private var wakeLock: PowerManager.WakeLock? = null
        private var screenLock: PowerManager.WakeLock? = null

        fun releaseAllLocks() {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            screenLock?.let { if (it.isHeld) it.release() }
            screenLock = null
        }
    }

    override fun doWork(): Result {
        if (LinkQueue.isEmpty(ctx)) {
            LinkQueue.setScheduled(ctx, false)
            return Result.success()
        }

        val batch = LinkQueue.peekBatch(ctx)
        val previewWait = LinkQueue.getPreviewWait(ctx)
        val nextDelay = LinkQueue.getNextDelay(ctx)

        // 1. Release any leftover locks from previous run
        releaseAllLocks()

        // 2. Wake screen FIRST
        wakeScreen()

        // 3. Set callback BEFORE start() — uses companion locks so they survive GC
        PosterCoordinator.onBatchComplete = {
            LinkQueue.consumeBatch(ctx)
            if (LinkQueue.isEmpty(ctx)) {
                LinkQueue.setScheduled(ctx, false)
            }
            releaseAllLocks()
            PosterCoordinator.onBatchComplete = null
        }

        // 4. Start coordinator
        PosterCoordinator.start(batch, previewWait, nextDelay)

        // 5. Keep CPU alive
        acquireWakeLock()

        // 6. Reset stale foreground state, then launch WhatsApp if needed after screen is on
        WhatsAppAccessibilityService.resetForegroundState()
        Handler(Looper.getMainLooper()).postDelayed({
            launchWhatsAppIfNeeded()
        }, 1500L)

        return Result.success()
    }

    private fun wakeScreen() {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            @Suppress("DEPRECATION")
            screenLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "walinkposter:screenon"
            ).also { it.acquire(10 * 60 * 1000L) }
        }
    }

    private fun acquireWakeLock() {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "walinkposter:schedule"
        ).also { it.acquire(10 * 60 * 1000L) }
    }

    private fun launchWhatsAppIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
            showLaunchNotification()
            return
        }

        // WhatsApp already in foreground — AccessibilityService will pick it up, no relaunch
        if (WhatsAppAccessibilityService.isWhatsAppForeground()) return

        val intent = ctx.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: ctx.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
            ?: return
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        ctx.startActivity(intent)
    }

    private fun showLaunchNotification() {
        val channelId = "wa_poster_schedule"
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "WA Link Poster", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val intent = ctx.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: ctx.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        val pi = if (intent != null) {
            android.app.PendingIntent.getActivity(
                ctx, 0,
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("WA Link Poster")
            .setContentText("Time to post! Tap to open WhatsApp.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(1001, notification)
    }
}
