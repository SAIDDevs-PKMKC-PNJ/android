package com.pkm.said.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class BatteryOptimizationManager(private val context: Context) {

    companion object {
        private const val TAG = "BatteryOptimization"
    }

    /**
     * Check if app is whitelisted from battery optimization
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            Log.d(TAG, if (isIgnoring) "✅ App is exempted from battery optimization"
            else "❌ App is subject to battery optimization")
            isIgnoring
        } else {
            true // Pre-Android 6.0 doesn't have doze mode
        }
    }

    /**
     * Request battery optimization exemption dengan user-friendly dialog
     */
    fun requestBatteryOptimizationExemption(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations()) {
                showBatteryOptimizationDialog(activity)
            } else {
                Log.d(TAG, "✅ Already exempted from battery optimization")
            }
        }
    }

    private fun showBatteryOptimizationDialog(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle("🔋 Battery Optimization Required")
            .setMessage("""
                For voice commands to work 24/7, SAID needs to:
                
                📱 Run in background continuously
                🎤 Listen for voice commands
                ⚡ Bypass battery optimization
                
                This ensures voice assistant works even when:
                • Screen is off
                • App is in background
                • Device is in power saving mode
                
                Tap 'Allow' in the next screen to exempt SAID.
            """.trimIndent())
            .setPositiveButton("Open Settings") { _, _ ->
                openBatteryOptimizationSettings(activity)
            }
            .setNegativeButton("Skip") { _, _ ->
                Log.w(TAG, "⚠️ User skipped battery optimization exemption")
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Open battery optimization settings
     */
    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings(activity: AppCompatActivity) {
        try {
            // Method 1: Direct request (Most reliable)
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${context.packageName}")
            activity.startActivity(intent)
            Log.d(TAG, "🔋 Opened battery optimization request dialog")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Direct request failed, trying alternative", e)
            openAlternativeBatterySettings(activity)
        }
    }

    /**
     * Alternative method jika direct request gagal
     */
    private fun openAlternativeBatterySettings(activity: AppCompatActivity) {
        try {
            // Method 2: General battery optimization settings
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            activity.startActivity(intent)
            Log.d(TAG, "🔋 Opened general battery optimization settings")

            // Show guidance
            showManualInstructions(activity)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Alternative method failed, opening app settings", e)
            openAppSettings(activity)
        }
    }

    /**
     * Fallback: Open app settings
     */
    private fun openAppSettings(activity: AppCompatActivity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            activity.startActivity(intent)
            Log.d(TAG, "📱 Opened app settings")

            showManualInstructions(activity)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Could not open any settings", e)
        }
    }

    /**
     * Show manual instructions for different manufacturers
     */
    private fun showManualInstructions(activity: AppCompatActivity) {
        val manufacturer = Build.MANUFACTURER.lowercase()

        val instructions = when {
            manufacturer.contains("xiaomi") -> """
                🔹 Find "SAID" in the list
                🔹 Tap on it
                🔹 Select "No restrictions"
                🔹 Enable "Autostart"
            """.trimIndent()

            manufacturer.contains("huawei") || manufacturer.contains("honor") -> """
                🔹 Find "SAID" in the list
                🔹 Tap on it  
                🔹 Select "Allow"
                🔹 Enable "Protected apps"
            """.trimIndent()

            manufacturer.contains("oppo") || manufacturer.contains("realme") -> """
                🔹 Find "SAID" in the list
                🔹 Toggle OFF battery optimization
                🔹 Enable "Startup Manager"
            """.trimIndent()

            manufacturer.contains("samsung") -> """
                🔹 Find "SAID" in the list
                🔹 Select "Optimize battery usage"
                🔹 Turn OFF optimization for SAID
                🔹 Add to "Sleeping apps" exceptions
            """.trimIndent()

            manufacturer.contains("oneplus") -> """
                🔹 Find "SAID" in the list
                🔹 Select "Don't optimize"
                🔹 Enable "Auto-launch"
            """.trimIndent()

            else -> """
                🔹 Find "SAID" in the list
                🔹 Disable battery optimization
                🔹 Allow background activity
                🔹 Enable autostart (if available)
            """.trimIndent()
        }

        AlertDialog.Builder(activity)
            .setTitle("📱 Manual Setup for ${Build.MANUFACTURER}")
            .setMessage("Please follow these steps:\n\n$instructions")
            .setPositiveButton("Got it", null)
            .show()
    }

    /**
     * Check specific manufacturer battery optimization status
     */
    fun checkManufacturerSpecificOptimizations(): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()

        try {
            // Xiaomi MIUI
            if (Build.MANUFACTURER.lowercase().contains("xiaomi")) {
                results["miui_autostart"] = checkMIUIAutostart()
                results["miui_background_activity"] = checkMIUIBackgroundActivity()
            }

            // Samsung
            if (Build.MANUFACTURER.lowercase().contains("samsung")) {
                results["samsung_sleeping_apps"] = checkSamsungSleepingApps()
            }

            // Huawei/Honor
            if (Build.MANUFACTURER.lowercase().contains("huawei") ||
                Build.MANUFACTURER.lowercase().contains("honor")) {
                results["huawei_protected_apps"] = checkHuaweiProtectedApps()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking manufacturer optimizations", e)
        }

        return results
    }

    private fun checkMIUIAutostart(): Boolean {
        // Implementation untuk check MIUI autostart status
        // Note: Ini memerlukan root access atau tidak reliable
        return false // Placeholder
    }

    private fun checkMIUIBackgroundActivity(): Boolean {
        // Implementation untuk check MIUI background activity
        return false // Placeholder
    }

    private fun checkSamsungSleepingApps(): Boolean {
        // Implementation untuk check Samsung sleeping apps
        return false // Placeholder
    }

    private fun checkHuaweiProtectedApps(): Boolean {
        // Implementation untuk check Huawei protected apps
        return false // Placeholder
    }
}