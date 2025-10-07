package com.pkm.said

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.auth.FirebaseAuth
import com.pkm.said.databinding.ActivityMainBinding
import com.pkm.said.screening.ScreeningActivity
import com.pkm.said.util.SessionManager
import com.pkm.said.service.VoiceActivationService
import com.pkm.said.util.SpecialPermissionManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var onBackPressedCallback: OnBackPressedCallback? = null

    // Voice Assistant Integration
    private var voiceService: VoiceActivationService? = null
    private var isBound = false
    private var isUserLoggedIn = false
    private var currentUsername: String = ""

    // Bottom nav destinations to hide
    private val hideBottomNavDestinations = setOf(
        R.id.navigation_historyDetail
    )

    // ✅ SPECIAL PERMISSION MANAGER
    private lateinit var permissionManager: SpecialPermissionManager

    // Handler for delayed tasks
    private val mainHandler = Handler(Looper.getMainLooper())

    // Permission request flags
    private var shouldRequestCriticalPermissions = false
    private var criticalPermissionsRequestedOnce = false

    companion object {
        private const val TAG = "MainActivity"
        private const val VOICE_ASSISTANT_ENABLED = "voice_assistant_enabled"
    }

    // ✅ SERVICE CONNECTION
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as VoiceActivationService.VoiceServiceBinder
                voiceService = binder.getService()
                isBound = true
                Log.d(TAG, "✅ Voice Activation Service connected")
                updateVoiceServiceStatus()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in service connection", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
            isBound = false
            Log.d(TAG, "❌ Voice Activation Service disconnected")
            updateVoiceServiceStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== MAIN ACTIVITY DEBUG START ===")

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "✅ View Binding setup completed")

            // ✅ INITIALIZE SPECIAL PERMISSION MANAGER
            permissionManager = SpecialPermissionManager(this)
            Log.d(TAG, "✅ Permission manager initialized")

            // Navigation setup
            navController = findNavController(R.id.nav_host_fragment)
            binding.bottomNavView.setupWithNavController(navController)

            navController.addOnDestinationChangedListener { _, destination, _ ->
                val shouldHide = destination.id in hideBottomNavDestinations
                binding.navViewContainer.isVisible = !shouldHide
            }

            val navigateTo = intent.getStringExtra("navigate_to")
            if (navigateTo == "history") {
                navController.navigate(R.id.navigation_history)
            }

            setupNavigation()
            setupBackPressedHandler()

            // ✅ CEK SERVICE YANG SUDAH JALAN
            checkAndConnectToExistingService()

            // Handle incoming intent actions
            handleVoiceIntent(intent)

            currentUsername = getCurrentUsername()
            simulateLoginSuccess()

            // Defer permission dialog
            shouldRequestCriticalPermissions = true

            Log.d(TAG, "✅ MainActivity setup completed - User: $currentUsername")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR in onCreate", e)
            e.printStackTrace()
        }
    }

    // ✅ CEK JIKA SERVICE SUDAH JALAN
    private fun checkAndConnectToExistingService() {
        if (isVoiceServiceRunning()) {
            Log.d(TAG, "🔍 Voice service already running, connecting...")
            connectToExistingVoiceService()
        } else {
            Log.d(TAG, "🔍 Voice service not running, will start new if needed")
        }
    }

    // ✅ DETEKSI SERVICE YANG SUDAH JALAN
    private fun isVoiceServiceRunning(): Boolean {
        return try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningServices = manager.getRunningServices(Integer.MAX_VALUE)
            val isRunning = runningServices.any {
                it.service.className == VoiceActivationService::class.java.name
            }
            Log.d(TAG, "🎤 Voice service running check: $isRunning")
            isRunning
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking service status", e)
            false
        }
    }

    override fun onPostResume() {
        super.onPostResume()

        // ✅ PERBAIKI: Hanya check permissions sekali saat pertama kali
        if (shouldRequestCriticalPermissions && !criticalPermissionsRequestedOnce && !isFinishing && !isDestroyed) {
            try {
                Log.d(TAG, "🔄 Checking permissions on post resume...")
                checkPermissions()
                criticalPermissionsRequestedOnce = true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error checking permissions", e)
            } finally {
                shouldRequestCriticalPermissions = false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "🔄 onNewIntent called!")
        setIntent(intent)
        handleVoiceIntent(intent)
    }

    // ✅ VOICE INTENT HANDLING
    // Di MainActivity - handleVoiceIntent() tambahkan:
    private fun handleVoiceIntent(intent: Intent?) {
        intent ?: return
        Log.d(TAG, "🎤 Handling voice intent: ${intent.action}")

        when (intent.action) {
            "START_SCREENING" -> {
                Log.d(TAG, "🏥 Voice command - Start screening")
                startStrokeScreening()
            }
            "DAILY_REMINDER_OPEN" -> {
                Log.d(TAG, "📅 App opened from daily reminder")
                val autoStartScreening = intent.getBooleanExtra("auto_start_screening", false)
                if (autoStartScreening) {
                    mainHandler.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this, "⏰ Daily screening time!", Toast.LENGTH_LONG).show()
                            startStrokeScreening()
                        }
                    }, 2000)
                }
            }
            "EMERGENCY_CALL" -> {
                Log.d(TAG, "🚨 Emergency command from service fallback")
                handleEmergencyFallback() // Handle fallback case
            }
            else -> {
                Log.d(TAG, "❓ Unknown voice intent action: ${intent.action}")
                if (intent.getBooleanExtra("from_voice_service", false)) {
                    val targetFragment = intent.getStringExtra("target_fragment")
                    when (targetFragment) {
                        "dashboard" -> navigateToDashboard()
                        "screening" -> startStrokeScreening()
                        "emergency" -> handleEmergencyFallback() // Handle emergency fallback
                    }

                    // Handle emergency command dari extra
                    if (intent.getBooleanExtra("emergency_command", false)) {
                        handleEmergencyFallback()
                    }
                }
            }
        }
    }

    // Fallback emergency handler jika EmergencyActivity gagal
    private fun handleEmergencyFallback() {
        Log.d(TAG, "🚨 Emergency fallback - showing dialog")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🚨 Emergency Detected")
            .setMessage("Voice assistant detected emergency situation but couldn't start emergency screen. Please manually open emergency features.")
            .setPositiveButton("Open Emergency") { _, _ ->
                // Try to start EmergencyActivity again
                try {
                    val intent = Intent(this, EmergencyActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open emergency screen", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    // ✅ EMERGENCY COMMAND HANDLER - DIUBAH MENJADI DIALOG
    private fun handleEmergencyCommand() {
        try {
            Log.d(TAG, "🚨 Starting emergency procedure")

            AlertDialog.Builder(this)
                .setTitle("🚨 Emergency Detected")
                .setMessage("Voice assistant detected emergency situation. Do you need help?")
                .setPositiveButton("Call Emergency") { _, _ ->
                    // Implement emergency call logic here
                    Toast.makeText(this, "Emergency call initiated", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel") { _, _ -> }
                .show()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling emergency command", e)
            Toast.makeText(this, "🚨 Emergency feature not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToDashboard() {
        try {
            navController.navigate(R.id.navigation_dashboard)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to dashboard", e)
        }
    }

    private fun getCurrentUsername(): String {
        return SessionManager.getUserName(this) ?: getFallbackUsername()
    }

    private fun getFallbackUsername(): String {
        return try {
            // Coba dapatkan dari Firebase Auth sebagai fallback
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            when {
                firebaseUser?.displayName != null -> {
                    val username = firebaseUser.displayName!!
                    // Simpan ke SessionManager untuk konsistensi
                    saveUsernameToSessionManager(username)
                    username
                }
                firebaseUser?.email != null -> {
                    val email = firebaseUser.email!!
                    val usernameFromEmail = email.substringBefore("@")
                    saveUsernameToSessionManager(usernameFromEmail)
                    usernameFromEmail
                }
                else -> generateAnonymousUsername()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting fallback username", e)
            generateAnonymousUsername()
        }
    }

    private fun saveUsernameToSessionManager(username: String) {
        try {
            // Jika user sudah login di Firebase, update SessionManager
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            firebaseUser?.let { user ->
                SessionManager.saveBasicFromFirebase(this, user, "auto_detected")
            }
            Log.d(TAG, "✅ Username saved to SessionManager: $username")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving username to SessionManager", e)
        }
    }

    private fun generateAnonymousUsername(): String {
        val anonymousUser = "user_${System.currentTimeMillis()}"
        Log.d(TAG, "Generated anonymous username: $anonymousUser")
        return anonymousUser
    }

    private fun setupNavigation() {
        try {
            Log.d(TAG, "🔧 Starting navigation setup...")
            if (navController.currentDestination?.id != R.id.navigation_dashboard) {
                navController.navigate(R.id.navigation_dashboard)
            }
            Log.d(TAG, "✅ Navigation setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ NAVIGATION SETUP FAILED!", e)
            finish()
        }
    }

    private fun setupBackPressedHandler() {
        try {
            onBackPressedCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleCustomBackPressed()
                }
            }
            onBackPressedCallback?.let { callback ->
                onBackPressedDispatcher.addCallback(this, callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up back handler", e)
        }
    }

    fun selectBottomTab(@IdRes menuId: Int) {
        binding.bottomNavView.selectedItemId = menuId
    }

    private fun handleCustomBackPressed() {
        try {
            val currentDestId = navController.currentDestination?.id
            when (currentDestId) {
                R.id.navigation_dashboard -> {
                    Log.d(TAG, "At HOME - minimizing app (Voice Assistant stays active)")
                    minimizeApp()
                }
                R.id.navigation_history,
                R.id.navigation_profile -> {
                    Log.d(TAG, "At other fragment - navigating to HOME")
                    navController.navigate(R.id.navigation_dashboard)
                }
                else -> {
                    if (!navController.navigateUp()) {
                        minimizeApp()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in back press handling", e)
            minimizeApp()
        }
    }

    private fun minimizeApp() {
        try {
            Log.d(TAG, "📱 Minimizing app - Voice Assistant stays active")
            moveTaskToBack(true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error minimizing app", e)
            finish()
        }
    }

    // ✅ SIMULATE LOGIN
    private fun simulateLoginSuccess() {
        isUserLoggedIn = true

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_logged_in", true).apply()

        // Generate reports menggunakan permissionManager
        try {
            Log.d(TAG, "🔐 Preparing critical permissions...")
            val report = permissionManager.generatePermissionReport()
            Log.d(TAG, "📊 Permission Report:\n$report")

            // ✅ CEK BATTERY OPTIMIZATION DARI PERMISSION MANAGER
            val isBatteryOptimized = permissionManager.getAllPermissionStatus()
                .find { it.name == "BATTERY_OPTIMIZATION_IGNORED" }?.isGranted == true
            Log.d(TAG, "🔋 Battery optimization status: ${if (isBatteryOptimized) "BYPASSED" else "ACTIVE"}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating permission report", e)
        }

        onLoginSuccess()
    }

    // ✅ PERMISSION CHECKING METHOD
    private fun checkPermissions() {
        Log.d(TAG, "🔐 Checking all permissions...")
        permissionManager.checkAndRequestAllPermissions(this)
    }

    // ✅ ENHANCED LOGIN SUCCESS
    private fun onLoginSuccess() {
        Log.d(TAG, "✅ Login successful - checking voice assistant status")

        if (isVoiceServiceRunning()) {
            Log.d(TAG, "🎤 Voice service already running, just connecting...")
            connectToExistingVoiceService()
        } else {
            Log.d(TAG, "🎤 Voice service not running, starting new...")
            checkPermissionsAndStartVoiceAssistant()
        }
    }

    // ✅ CONNECT TO EXISTING SERVICE
    private fun connectToExistingVoiceService() {
        try {
            val intent = Intent(this, VoiceActivationService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "🔗 Connected to existing voice activation service")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error connecting to existing service", e)
        }
    }

    // ✅ PERMISSION CHECK
    private fun checkPermissionsAndStartVoiceAssistant() {
        val permissions = permissionManager.getAllPermissionStatus()
        val criticalMissing = permissions.count { !it.isGranted && it.isRequired }

        if (criticalMissing > 0) {
            Log.w(TAG, "⚠️ $criticalMissing critical permissions missing")
            // Biarkan SpecialPermissionManager menangani dialog
            // Tidak perlu showPermissionDialog() lagi
        } else {
            startVoiceAssistant()
        }
    }

    // ✅ START VOICE ASSISTANT
    private fun startVoiceAssistant() {
        try {
            Log.d(TAG, "🎤 Starting Voice Activation Service...")

            val intent = Intent(this, VoiceActivationService::class.java)
            ContextCompat.startForegroundService(this, intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(VOICE_ASSISTANT_ENABLED, true).apply()

            if (!isFinishing && !isDestroyed) {
                Toast.makeText(
                    this,
                    "🎤 Voice Assistant activated. Say 'Hey Barista' for commands",
                    Toast.LENGTH_LONG
                ).show()
            }

            Log.d(TAG, "✅ Voice Activation Service started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting voice assistant", e)
        }
    }

    // ✅ UPDATE SERVICE STATUS UI
    private fun updateVoiceServiceStatus() {
        try {
            val isRunning = isVoiceServiceRunning() && isBound
            Log.d(TAG, "🔊 Voice Service Status: ${if (isRunning) "RUNNING" else "STOPPED"}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating voice service status", e)
        }
    }

    // ✅ CALLBACK HANDLERS UNTUK PERMISSION MANAGER
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Forward ke Permission Manager
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults, this)

        // Check jika permissions sudah granted dan start service
        if (requestCode == SpecialPermissionManager.REQUEST_CODE_BASIC_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Log.d(TAG, "✅ All basic permissions granted, starting voice assistant...")
                startVoiceAssistant()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Forward ke Permission Manager
        permissionManager.onActivityResult(requestCode, resultCode, data, this)

        // Handle specific results
        when (requestCode) {
            SpecialPermissionManager.REQUEST_CODE_BATTERY_OPTIMIZATION -> {
                Log.d(TAG, "🔋 User kembali dari battery settings")
                // Optional: Check status battery optimization
                val isBatteryOptimized = permissionManager.getAllPermissionStatus()
                    .find { it.name == "BATTERY_OPTIMIZATION_IGNORED" }?.isGranted == true
                Log.d(TAG, "🔋 Battery optimization after settings: ${if (isBatteryOptimized) "BYPASSED" else "ACTIVE"}")
            }
            SpecialPermissionManager.REQUEST_CODE_OVERLAY_PERMISSION -> {
                Log.d(TAG, "📱 User kembali dari overlay settings")
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // ✅ PERBAIKI: Hanya update status, tidak check permissions berulang
        Log.d(TAG, "🔄 MainActivity onResume - updating status")
        updateVoiceServiceStatus()

        // Optional: Update permission status display jika ada UI
        checkPermissionStatus()
    }

    // ✅ STROKE SCREENING
    private fun startStrokeScreening() {
        try {
            Log.d(TAG, "🏥 Starting stroke screening process...")
            Log.d(TAG, "Username: $currentUsername")

            // ✅ GUNAKAN METHOD start() DARI SCREENINGACTIVITY
            ScreeningActivity.start(this, currentUsername, startNew = true)

            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "🏥 Starting screening for $currentUsername", Toast.LENGTH_SHORT).show()
            }
            Log.d(TAG, "✅ ScreeningActivity started successfully via companion method")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting stroke screening", e)
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "❌ Failed to start screening", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ PERMISSION STATUS CHECK (untuk logging)
    fun checkPermissionStatus() {
        val permissions = permissionManager.getAllPermissionStatus()
        val grantedCount = permissions.count { it.isGranted }
        val totalCount = permissions.size

        Log.d("PermissionStatus", "Granted: $grantedCount/$totalCount")

        permissions.forEach { permission ->
            Log.d("PermissionStatus", "${permission.name}: ${if (permission.isGranted) "GRANTED" else "MISSING"}")
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            supportFragmentManager.fragments.forEach { parent ->
                if (parent is androidx.fragment.app.DialogFragment) {
                    parent.dismissAllowingStateLoss()
                }
                parent.childFragmentManager.fragments.forEach { child ->
                    if (child is androidx.fragment.app.DialogFragment) {
                        child.dismissAllowingStateLoss()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Warning dismissing dialogs onStop", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🧹 MainActivity onDestroy() - cleanup")

        // Remove pending callbacks
        mainHandler.removeCallbacksAndMessages(null)

        // Cleanup service connection
        try {
            if (isBound) {
                unbindService(serviceConnection)
                isBound = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unbinding service", e)
        }

        // Clear references
        voiceService = null
        onBackPressedCallback?.remove()
        onBackPressedCallback = null

        Log.d(TAG, "=== MAIN ACTIVITY DEBUG END ===")
    }
}