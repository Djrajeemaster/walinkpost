package com.spicyraja.walinkposter

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val WORK_NAME = "wa_link_poster_schedule"

    fun schedule(ctx: Context) {
        val interval = LinkQueue.getIntervalMinutes(ctx).toLong().coerceAtLeast(15L)
        val request = PeriodicWorkRequestBuilder<ScheduleWorker>(interval, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
        LinkQueue.setScheduled(ctx, true)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
        LinkQueue.setScheduled(ctx, false)
    }
}
