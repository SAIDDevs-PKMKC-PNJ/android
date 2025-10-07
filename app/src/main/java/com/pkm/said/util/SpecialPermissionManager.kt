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
import android.os.Handler
import android.os.Looper
import java.util.Date
import java.util.LinkedList

class SpecialPermissionManager(private val context: Context) {

    companion object {
        private const val TAG = "SpecialPermissions"
        const val REQUEST_CODE_BASIC_PERMISSIONS = 100
        const val REQUEST_CODE_BATTERY_OPTIMIZATION = 101
        const val REQUEST_CODE_OVERLAY_PERMISSION = 102
    }

    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val apiLevel: Int,
        val uiVersion: String? = null
    )

    data class PermissionInstruction(
        val title: String,
        val steps: List<String>,
        val settingsIntent: Intent? = null,
        val isCritical: Boolean = true,
        val estimatedTime: String = "2-3 minutes"
    )

    data class PermissionStatus(
        val name: String,
        val isGranted: Boolean,
        val isRequired: Boolean,
        val description: String,
        val settingsAction: String? = null
    )

    private val pendingPermissionRequests = LinkedList<PermissionStatus>()
    private var currentDialog: AlertDialog? = null

    fun getAllPermissionStatus(): List<PermissionStatus> {
        val permissions = mutableListOf<PermissionStatus>()

        // 1. BASIC ANDROID PERMISSIONS (yang diminta via requestPermissions)
        permissions.add(PermissionStatus(
            "RECORD_AUDIO",
            isRecordAudioGranted(),
            true, // CRITICAL untuk voice
            "Merekam suara untuk deteksi perintah voice",
            null
        ))

        // Android 13+ notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(PermissionStatus(
                "POST_NOTIFICATIONS",
                isNotificationPermissionGranted(),
                true, // CRITICAL untuk foreground service
                "Menampilkan notifikasi status voice activation",
                null
            ))
        }

        // 2. SPECIAL PERMISSIONS (yang perlu manual setup di settings)
        permissions.add(PermissionStatus(
            "BATTERY_OPTIMIZATION_IGNORED",
            isBatteryOptimizationIgnored(),
            true,
            "Mencegah Android mematikan app di background",
            null // Tidak menggunakan settings action langsung
        ))

        permissions.add(PermissionStatus(
            "AUTOSTART",
            isAutoStartEnabled(),
            true,
            "Auto-start setelah device restart (manufacturer specific)",
            getAutoStartIntent().action
        ))

        permissions.add(PermissionStatus(
            "BACKGROUND_ACTIVITY",
            isBackgroundActivityAllowed(),
            true,
            "Akses background activity untuk voice service",
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        ))

        // Optional permissions
        permissions.add(PermissionStatus(
            "SYSTEM_ALERT_WINDOW",
            canDrawOverlays(),
            false,
            "Menampilkan overlay/indikator voice activation",
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION
        ))

        return permissions
    }

    // === IMPLEMENTATION OF PERMISSION CHECKS ===

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
            true // Untuk Android < 13, notifikasi selalu granted
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Untuk Android < 6, tidak ada battery optimization
        }
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
        // Untuk beberapa device, kita bisa check melalui AppOpsManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                val mode = appOps?.checkOpNoThrow(
                    "android:auto_start",
                    android.os.Process.myUid(),
                    context.packageName
                )
                mode == AppOpsManager.MODE_ALLOWED
            } catch (e: Exception) {
                // Fallback ke shared preferences
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.getBoolean("autostart_user_enabled", false)
            }
        } else {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("autostart_user_enabled", false)
        }
    }

    private fun isBackgroundActivityAllowed(): Boolean {
        // Untuk Android 10+, check background activity restriction
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkForegroundServiceCapability()
        } else {
            true
        }
    }

    private fun checkForegroundServiceCapability(): Boolean {
        return try {
            // Check if app has foreground service permission in manifest
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            val permissions = packageInfo.requestedPermissions

            permissions?.any { it == Manifest.permission.FOREGROUND_SERVICE } == true
        } catch (e: Exception) {
            Log.w(TAG, "Error checking foreground service capability", e)
            true // Assume allowed
        }
    }

    private fun getAutoStartIntent(): Intent {
        return getManufacturerInstructions().settingsIntent ?: openAppSettingsIntent()
    }

    fun checkAndRequestAllPermissions(activity: AppCompatActivity) {
        val missingPermissions = getAllPermissionStatus().filter {
            !it.isGranted && it.isRequired
        }

        if (missingPermissions.isEmpty()) {
            showAllPermissionsGrantedDialog(activity)
            return
        }

        val sortedPermissions = missingPermissions.sortedBy { permission ->
            when (permission.name) {
                "RECORD_AUDIO", "POST_NOTIFICATIONS" -> 0 // Basic first
                else -> 1 // Special later
            }
        }

        // Setup queue untuk multiple permission requests
        pendingPermissionRequests.clear()
        pendingPermissionRequests.addAll(sortedPermissions)

        // Mulai process permission request
        processNextPermissionRequest(activity)
    }

    private fun processNextPermissionRequest(activity: AppCompatActivity) {
        // Close previous dialog jika ada
        currentDialog?.dismiss()

        if (pendingPermissionRequests.isEmpty()) {
            // Semua permissions sudah diproses
            showPermissionSummary(activity)
            return
        }

        val nextPermission = pendingPermissionRequests.poll()
        showPermissionRequestDialog(activity, nextPermission)
    }

    /**
     * Show dialog untuk setiap permission yang dibutuhkan
     */
    private fun showPermissionRequestDialog(activity: AppCompatActivity, permission: PermissionStatus) {
        val dialogConfig = getDialogConfigForPermission(permission)

        currentDialog = AlertDialog.Builder(activity)
            .setTitle(dialogConfig.title)
            .setMessage(dialogConfig.message)
            .setPositiveButton(dialogConfig.positiveButton) { _, _ ->
                handlePermissionAction(activity, permission)
            }
            .setNegativeButton("Nanti") { _, _ ->
                // Skip permission ini, lanjut ke berikutnya
                processNextPermissionRequest(activity)
            }
            .setNeutralButton("Info Detail") { _, _ ->
                showDetailedExplanation(activity, permission)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Konfigurasi dialog untuk setiap jenis permission
     */
    private data class DialogConfig(
        val title: String,
        val message: String,
        val positiveButton: String
    )

    private fun getDialogConfigForPermission(permission: PermissionStatus): DialogConfig {
        return when (permission.name) {
            "RECORD_AUDIO" -> DialogConfig(
                "🎤 Izin Akses Mikrofon",
                "SAID membutuhkan akses mikrofon untuk mendeteksi perintah suara \"Hello SAID\" dan perintah lainnya.\n\nTanpa izin ini, fitur voice activation tidak dapat berfungsi.",
                "Berikan Izin Mikrofon"
            )

            "POST_NOTIFICATIONS" -> DialogConfig(
                "🔔 Izin Notifikasi",
                "SAID perlu menampilkan notifikasi untuk menunjukkan status voice activation sedang aktif.\n\nNotifikasi ini penting untuk memastikan aplikasi tetap berjalan di background.",
                "Izinkan Notifikasi"
            )

            "BATTERY_OPTIMIZATION_IGNORED" -> DialogConfig(
                "🔋 Optimasi Baterai",
                "Agar SAID tetap aktif di background, perlu dimatikan optimasi baterai untuk aplikasi ini.\n\nTanpa ini, Android mungkin akan mematikan SAID secara otomatis.",
                "Pergi ke Settings"
            )

            "AUTOSTART" -> DialogConfig(
                "🚀 Auto-Start",
                "Izinkan SAID untuk auto-start setelah device restart.\n\nIni memastikan voice activation selalu siap digunakan.",
                "Setup Auto-Start"
            )

            "BACKGROUND_ACTIVITY" -> DialogConfig(
                "🔄 Aktivitas Background",
                "SAID perlu izin untuk berjalan di background agar dapat mendeteksi perintah suara kapan saja.",
                "Buka Settings"
            )

            "SYSTEM_ALERT_WINDOW" -> DialogConfig(
                "📱 Overlay Permission",
                "Izinkan SAID untuk menampilkan indikator visual ketika voice activation aktif.\n\nIni opsional tapi sangat disarankan.",
                "Izinkan Overlay"
            )

            else -> DialogConfig(
                "⚙️ Izin Diperlukan",
                "Izin tambahan diperlukan untuk fungsi lengkap SAID.",
                "Buka Settings"
            )
        }
    }

    private fun handlePermissionAction(activity: AppCompatActivity, permission: PermissionStatus) {
        when (permission.name) {
            "RECORD_AUDIO" -> {
                requestBasicPermissions(activity)
                return
            }

            "POST_NOTIFICATIONS" -> {
                requestBasicPermissions(activity)
                return
            }

            "BATTERY_OPTIMIZATION_IGNORED" -> {
                showBatteryOptimizationDialog(activity)
            }

            "AUTOSTART" -> {
                try {
                    val manufacturerIntent = getManufacturerInstructions().settingsIntent
                    activity.startActivity(manufacturerIntent ?: openAppSettingsIntent())
                } catch (e: Exception) {
                    openAppSettings(activity)
                }
            }

            "SYSTEM_ALERT_WINDOW" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        intent.data = Uri.parse("package:${activity.packageName}")
                        activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
                    } catch (e: Exception) {
                        openAppSettings(activity)
                    }
                }
            }

            else -> {
                openAppSettings(activity)
            }
        }

        // Set timeout untuk lanjut ke permission berikutnya
        if (permission.name !in listOf("RECORD_AUDIO", "POST_NOTIFICATIONS")) {
            Handler(Looper.getMainLooper()).postDelayed({
                processNextPermissionRequest(activity)
            }, 3000)
        }
    }

    /**
     * Handle battery optimization permission secara manual
     */
    private fun showBatteryOptimizationDialog(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle("🔋 Optimasi Baterai")
            .setMessage("Agar SAID tetap aktif di background, Anda perlu menonaktifkan optimasi baterai secara manual.\n\nTekan 'Buka Settings' lalu cari SAID dan pilih 'Don't optimize'.")
            .setPositiveButton("Buka Settings") { _, _ ->
                openBatteryOptimizationSettings(activity)
            }
            .setNegativeButton("Nanti") { _, _ ->
                processNextPermissionRequest(activity)
            }
            .setNeutralButton("Panduan Detail") { _, _ ->
                showBatteryOptimizationGuide(activity)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Buka halaman battery optimization settings
     */
    private fun openBatteryOptimizationSettings(activity: AppCompatActivity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Intent untuk app info page (dimana battery optimization settings biasanya ada)
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATION)
            } else {
                // Untuk Android < 6, buka app settings
                openAppSettings(activity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening battery optimization settings", e)
            openAppSettings(activity)
        }
    }

    /**
     * Tampilkan panduan detail untuk battery optimization
     */
    private fun showBatteryOptimizationGuide(activity: AppCompatActivity) {
        val deviceInfo = getDeviceInfo()
        val guide = when {
            deviceInfo.manufacturer.contains("xiaomi") -> getXiaomiBatteryGuide()
            deviceInfo.manufacturer.contains("samsung") -> getSamsungBatteryGuide()
            deviceInfo.manufacturer.contains("oppo") -> getOppoBatteryGuide()
            deviceInfo.manufacturer.contains("vivo") -> getVivoBatteryGuide()
            else -> getGenericBatteryGuide()
        }

        AlertDialog.Builder(activity)
            .setTitle("📖 Panduan Optimasi Baterai - ${deviceInfo.manufacturer.replaceFirstChar { it.uppercase() }}")
            .setMessage(guide)
            .setPositiveButton("Buka Settings") { _, _ ->
                openBatteryOptimizationSettings(activity)
            }
            .setNegativeButton("Mengerti") { _, _ ->
                showBatteryOptimizationDialog(activity)
            }
            .show()
    }

    private fun getXiaomiBatteryGuide(): String {
        return """Langkah-langkah untuk Xiaomi (MIUI):
    
1. Buka **Settings** → **Apps** → **SAID**
2. Pilih **Battery saver** 
3. Pilih **No restrictions**
4. Buka **Security app** → **Battery** 
5. Temukan **SAID** dan pilih **No restrictions**

Atau cari di settings: "Battery optimization" → "Don't optimize" """
    }

    private fun getSamsungBatteryGuide(): String {
        return """Langkah-langkah untuk Samsung:
    
1. Buka **Settings** → **Apps** → **SAID**
2. Pilih **Battery**
3. Pilih **Unrestricted**
4. Buka **Device care** → **Battery** 
5. **Background usage limits** → **Never sleeping apps**
6. Tambahkan **SAID** ke list

Cari juga: "Optimize battery usage" → "All apps" → "SAID" → "Don't optimize" """
    }

    private fun getOppoBatteryGuide(): String {
        return """Langkah-langkah untuk Oppo:
    
1. Buka **Security app** → **Permissions** 
2. **Startup manager** → Enable untuk SAID
3. **Battery** → **App battery management**
4. Pilih **SAID** → **Allow background activity**

Cari juga: "Battery optimization" → "SAID" → "Don't optimize" """
    }

    private fun getVivoBatteryGuide(): String {
        return """Langkah-langkah untuk Vivo:
    
1. Buka **Settings** → **More settings** 
2. **Applications** → **Autostart** → Enable SAID
3. **Battery** → **Background power consumption**
4. Enable **High background power consumption** untuk SAID

Cari: "Battery optimization" → "SAID" → "Not optimized" """
    }

    private fun getGenericBatteryGuide(): String {
        return """Langkah-langkah umum:
    
1. Buka **Settings** → **Apps** → **SAID**
2. Cari opsi **Battery** atau **Power management**
3. Pilih **Unrestricted** atau **Don't optimize**
4. Atau cari di settings: "Battery optimization"
5. Pilih **All apps** → Cari **SAID** → Pilih **Don't optimize**

Tips: Setiap brand Android memiliki menu yang sedikit berbeda, cari menu yang berkaitan dengan "Battery", "Power", atau "Optimization" """
    }

    /**
     * Dialog penjelasan detail untuk setiap permission
     */
    private fun showDetailedExplanation(activity: AppCompatActivity, permission: PermissionStatus) {
        val explanation = when (permission.name) {
            "RECORD_AUDIO" ->
                "• Mendengarkan perintah 'Hello SAID' untuk activation\n" +
                        "• Merekam pertanyaan dan perintah suara Anda\n" +
                        "• Audio diproses secara lokal untuk privasi\n" +
                        "• Tidak menyimpan rekaman suara"

            "BATTERY_OPTIMIZATION_IGNORED" ->
                "• Mencegah sistem Android menghentikan SAID\n" +
                        "• Memastikan voice activation selalu siap\n" +
                        "• Tidak menguras baterai - hanya aktif ketika diperlukan\n" +
                        "• Wajib untuk pengalaman tanpa hambatan"

            "AUTOSTART" ->
                "• Auto-start setelah restart/hidup ulang device\n" +
                        "• Tidak perlu manual buka app setelah reboot\n" +
                        "• Khusus untuk device Samsung, Xiaomi, Oppo, Vivo, dll\n" +
                        "• Penting untuk reliability jangka panjang"

            "BACKGROUND_ACTIVITY" ->
                "• Menjaga service voice detection aktif\n" +
                        "• Berjalan efisien di background\n" +
                        "• Minimal impact pada baterai\n" +
                        "• Essential untuk hands-free operation"

            else -> "Izin ini diperlukan untuk fungsi optimal SAID Voice Assistant."
        }

        AlertDialog.Builder(activity)
            .setTitle("ℹ️ Detail: ${permission.name}")
            .setMessage(explanation)
            .setPositiveButton("Mengerti") { _, _ ->
                showPermissionRequestDialog(activity, permission)
            }
            .show()
    }

    /**
     * Summary setelah semua permissions diproses
     */
    private fun showPermissionSummary(activity: AppCompatActivity) {
        val permissions = getAllPermissionStatus()
        val grantedCount = permissions.count { it.isGranted }
        val totalCount = permissions.size
        val missingPermissions = permissions.filter { !it.isGranted && it.isRequired }

        val summaryMessage = buildString {
            append("**Ringkasan Izin Aplikasi:**\n\n")
            append("✅ **Telah diizinkan:** $grantedCount/$totalCount\n\n")

            if (missingPermissions.isNotEmpty()) {
                append("⚠️ **Masih diperlukan:**\n")
                missingPermissions.forEach { permission ->
                    append("• ${permission.description}\n")
                }
                append("\nBeberapa fitur mungkin terbatas tanpa izin ini.")
            } else {
                append("🎉 **Semua izin telah diberikan!**\n\n")
                append("SAID sekarang siap digunakan dengan fitur lengkap.")
            }
        }

        AlertDialog.Builder(activity)
            .setTitle("📋 Status Izin")
            .setMessage(summaryMessage)
            .setPositiveButton("OK") { _, _ ->
                if (missingPermissions.isNotEmpty()) {
                    // Tawarkan untuk buka settings manual
                    showManualSetupOffer(activity)
                }
            }
            .setNegativeButton("Test Voice") { _, _ ->
                // Panggil callback atau function untuk test voice
            }
            .show()
    }

    /**
     * Tawarkan manual setup untuk permissions yang masih missing
     */
    private fun showManualSetupOffer(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle("🔧 Setup Manual Diperlukan")
            .setMessage("Beberapa izin membutuhkan setup manual di system settings. Mau dibantu dengan panduan khusus untuk device ${getDeviceInfo().manufacturer.replaceFirstChar { it.uppercase() }}?")
            .setPositiveButton("Ya, Pandu Saya") { _, _ ->
                showManufacturerPermissionGuide(activity)
            }
            .setNegativeButton("Nanti Saja") { _, _ ->
                // User memilih untuk setup nanti
            }
            .show()
    }

    /**
     * Dialog final ketika semua permissions granted
     */
    private fun showAllPermissionsGrantedDialog(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle("🎉 Siap Digunakan!")
            .setMessage("Semua izin yang diperlukan telah diberikan.\n\nSAID Voice Assistant sekarang siap membantu Anda dengan fitur:\n\n• 🎵 Voice Activation \"Hello SAID\"\n• 🔊 Pemutar Musik & Kontrol\n• 📱 Smart Assistant\n• 🔄 Background Operation")
            .setPositiveButton("Test Sekarang") { _, _ ->
                // Start voice test atau main functionality
            }
            .setIcon(android.R.drawable.ic_dialog_info)
            .show()
    }

    // Helper methods
    private fun openAppSettings(activity: AppCompatActivity) {
        try {
            activity.startActivity(openAppSettingsIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open app settings", e)
        }
    }

    private fun openAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Get detailed device information
     */
    fun getDeviceInfo(): DeviceInfo {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val uiVersion = detectUIVersion(manufacturer)

        return DeviceInfo(
            manufacturer = manufacturer,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            uiVersion = uiVersion
        )
    }

    private fun detectUIVersion(manufacturer: String): String? {
        return when {
            manufacturer.contains("xiaomi") -> detectMIUIVersion()
            manufacturer.contains("samsung") -> detectOneUIVersion()
            manufacturer.contains("oppo") -> detectColorOSVersion()
            manufacturer.contains("vivo") -> detectFuntouchOSVersion()
            manufacturer.contains("realme") -> detectRealmeUIVersion()
            manufacturer.contains("oneplus") -> detectOxygenOSVersion()
            else -> null
        }
    }

    private fun detectMIUIVersion(): String {
        // MIUI version detection
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java)
            val version = getMethod.invoke(systemProperties, "ro.miui.ui.version.name") as String?
            version ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun detectOneUIVersion(): String {
        // Samsung One UI version detection
        return try {
            when (Build.VERSION.SDK_INT) {
                in 34..Int.MAX_VALUE -> "One UI 7.x (Android 14+ - 15+)"
                in 33..33 -> "One UI 6.x"
                in 32..32 -> "One UI 5.x"
                in 31..31 -> "One UI 4.x"
                in 29..30 -> "One UI 3.x"
                else -> "One UI 2 or older"
            }
        } catch (e: Exception) {
            "One UI (Latest)"
        }
    }

    private fun detectColorOSVersion(): String {
        // ColorOS version detection
        return when (Build.VERSION.SDK_INT) {
            in 33..Int.MAX_VALUE -> "ColorOS 13+"
            in 31..32 -> "ColorOS 12+"
            in 30..30 -> "ColorOS 11+"
            else -> "ColorOS 10 or older"
        }
    }

    private fun detectFuntouchOSVersion(): String {
        // FuntouchOS version detection
        return when (Build.VERSION.SDK_INT) {
            in 33..Int.MAX_VALUE -> "FuntouchOS 13+"
            in 31..32 -> "FuntouchOS 12+"
            else -> "FuntouchOS 11 or older"
        }
    }

    private fun detectRealmeUIVersion(): String {
        // Realme UI version detection
        return when (Build.VERSION.SDK_INT) {
            in 33..Int.MAX_VALUE -> "Realme UI 4+"
            in 31..32 -> "Realme UI 3+"
            in 30..30 -> "Realme UI 2+"
            else -> "Realme UI 1 or older"
        }
    }

    private fun detectOxygenOSVersion(): String {
        // OxygenOS version detection
        return when (Build.VERSION.SDK_INT) {
            in 33..Int.MAX_VALUE -> "OxygenOS 13+"
            in 31..32 -> "OxygenOS 12+"
            in 30..30 -> "OxygenOS 11+"
            else -> "OxygenOS 10 or older"
        }
    }

    /**
     * Get manufacturer-specific instructions
     */
    fun getManufacturerInstructions(): PermissionInstruction {
        val deviceInfo = getDeviceInfo()

        return when {
            deviceInfo.manufacturer.contains("xiaomi") -> getXiaomiInstructions(deviceInfo)
            deviceInfo.manufacturer.contains("samsung") -> getSamsungInstructions(deviceInfo)
            deviceInfo.manufacturer.contains("oppo") -> getOppoInstructions(deviceInfo)
            deviceInfo.manufacturer.contains("vivo") -> getVivoInstructions(deviceInfo)
            deviceInfo.manufacturer.contains("realme") -> getRealmeInstructions(deviceInfo)
            deviceInfo.manufacturer.contains("oneplus") -> getOnePlusInstructions(deviceInfo)
            deviceInfo.manufacturer.contains("tecno") -> getTecnoInstructions()
            else -> getGenericInstructions()
        }
    }

    /**
     * XIAOMI (MIUI) Instructions
     */
    private fun getXiaomiInstructions(device: DeviceInfo): PermissionInstruction {
        val steps = mutableListOf<String>()

        // MIUI 14+ specific
        if (device.uiVersion?.contains("14") == true) {
            steps.addAll(listOf(
                "📱 Buka **Settings** → **Apps** → **SAID**",
                "⚙️ Tap **App permissions** → **Background autostart**",
                "✅ Enable **Background autostart** untuk SAID",
                "🔋 Buka **Security app** → **Boost speed** → **Settings cog**",
                "🚫 **Additional settings** → **Battery optimization** → **SAID** → **Don't optimize**",
                "⚡ **Battery saver** → **No restrictions** untuk SAID"
            ))
        } else {
            // MIUI 13 and below
            steps.addAll(listOf(
                "📱 Buka **Security app** → **Permissions** → **Autostart**",
                "✅ Enable **Autostart** untuk SAID",
                "🔋 **Battery saver** → **No restrictions** untuk SAID",
                "⚡ **Other permissions** → Enable semua yang diperlukan"
            ))
        }

        return PermissionInstruction(
            title = "🚀 MIUI ${device.uiVersion ?: "Auto-start"} Setup",
            steps = steps,
            settingsIntent = createXiaomiIntent(),
            estimatedTime = "3-4 minutes"
        )
    }

    /**
     * SAMSUNG Instructions
     */
    private fun getSamsungInstructions(device: DeviceInfo): PermissionInstruction {
        val steps = mutableListOf<String>()

        when {
            device.apiLevel >= 34 -> {
                steps.addAll(listOf(
                    "📱 **Settings** → **Apps** → **SAID**",
                    "⚡ **Battery** → Pilih **Unrestricted**",
                    "🔋 **Device Care** → **Battery** → **Background usage limits**",
                    "➕ **Never sleeping apps** → Add **SAID**",
                    "🚫 **Battery** → **More battery settings** → Turn off **Adaptive battery**"
                ))
            }
            device.apiLevel >= 33 -> { // Android 13+
                steps.addAll(listOf(
                    "📱 **Settings** → **Apps** → **SAID** → **Battery**",
                    "⚡ Pilih **Unrestricted**",
                    "🔋 **Battery and device care** → **Battery** → **Background usage limits**",
                    "➕ **Never sleeping apps** → Tambah **SAID**",
                    "⏰ **Special access** → **Alarms and Reminders** → Enable untuk SAID"
                ))
            }
            device.apiLevel >= 30 -> { // Android 11-12
                steps.addAll(listOf(
                    "📱 **Settings** → **Apps** → **SAID** → **Battery**",
                    "🔋 **Battery optimization** → **All apps** → **SAID** → **Don't optimize**",
                    "⚡ **Background usage limits** → **Never sleeping apps** → Tambah **SAID**"
                ))
            }
            else -> { // Android 10 and below
                steps.addAll(listOf(
                    "📱 **Settings** → **Device care** → **Battery** → **3-dot menu** → **Settings**",
                    "🚫 Uncheck **SAID** dari **Sleeping apps** dan **Auto-disable unused apps**",
                    "🔋 **App power monitor** → **Unmonitored apps** → Tambah **SAID**"
                ))
            }
        }

        return PermissionInstruction(
            title = "🔄 Samsung ${device.uiVersion ?: "One UI"} Setup",
            steps = steps,
            settingsIntent = createSamsungIntent(),
            estimatedTime = "2-3 minutes"
        )
    }

    /**
     * OPPO Instructions
     */
    private fun getOppoInstructions(device: DeviceInfo): PermissionInstruction {
        val steps = listOf(
            "📱 Buka **Security app** → **Permissions** → **Startup manager**",
            "✅ Enable **Auto-launch** untuk SAID",
            "🔋 **App management** → **SAID** → **Battery usage** → **Run in background**",
            "⚡ **Battery** → **App battery management** → **SAID** → **Allow all**"
        )

        return PermissionInstruction(
            title = "⚡ ColorOS ${device.uiVersion ?: "Auto-start"} Setup",
            steps = steps,
            settingsIntent = createOppoIntent(),
            estimatedTime = "3 minutes"
        )
    }

    /**
     * VIVO Instructions
     */
    private fun getVivoInstructions(device: DeviceInfo): PermissionInstruction {
        val steps = listOf(
            "📱 **Settings** → **More settings** → **Applications** → **Autostart**",
            "✅ Enable **Autostart** untuk SAID",
            "🔋 **Battery** → **Background power consumption management**",
            "⚡ Enable **High background power consumption** untuk SAID",
            "🚫 **Battery optimization** → **SAID** → **Not optimized**"
        )

        return PermissionInstruction(
            title = "🔄 FuntouchOS ${device.uiVersion ?: "Auto-start"} Setup",
            steps = steps,
            settingsIntent = createVivoIntent(),
            estimatedTime = "2-3 minutes"
        )
    }

    /**
     * REALME Instructions
     */
    private fun getRealmeInstructions(device: DeviceInfo): PermissionInstruction {
        val steps = listOf(
            "📱 **Settings** → **Battery** → **App battery management**",
            "✅ Pilih **SAID** → **Allow all** permissions",
            "🚀 **Allow auto-launch**, **Allow foreground activity**, **Allow background activity**",
            "🔋 **Optimize battery use** → Set ke **Off** untuk SAID",
            "⚡ **Power saving settings** → **App battery management** → **SAID** → **Allow all**"
        )

        return PermissionInstruction(
            title = "⚡ Realme UI ${device.uiVersion ?: "Battery"} Setup",
            steps = steps,
            settingsIntent = createRealmeIntent(),
            estimatedTime = "2-3 minutes"
        )
    }

    /**
     * ONEPLUS Instructions
     */
    private fun getOnePlusInstructions(device: DeviceInfo): PermissionInstruction {
        val steps = mutableListOf(
            "📱 **Settings** → **Battery** → **Battery optimization**",
            "📝 **All apps** → **SAID** → **Don't optimize**",
            "🚀 **App Auto-Launch** → Enable untuk SAID",
            "📌 **Lock SAID** di Recent Apps (swipe down pada app di recent apps)"
        )

        // Additional steps for OnePlus 6 and newer
        if (device.model.contains("6") || device.model.contains("7") ||
            device.model.contains("8") || device.model.contains("9") ||
            device.model.contains("10") || device.model.contains("11")) {
            steps.addAll(listOf(
                "⚙️ **Battery optimization** → **3-dot menu** → **Advanced optimization**",
                "🚫 **Disable Deep optimization** dan **Sleep standby optimization**"
            ))
        }

        return PermissionInstruction(
            title = "🔋 OxygenOS ${device.uiVersion ?: "Battery"} Setup",
            steps = steps,
            settingsIntent = createOnePlusIntent(),
            estimatedTime = "3-4 minutes"
        )
    }

    /**
     * TECNO Instructions
     */
    private fun getTecnoInstructions(): PermissionInstruction {
        val steps = listOf(
            "📱 **Settings** → **Battery Lab** → **Battery Saving Settings**",
            "🚫 **Disable Power Saving Management** untuk SAID",
            "🔧 Buka **Phone Master** → **Toolbox** → **Auto-start management**",
            "✅ **Allow SAID** to run in background",
            "⚡ **Battery optimization** → **SAID** → **Don't optimize**"
        )

        return PermissionInstruction(
            title = "🔧 Tecno Battery Setup",
            steps = steps,
            settingsIntent = createTecnoIntent(),
            estimatedTime = "2-3 minutes"
        )
    }

    /**
     * GENERIC Instructions for other devices
     */
    private fun getGenericInstructions(): PermissionInstruction {
        val steps = listOf(
            "📱 **Settings** → **Apps** → **SAID** → **Battery**",
            "⚡ Pilih **Unrestricted** atau **Don't optimize**",
            "🔋 **Battery optimization** → **All apps** → **SAID** → **Don't optimize**",
            "🚀 Cari **Auto-start** atau **Background activity** di settings",
            "✅ Enable semua permission background untuk SAID"
        )

        return PermissionInstruction(
            title = "⚡ General Device Setup",
            steps = steps,
            settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
            estimatedTime = "2-3 minutes"
        )
    }

    /**
     * Create manufacturer-specific intents
     */
    private fun createXiaomiIntent(): Intent {
        return try {
            Intent("miui.intent.action.OP_AUTO_START").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
        } catch (e: Exception) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    private fun createSamsungIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    private fun createOppoIntent(): Intent {
        return try {
            Intent().apply {
                setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
            }
        } catch (e: Exception) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    private fun createVivoIntent(): Intent {
        return try {
            Intent().apply {
                setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
        } catch (e: Exception) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    private fun createRealmeIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    private fun createOnePlusIntent(): Intent {
        return try {
            Intent().apply {
                setClassName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            }
        } catch (e: Exception) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    private fun createTecnoIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * request permission digunakan sebagai handler ketika ada permission yang belum diterapkan
     */
    fun requestBasicPermissions(activity: AppCompatActivity) {
        val permissionsToRequest = mutableListOf<String>()

        if (!isRecordAudioGranted()) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted()) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            activity.requestPermissions(
                permissionsToRequest.toTypedArray(),
                REQUEST_CODE_BASIC_PERMISSIONS
            )
        }
    }

    fun requestSpecialPermissions(activity: AppCompatActivity) {
        val missingPermissions = getAllPermissionStatus().filter {
            !it.isGranted && it.isRequired
        }

        if (missingPermissions.isNotEmpty()) {
            showManufacturerPermissionGuide(activity)
        } else {
            Log.d(TAG, "✅ All special permissions granted")
        }
    }

    /**
     * Show manufacturer-specific permission guide
     */
    fun showManufacturerPermissionGuide(activity: AppCompatActivity) {
        val deviceInfo = getDeviceInfo()
        val instructions = getManufacturerInstructions()

        val message = buildString {
            append("📱 **Device**: ${deviceInfo.manufacturer.replaceFirstChar { it.uppercase() }} ${deviceInfo.model}\n")
            append("🤖 **Android**: ${deviceInfo.androidVersion} (API ${deviceInfo.apiLevel})\n")
            append("🎨 **UI**: ${deviceInfo.uiVersion ?: "Standard"}\n\n")
            append("**${instructions.title}**\n\n")
            append("⏱️ **Estimated time**: ${instructions.estimatedTime}\n\n")
            append("**Follow these steps carefully:**\n")
            instructions.steps.forEachIndexed { index, step ->
                append("${index + 1}. $step\n")
            }
            append("\n💡 **Tip**: Setelah selesai, kembali ke app dan test voice command!")
        }

        AlertDialog.Builder(activity)
            .setTitle("🔧 Optimize SAID for ${deviceInfo.manufacturer.replaceFirstChar { it.uppercase() }}")
            .setMessage(message)
            .setPositiveButton("📋 Open Settings") { _, _ ->
                try {
                    activity.startActivity(instructions.settingsIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open manufacturer settings", e)
                    // Fallback to general settings
                    openAppSettings(activity)
                }
            }
            .setNegativeButton("🧑‍💻 Manual Setup") { _, _ ->
                showManualSetupInstructions(activity, instructions)
            }
            .setNeutralButton("📊 Device Report") { _, _ ->
                showDeviceReport(activity)
            }
            .setCancelable(false)
            .show()
    }

    private fun showManualSetupInstructions(activity: AppCompatActivity, instructions: PermissionInstruction) {
        val detailedMessage = buildString {
            append("**Manual Setup Instructions**\n\n")
            append("Jika tombol 'Open Settings' tidak bekerja, ikuti langkah manual:\n\n")
            instructions.steps.forEachIndexed { index, step ->
                append("${index + 1}. $step\n")
            }
            append("\n**Cara akses settings manual:**\n")
            append("1. Buka **Settings** (Pengaturan) di device Anda\n")
            append("2. Cari **Apps** (Aplikasi) atau **Battery** (Baterai)\n")
            append("3. Temukan **SAID** dalam list aplikasi\n")
            append("4. Ikuti langkah-langkah di atas\n")
        }

        AlertDialog.Builder(activity)
            .setTitle("📖 Manual Setup Guide")
            .setMessage(detailedMessage)
            .setPositiveButton("OK") { _, _ ->
                showManufacturerPermissionGuide(activity) // Kembali ke dialog utama
            }
            .setNegativeButton("Buka Settings Umum") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            }
            .show()
    }

    private fun showDeviceReport(activity: AppCompatActivity) {
        val deviceInfo = getDeviceInfo()
        val report = generatePermissionReport()

        AlertDialog.Builder(activity)
            .setTitle("📊 Device Compatibility Report")
            .setMessage(report)
            .setPositiveButton("Share Report") { _, _ ->
                shareDeviceReport(activity, report)
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun shareDeviceReport(activity: AppCompatActivity, report: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "SAID Device Compatibility Report")
                putExtra(Intent.EXTRA_TEXT, report)
            }
            activity.startActivity(Intent.createChooser(shareIntent, "Share Device Report"))
        } catch (e: Exception) {
            Log.e(TAG, "Could not share report", e)
        }
    }

    /**
     * Enhanced permission report with manufacturer info
     */
    fun generatePermissionReport(): String {
        val deviceInfo = getDeviceInfo()
        val permissions = getAllPermissionStatus()
        val granted = permissions.count { it.isGranted }
        val total = permissions.size

        val report = StringBuilder()
        report.appendLine("📊 SAID Device Compatibility Report")
        report.appendLine("Generated: ${Date()}")
        report.appendLine()
        report.appendLine("📱 Device Information:")
        report.appendLine("Manufacturer: ${deviceInfo.manufacturer.replaceFirstChar { it.uppercase() }}")
        report.appendLine("Model: ${deviceInfo.model}")
        report.appendLine("Android: ${deviceInfo.androidVersion} (API ${deviceInfo.apiLevel})")
        report.appendLine("UI Version: ${deviceInfo.uiVersion ?: "Not detected"}")
        report.appendLine()
        report.appendLine("📈 Permission Summary:")
        report.appendLine("Granted: $granted/$total")
        report.appendLine("Status: ${if (granted == total) "✅ OPTIMAL" else "⚠️ NEEDS SETUP"}")
        report.appendLine()
        report.appendLine("🔧 Recommended Actions:")
        report.appendLine(getManufacturerInstructions().steps.joinToString("\n") { "• $it" })

        return report.toString()
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?, activity: AppCompatActivity) {
        when (requestCode) {
            REQUEST_CODE_BATTERY_OPTIMIZATION -> {
                Log.d(TAG, "User kembali dari battery optimization settings")
                // Beri waktu untuk system update settings
                Handler(Looper.getMainLooper()).postDelayed({
                    val isIgnored = isBatteryOptimizationIgnored()
                    Log.d(TAG, "Battery optimization status: ${if (isIgnored) "IGNORED" else "OPTIMIZED"}")

                    // Lanjut ke permission berikutnya
                    processNextPermissionRequest(activity)
                }, 1000)
            }
            REQUEST_CODE_OVERLAY_PERMISSION -> {
                Log.d(TAG, "User kembali dari overlay permission settings")
                Handler(Looper.getMainLooper()).postDelayed({
                    processNextPermissionRequest(activity)
                }, 500)
            }
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray, activity: AppCompatActivity) {
        if (requestCode == REQUEST_CODE_BASIC_PERMISSIONS) {
            permissions.forEachIndexed { index, permission ->
                val isGranted = grantResults[index] == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Permission $permission: ${if (isGranted) "GRANTED" else "DENIED"}")
            }
            Handler(Looper.getMainLooper()).postDelayed({
                processNextPermissionRequest(activity)
            }, 1000)
        }
    }
}