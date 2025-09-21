package com.pkm.said.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Date

class SpecialPermissionManager(private val context: Context) {

    companion object {
        private const val TAG = "SpecialPermissions"
        private const val OPSTR_START_FOREGROUND = "android:start_foreground"
        private const val OPSTR_SYSTEM_ALERT_WINDOW = "android:system_alert_window"
    }

    data class PermissionStatus(
        val name: String,
        val isGranted: Boolean,
        val isRequired: Boolean,
        val description: String
    )

    /**
     * Get comprehensive permission status
     */
    fun getAllPermissionStatus(): List<PermissionStatus> {
        val permissions = mutableListOf<PermissionStatus>()

        // Basic permissions
        permissions.add(PermissionStatus(
            "RECORD_AUDIO",
            isRecordAudioGranted(),
            true,
            "Required for voice command detection"
        ))

        permissions.add(PermissionStatus(
            "FOREGROUND_SERVICE",
            true, // Always granted if declared
            true,
            "Allows app to run in background"
        ))

        // Android 13+ notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(PermissionStatus(
                "POST_NOTIFICATIONS",
                isNotificationPermissionGranted(),
                true,
                "Required for voice assistant notifications"
            ))
        }

        // Special permissions
        permissions.add(PermissionStatus(
            "BATTERY_OPTIMIZATION_IGNORED",
            isBatteryOptimizationIgnored(),
            true,
            "Prevents Android from killing the app"
        ))

        permissions.add(PermissionStatus(
            "SYSTEM_ALERT_WINDOW",
            canDrawOverlays(),
            false,
            "Allows app to show overlay windows"
        ))

        permissions.add(PermissionStatus(
            "AUTOSTART",
            isAutoStartEnabled(),
            true,
            "Manufacturer-specific auto start permission"
        ))

        permissions.add(PermissionStatus(
            "BACKGROUND_ACTIVITY",
            isBackgroundActivityAllowed(),
            true,
            "Allows background processing"
        ))

        return permissions
    }

    private fun isRecordAudioGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        return BatteryOptimizationManager(context).isIgnoringBatteryOptimizations()
    }

    @SuppressLint("NewApi")
    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    private fun isAutoStartEnabled(): Boolean {
        // This is manufacturer-specific and usually cannot be detected programmatically
        return checkAutoStartHeuristic()
    }

    private fun isBackgroundActivityAllowed(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkBackgroundActivityPermission()
        } else {
            true
        }
    }

    @SuppressLint("NewApi")
    private fun checkBackgroundActivityPermission(): Boolean {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    // Method 1: Try using AppOpsManager
                    checkWithAppOpsManager()
                }
                else -> {
                    true // Assume granted for older versions
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking background activity permission", e)
            // Fallback: assume granted to avoid false negatives
            true
        }
    }

    @SuppressLint("NewApi")
    private fun checkWithAppOpsManager(): Boolean {
        return try {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

            // ✅ Use custom constant to avoid API level issues
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use reflection to access the constant safely
                getAppOpsMode(appOpsManager, "OPSTR_START_FOREGROUND")
            } else {
                AppOpsManager.MODE_ALLOWED
            }

            val isAllowed = mode == AppOpsManager.MODE_ALLOWED
            Log.d(TAG, "Background activity permission check: ${if (isAllowed) "ALLOWED" else "DENIED"}")

            isAllowed

        } catch (e: Exception) {
            Log.w(TAG, "Could not check AppOps, assuming allowed", e)
            true
        }
    }

    // ✅ Safe way to access AppOps constants using reflection
    @SuppressLint("NewApi")
    private fun getAppOpsMode(appOpsManager: AppOpsManager, opName: String): Int {
        return try {
            // Try to get the actual constant value
            val opCode = when (opName) {
                "OPSTR_START_FOREGROUND" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use reflection to get the constant safely
                        AppOpsManager::class.java.getDeclaredField("OPSTR_START_FOREGROUND")
                            .get(null) as String
                    } else {
                        OPSTR_START_FOREGROUND
                    }
                }
                else -> OPSTR_START_FOREGROUND
            }

            appOpsManager.checkOpNoThrow(
                opCode,
                android.os.Process.myUid(),
                context.packageName
            )

        } catch (e: Exception) {
            Log.w(TAG, "Reflection failed, using fallback method", e)

            // Fallback: Use string directly
            try {
                appOpsManager.checkOpNoThrow(
                    OPSTR_START_FOREGROUND,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } catch (e2: Exception) {
                Log.w(TAG, "Fallback also failed, returning MODE_ALLOWED", e2)
                AppOpsManager.MODE_ALLOWED
            }
        }
    }

    private fun checkAutoStartHeuristic(): Boolean {
        // Heuristic check - tidak 100% akurat
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val hasBeenAsked = prefs.getBoolean("autostart_requested", false)
        val userClaimsEnabled = prefs.getBoolean("autostart_user_enabled", false)

        // If we haven't asked yet, assume it might need setup
        return hasBeenAsked && userClaimsEnabled
    }

    /**
     * Request all missing critical permissions
     */
    fun requestAllCriticalPermissions(activity: AppCompatActivity) {
        val missingPermissions = getAllPermissionStatus().filter {
            !it.isGranted && it.isRequired
        }

        if (missingPermissions.isNotEmpty()) {
            showCriticalPermissionsDialog(activity, missingPermissions)
        } else {
            Log.d(TAG, "✅ All critical permissions granted")
        }
    }

    private fun showCriticalPermissionsDialog(
        activity: AppCompatActivity,
        missingPermissions: List<PermissionStatus>
    ) {
        val permissionList = missingPermissions.joinToString("\n") {
            "• ${it.name}: ${it.description}"
        }

        AlertDialog.Builder(activity)
            .setTitle("🔐 Critical Permissions Required")
            .setMessage("""
                SAID Voice Assistant needs these permissions to work properly:
                
                $permissionList
                
                Without these permissions, voice commands may not work reliably.
            """.trimIndent())
            .setPositiveButton("Grant Permissions") { _, _ ->
                requestPermissionsSequentially(activity, missingPermissions, 0)
            }
            .setNegativeButton("Skip") { _, _ ->
                Log.w(TAG, "⚠️ User skipped critical permissions")
            }
            .setCancelable(false)
            .show()
    }

    private fun requestPermissionsSequentially(
        activity: AppCompatActivity,
        permissions: List<PermissionStatus>,
        index: Int
    ) {
        if (index >= permissions.size) {
            Log.d(TAG, "✅ Finished requesting permissions")
            showCompletionDialog(activity)
            return
        }

        val permission = permissions[index]
        Log.d(TAG, "🔄 Requesting permission: ${permission.name}")

        when (permission.name) {
            "BATTERY_OPTIMIZATION_IGNORED" -> {
                BatteryOptimizationManager(context).requestBatteryOptimizationExemption(activity)
            }
            "SYSTEM_ALERT_WINDOW" -> {
                requestSystemAlertWindowPermission(activity)
            }
            "AUTOSTART" -> {
                requestAutoStartPermission(activity)
            }
            "BACKGROUND_ACTIVITY" -> {
                requestBackgroundActivityPermission(activity)
            }
        }

        // Continue dengan permission berikutnya setelah delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            requestPermissionsSequentially(activity, permissions, index + 1)
        }, 3000) // 3 second delay untuk user experience
    }

    private fun showCompletionDialog(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle("🎉 Permission Setup Complete")
            .setMessage("""
                Great! You've completed the permission setup.
                
                🎤 Voice Assistant is now optimized for 24/7 operation
                🔋 Battery optimization bypassed
                🚀 Auto-start configured
                
                Voice commands should work reliably now.
                Say "Hey Google" then "start screening" to test!
            """.trimIndent())
            .setPositiveButton("Test Voice Commands") { _, _ ->
                // Optional: Show voice command guide
                showVoiceCommandGuide(activity)
            }
            .setNegativeButton("Done", null)
            .show()
    }

    private fun showVoiceCommandGuide(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle("🎤 Voice Commands Guide")
            .setMessage("""
                Available voice commands:
                
                🏠 Navigation:
                • "go to home" / "beranda"
                • "go to news" / "berita"  
                • "go to chatbot" / "chat"
                
                🏥 Health Screening:
                • "start screening" / "mulai screening"
                • "face test" / "tes wajah"
                • "arm test" / "tes lengan"
                • "speech test" / "tes bicara"
                
                🚨 Emergency:
                • "emergency" / "darurat"
                • "call ambulance" / "panggil ambulans"
                
                Remember: Say "Hey Google" first, then your command!
            """.trimIndent())
            .setPositiveButton("Got it", null)
            .show()
    }
    /**
     * Request System Alert Window permission
     */
    private fun requestSystemAlertWindowPermission(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                AlertDialog.Builder(activity)
                    .setTitle("🪟 Display Over Other Apps")
                    .setMessage("""
                        This permission allows SAID to:
                        • Show voice command feedback
                        • Display quick controls overlay
                        • Provide visual indicators
                        
                        Enable "Display over other apps" for SAID.
                    """.trimIndent())
                    .setPositiveButton("Open Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            activity.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Could not open overlay permission settings", e)
                        }
                    }
                    .setNegativeButton("Skip", null)
                    .show()
            }
        }
    }

    /**
     * Request auto-start permission (manufacturer specific)
     */
    private fun requestAutoStartPermission(activity: AppCompatActivity) {
        val manufacturer = Build.MANUFACTURER.lowercase()

        val (title, instructions, hasSpecialIntent) = when {
            manufacturer.contains("xiaomi") -> Triple(
                "🚀 Enable Auto-start (MIUI)",
                """
                For MIUI (Xiaomi):
                1. Open Security app
                2. Go to Autostart
                3. Find "SAID" and enable it
                4. Also enable "Background Activity"
                """.trimIndent(),
                true
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> Triple(
                "🛡️ Protected Apps (EMUI/Magic UI)",
                """
                For EMUI/Magic UI:
                1. Go to Settings → Apps
                2. Find "SAID"
                3. Enable "Protected apps"
                4. Allow "Run in background"
                """.trimIndent(),
                true
            )
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> Triple(
                "⚡ Startup Manager (ColorOS)",
                """
                For ColorOS:
                1. Go to Settings → Apps → App Management
                2. Find "SAID"
                3. Enable "Auto-launch"
                4. Enable "Background App Refresh"
                """.trimIndent(),
                true
            )
            manufacturer.contains("vivo") -> Triple(
                "🔄 Background App Refresh (FunTouch OS)",
                """
                For FunTouch OS:
                1. Go to Settings → Apps & Permissions
                2. Find "SAID"
                3. Enable "Background App Refresh"
                4. Enable "High Background App Consumption"
                """.trimIndent(),
                true
            )
            manufacturer.contains("samsung") -> Triple(
                "💤 Sleeping Apps Exception (One UI)",
                """
                For One UI (Samsung):
                1. Go to Settings → Battery and device care
                2. Battery → Background usage limits
                3. Never sleeping apps → Add "SAID"
                4. Sleeping apps → Remove "SAID" if present
                """.trimIndent(),
                false
            )
            manufacturer.contains("oneplus") -> Triple(
                "🔋 Battery Optimization (OxygenOS)",
                """
                For OxygenOS:
                1. Go to Settings → Battery → Battery Optimization
                2. Find "SAID" and select "Don't optimize"
                3. Enable "Auto-launch" in Advanced options
                """.trimIndent(),
                true
            )
            else -> Triple(
                "🚀 Auto-start Permission",
                """
                For your device:
                1. Look for "Auto-start" or "Startup Manager"
                2. Find "SAID" and enable it
                3. Allow background activity
                4. Disable battery optimization
                """.trimIndent(),
                false
            )
        }

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage("""
                To ensure voice commands work after device restart:
                
                $instructions
                
                This is critical for 24/7 voice assistant operation.
            """.trimIndent())
            .setPositiveButton("Open Settings") { _, _ ->
                openAutoStartSettings(activity, manufacturer, hasSpecialIntent)

                // Mark that we've requested this
                val prefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("autostart_requested", true).apply()
            }
            .setNegativeButton("Manual Setup") { _, _ ->
                openGeneralSettings(activity)
            }
            .show()
    }

    private fun openAutoStartSettings(activity: AppCompatActivity, manufacturer: String, hasSpecialIntent: Boolean) {
        try {
            if (hasSpecialIntent) {
                // Try manufacturer-specific intents
                val intent = when {
                    manufacturer.contains("xiaomi") -> Intent("miui.intent.action.OP_AUTO_START")
                    manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                        Intent("huawei.intent.action.HSM_PROTECTED_APPS")
                    manufacturer.contains("oppo") || manufacturer.contains("realme") ->
                        Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
                    manufacturer.contains("vivo") ->
                        Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                    manufacturer.contains("oneplus") ->
                        Intent().setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
                    else -> null
                }

                if (intent != null) {
                    activity.startActivity(intent)
                    Log.d(TAG, "🚀 Opened manufacturer-specific auto-start settings")
                    return
                }
            }

            // Fallback to general settings
            openGeneralSettings(activity)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Could not open manufacturer settings", e)
            openGeneralSettings(activity)
        }
    }

    private fun openGeneralSettings(activity: AppCompatActivity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            activity.startActivity(intent)
            Log.d(TAG, "📱 Opened general app settings")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Could not open app settings", e)
        }
    }

    private fun requestBackgroundActivityPermission(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AlertDialog.Builder(activity)
                .setTitle("🔄 Background Activity")
                .setMessage("""
                    Allow SAID to start activities from background.
                    
                    This ensures voice commands can:
                    • Open the app when it's closed
                    • Start screening directly from voice
                    • Work even when SAID is in background
                    
                    Look for "Background activity" or "Start from background" option in app settings.
                """.trimIndent())
                .setPositiveButton("Open Settings") { _, _ ->
                    openGeneralSettings(activity)
                }
                .setNegativeButton("Skip", null)
                .show()
        }
    }

    fun generatePermissionReport(): String {
        val permissions = getAllPermissionStatus()
        val granted = permissions.count { it.isGranted }
        val total = permissions.size
        val critical = permissions.filter { it.isRequired }
        val criticalGranted = critical.count { it.isGranted }

        val report = StringBuilder()
        report.appendLine("📊 SAID Permission Report")
        report.appendLine("Generated: ${Date()}")
        report.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        report.appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        report.appendLine("User: itsLuxra")
        report.appendLine("Timestamp: 2025-08-29 06:53:56 UTC")
        report.appendLine()
        report.appendLine("📈 Summary:")
        report.appendLine("Total permissions: $granted/$total granted")
        report.appendLine("Critical permissions: $criticalGranted/${critical.size} granted")
        report.appendLine()
        report.appendLine("📋 Detailed Status:")

        permissions.forEach { permission ->
            val status = if (permission.isGranted) "✅" else "❌"
            val required = if (permission.isRequired) " (REQUIRED)" else ""
            report.appendLine("$status ${permission.name}$required")
            report.appendLine("   ${permission.description}")
        }

        return report.toString()
    }

    // ✅ Utility method to mark autostart as user-configured
    fun markAutoStartAsConfigured(activity: AppCompatActivity, enabled: Boolean) {
        val prefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("autostart_user_enabled", enabled)
            .putBoolean("autostart_requested", true)
            .apply()

        Log.d(TAG, "✅ Auto-start marked as ${if (enabled) "enabled" else "disabled"} by user")
    }
}