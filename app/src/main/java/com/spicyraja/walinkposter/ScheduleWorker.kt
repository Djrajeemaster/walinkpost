package com.spicyraja.walinkposter

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class ScheduleWorker(private val ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun doWork(): Result {
        if (LinkQueue.isEmpty(ctx)) {
            LinkQueue.setScheduled(ctx, false)
            return Result.success()
        }

        val batch = LinkQueue.peekBatch(ctx)
        val previewWait = LinkQueue.getPreviewWait(ctx)
        val nextDelay = LinkQueue.getNextDelay(ctx)

        acquireWakeLock()

        // Set callback BEFORE start() so it is never wiped
        PosterCoordinator.onBatchComplete = {
            LinkQueue.consumeBatch(ctx)
            if (LinkQueue.isEmpty(ctx)) {
                LinkQueue.setScheduled(ctx, false)
            }
            releaseWakeLock()
            PosterCoordinator.onBatchComplete = null
        }

        PosterCoordinator.start(batch, previewWait, nextDelay)
        wakeAndLaunchWhatsApp()

        return Result.success()
    }

    private fun acquireWakeLock() {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        // PARTIAL_WAKE_LOCK keeps CPU alive without triggering unlock dialog
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "walinkposter:schedule"
        ).also { it.acquire(10 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun wakeAndLaunchWhatsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
            // No overlay permission — show notification so user can tap to open WhatsApp
            showLaunchNotification()
            return
        }

        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        if (!pm.isInteractive) {
            // Screen is off — turn it on using a bright wake lock briefly
            val screenLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "walinkposter:screenon"
            )
            screenLock.acquire(3000L) // just enough to turn screen on
        }

        // Dismiss keyguard if possible (works on non-secure lock screens)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(null, null)
        }

        val intent = ctx.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: ctx.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
            ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
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
