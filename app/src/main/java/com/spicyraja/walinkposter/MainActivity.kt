package com.spicyraja.walinkposter

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.spicyraja.walinkposter.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PosterCoordinator.onStatus = { status ->
            runOnUiThread { binding.statusText.text = "Status: $status" }
        }

        // Restore saved schedule values into fields
        binding.batchSizeInput.setText(LinkQueue.getBatchSize(this).toString())
        binding.intervalInput.setText(LinkQueue.getIntervalMinutes(this).toString())

        binding.startBtn.setOnClickListener {
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this, "Enable accessibility service first", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            val lines = binding.linksInput.text.toString()
                .split("\n").map { it.trim() }.filter { it.isNotBlank() }
            val previewWait = binding.previewWaitInput.text.toString().toIntOrNull() ?: 8
            val nextDelay = binding.nextDelayInput.text.toString().toIntOrNull() ?: 3
            PosterCoordinator.start(lines, previewWait, nextDelay)
            openWhatsApp()
            Toast.makeText(this, "WhatsApp open avutundi. Target chat/channel open ga vunchandi.", Toast.LENGTH_LONG).show()
        }

        binding.stopBtn.setOnClickListener { PosterCoordinator.stop() }

        binding.scheduleStartBtn.setOnClickListener {
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this, "Enable accessibility service first", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            val rawLinks = binding.queueLinksInput.text.toString()
                .split("\n").map { it.trim() }
                .filter { it.startsWith("http://") || it.startsWith("https://") }
            if (rawLinks.isEmpty()) {
                Toast.makeText(this, "Add links to the schedule queue first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val batch = binding.batchSizeInput.text.toString().toIntOrNull() ?: 5
            val interval = binding.intervalInput.text.toString().toIntOrNull() ?: 30
            val previewWait = binding.previewWaitInput.text.toString().toIntOrNull() ?: 8
            val nextDelay = binding.nextDelayInput.text.toString().toIntOrNull() ?: 3

            LinkQueue.saveLinks(this, rawLinks)
            LinkQueue.setBatchSize(this, batch)
            LinkQueue.setIntervalMinutes(this, interval)
            LinkQueue.setPreviewWait(this, previewWait)
            LinkQueue.setNextDelay(this, nextDelay)

            Scheduler.schedule(this)
            updateQueueStatus()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant 'Draw over other apps' for locked screen auto-launch", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                requestBatteryOptimizationExemption()
            }

            Toast.makeText(this, "Scheduled! ${rawLinks.size} links, $batch per run, every $interval min", Toast.LENGTH_LONG).show()
        }

        binding.scheduleStopBtn.setOnClickListener {
            Scheduler.cancel(this)
            updateQueueStatus()
            Toast.makeText(this, "Schedule cancelled", Toast.LENGTH_SHORT).show()
        }

        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Enable accessibility service first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        updateQueueStatus()
    }

    override fun onDestroy() {
        PosterCoordinator.onStatus = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            requestBatteryOptimizationExemption()
        }
        updateQueueStatus()
    }

    private fun updateQueueStatus() {
        val count = LinkQueue.size(this)
        val scheduled = LinkQueue.isScheduled(this)
        val interval = LinkQueue.getIntervalMinutes(this)
        val batch = LinkQueue.getBatchSize(this)
        binding.queueStatusText.text = if (scheduled)
            "Queue: $count links remaining | $batch per run | every $interval min"
        else
            "Queue: $count links | Schedule OFF"
    }

    private fun openWhatsApp() {
        val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp")))
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Disable battery optimization for reliable scheduling", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${WhatsAppAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(expected, ignoreCase = true)
    }
}
