package com.spicyraja.walinkposter

import android.content.Context

object LinkQueue {
    private const val PREFS = "link_queue"
    private const val KEY_LINKS = "links"
    private const val KEY_BATCH = "batch_size"
    private const val KEY_PREVIEW = "preview_wait"
    private const val KEY_DELAY = "next_delay"
    private const val KEY_INTERVAL = "interval_minutes"
    private const val KEY_SCHEDULED = "scheduled"

    fun saveLinks(ctx: Context, links: List<String>) {
        prefs(ctx).edit().putString(KEY_LINKS, links.joinToString("\n")).apply()
    }

    fun peekBatch(ctx: Context): List<String> {
        val all = loadAll(ctx)
        val batch = getBatchSize(ctx)
        return all.take(batch)
    }

    fun consumeBatch(ctx: Context) {
        val all = loadAll(ctx)
        val batch = getBatchSize(ctx)
        saveLinks(ctx, all.drop(batch))
    }

    fun isEmpty(ctx: Context) = loadAll(ctx).isEmpty()

    fun size(ctx: Context) = loadAll(ctx).size

    fun loadAll(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_LINKS, "") ?: ""
        return raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun getBatchSize(ctx: Context) = prefs(ctx).getInt(KEY_BATCH, 5)
    fun setBatchSize(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_BATCH, v).apply()

    fun getPreviewWait(ctx: Context) = prefs(ctx).getInt(KEY_PREVIEW, 8)
    fun setPreviewWait(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_PREVIEW, v).apply()

    fun getNextDelay(ctx: Context) = prefs(ctx).getInt(KEY_DELAY, 3)
    fun setNextDelay(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DELAY, v).apply()

    fun getIntervalMinutes(ctx: Context) = prefs(ctx).getInt(KEY_INTERVAL, 30)
    fun setIntervalMinutes(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_INTERVAL, v).apply()

    fun isScheduled(ctx: Context) = prefs(ctx).getBoolean(KEY_SCHEDULED, false)
    fun setScheduled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_SCHEDULED, v).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
