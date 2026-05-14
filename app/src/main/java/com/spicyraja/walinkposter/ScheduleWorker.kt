package com.spicyraja.walinkposter

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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

        PosterCoordinator.onBatchComplete = {
            LinkQueue.consumeBatch(ctx)
            if (LinkQueue.isEmpty(ctx)) {
                LinkQueue.setScheduled(ctx, false)
            }
            releaseWakeLock()
            PosterCoordinator.onBatchComplete = null
        }

        PosterCoordinator.start(batch, previewWait, nextDelay)
        launchWhatsApp()

        return Result.success()
    }

    private fun acquireWakeLock() {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "walinkposter:schedule"
        ).also { it.acquire(10 * 60 * 1000L) } // 10 min safety timeout
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun launchWhatsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) return
        val intent = ctx.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: ctx.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
            ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        ctx.startActivity(intent)
    }
}
