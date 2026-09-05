package com.example.ussdauto

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ussdauto.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val USSD_CODE = "*847*1*1*0977759483*1000#"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        updatePermissionStatus()
        if (isGranted) {
            Toast.makeText(this, "Phone permission granted ✓", Toast.LENGTH_SHORT).show()
            // If accessibility is also ready, we can start
            if (isAccessibilityServiceEnabled()) {
                startUssd()
            }
        } else {
            showPhonePermissionDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvUssdCode.text = USSD_CODE

        binding.btnEnableAccessibility.setOnClickListener {
            showAccessibilityHelpDialog()
        }

        binding.btnGrantPhone.setOnClickListener {
            requestPhonePermission()
        }

        binding.btnSend.setOnClickListener {
            tryStartTransaction()
        }

        updatePermissionStatus()
    }

    private fun tryStartTransaction() {
        val hasPhone = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
        val hasAccessibility = isAccessibilityServiceEnabled()

        when {
            !hasAccessibility -> {
                showAccessibilityHelpDialog()
            }
            !hasPhone -> {
                requestPhonePermission()
            }
            else -> {
                // Everything ready
                UssdAccessibilityService.waitingForConfirmation = true
                UssdAccessibilityService.step = 0
                startUssd()
            }
        }
    }

    private fun requestPhonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Phone permission already granted ✓", Toast.LENGTH_SHORT).show()
            updatePermissionStatus()
            return
        }
        requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
    }

    private fun startUssd() {
        binding.tvStatus.text = "Dialing USSD…\nType your PIN when asked.\nThe app will auto-confirm with 1."
        dialUssd(USSD_CODE)
        Toast.makeText(
            this,
            "USSD started. Enter your PIN, then the app will automatically press 1",
            Toast.LENGTH_LONG
        ).show()
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
            Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
            showPhonePermissionDialog()
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

    private fun updatePermissionStatus() {
        val hasPhone = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
        val hasAccessibility = isAccessibilityServiceEnabled()

        // Accessibility status
        if (hasAccessibility) {
            binding.tvAccessibilityStatus.text = "✓ Accessibility Service: ON"
            binding.tvAccessibilityStatus.setTextColor(0xFF2E7D32.toInt()) // green
            binding.btnEnableAccessibility.text = "Accessibility is Enabled"
        } else {
            binding.tvAccessibilityStatus.text = "✗ Accessibility Service: OFF"
            binding.tvAccessibilityStatus.setTextColor(0xFFC62828.toInt()) // red
            binding.btnEnableAccessibility.text = "1. Enable Accessibility Service"
        }

        // Phone status
        if (hasPhone) {
            binding.tvPhoneStatus.text = "✓ Phone Permission: GRANTED"
            binding.tvPhoneStatus.setTextColor(0xFF2E7D32.toInt())
            binding.btnGrantPhone.visibility = View.GONE
        } else {
            binding.tvPhoneStatus.text = "✗ Phone Permission: NOT GRANTED"
            binding.tvPhoneStatus.setTextColor(0xFFC62828.toInt())
            binding.btnGrantPhone.visibility = View.VISIBLE
            binding.btnGrantPhone.text = "2. Grant Phone Permission"
        }

        // Overall status + Start button
        if (hasAccessibility && hasPhone) {
            binding.tvStatus.text = "All permissions ready ✓\nYou can start the transaction"
            binding.btnSend.isEnabled = true
            binding.btnSend.alpha = 1.0f
        } else {
            binding.tvStatus.text = "Please complete the steps above first"
            binding.btnSend.isEnabled = true // still allow click so we can guide them
            binding.btnSend.alpha = 0.85f
        }
    }

    private fun showAccessibilityHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage(
                "This is required so the app can automatically press \"1\" after you enter your PIN.\n\n" +
                "Steps:\n" +
                "1. Tap OK below\n" +
                "2. Find \"USSD Auto\" in the list\n" +
                "3. Turn it ON\n" +
                "4. Confirm the permission\n" +
                "5. Come back to this app"
            )
            .setPositiveButton("Open Settings") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPhonePermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Phone Permission Needed")
            .setMessage(
                "The app needs Phone permission to dial the USSD code.\n\n" +
                "Without this permission the transaction cannot start."
            )
            .setPositiveButton("Grant Permission") { _, _ ->
                requestPhonePermission()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Find \"USSD Auto\" and turn it ON", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
}
