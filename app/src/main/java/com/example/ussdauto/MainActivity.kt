package com.example.ussdauto

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ussdauto.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Fixed USSD code
    private val USSD_CODE = "*847*1*1*0977759483*1000#"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startUssd()
        } else {
            Toast.makeText(this, getString(R.string.permission_needed), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvUssdCode.text = USSD_CODE

        binding.btnEnableAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.btnSend.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, getString(R.string.accessibility_needed), Toast.LENGTH_LONG).show()
                openAccessibilitySettings()
                return@setOnClickListener
            }

            // Reset the service state so it waits for confirmation
            UssdAccessibilityService.step = 0
            UssdAccessibilityService.waitingForConfirmation = true

            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                        == PackageManager.PERMISSION_GRANTED -> {
                    startUssd()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
            }
        }
    }

    private fun startUssd() {
        binding.tvStatus.text = "Dialing USSD… Enter your PIN when asked"
        dialUssd(USSD_CODE)
        Toast.makeText(this, "USSD started. Type your PIN, then the app will auto-confirm with 1", Toast.LENGTH_LONG).show()
    }

    private fun dialUssd(code: String) {
        try {
            val encoded = Uri.encode(code)
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$encoded")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, UssdAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = ComponentName.unflattenFromString(colonSplitter.next())
            if (componentName != null && componentName == expectedComponent) {
                return true
            }
        }
        return false
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Find \"USSD Auto\" and turn it ON", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open Accessibility settings", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAccessibilityServiceEnabled()) {
            binding.tvStatus.text = "Accessibility Service: ON ✓  Ready"
            binding.btnEnableAccessibility.text = "Accessibility is Enabled"
        } else {
            binding.tvStatus.text = "Accessibility Service: OFF – please enable it"
            binding.btnEnableAccessibility.text = getString(R.string.enable_accessibility)
        }
    }
}
