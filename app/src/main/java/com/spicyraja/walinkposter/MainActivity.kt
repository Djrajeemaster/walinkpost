package com.spicyraja.walinkposter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

        binding.startBtn.setOnClickListener {
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this, "Enable accessibility service first", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }

            val lines = binding.linksInput.text.toString()
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val previewWait = binding.previewWaitInput.text.toString().toIntOrNull() ?: 8
            val nextDelay = binding.nextDelayInput.text.toString().toIntOrNull() ?: 3

            PosterCoordinator.start(lines, previewWait, nextDelay)

            openWhatsApp()
            Toast.makeText(
                this,
                "WhatsApp open avutundi. Target chat/channel open ga vunchandi.",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.stopBtn.setOnClickListener {
            PosterCoordinator.stop()
        }

        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Enable accessibility service first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun openWhatsApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp")))
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${WhatsAppAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(expected, ignoreCase = true)
    }
}
