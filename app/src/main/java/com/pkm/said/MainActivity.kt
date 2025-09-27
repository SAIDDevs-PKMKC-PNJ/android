package com.pkm.said

import android.Manifest
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.pkm.said.databinding.ActivityMainBinding
import com.pkm.said.screening.ScreeningActivity
import com.pkm.said.service.VoiceAssistantService
import com.pkm.said.util.BatteryOptimizationManager
import com.pkm.said.util.SpecialPermissionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var onBackPressedCallback: OnBackPressedCallback? = null

    // Voice Assistant Integration
    private var voiceService: VoiceAssistantService? = null
    private var isBound = false
    private var isUserLoggedIn = false
    private var currentUsername = "itsLuxra"

    // Bottom nav destinations to hide
    private val hideBottomNavDestinations = setOf(
        R.id.navigation_historyDetail
    )

    // Managers
    private lateinit var batteryManager: BatteryOptimizationManager
    private lateinit var permissionManager: SpecialPermissionManager

    // Handler for delayed tasks; cleared on destroy to avoid posting after Activity is gone
    private val mainHandler = Handler(Looper.getMainLooper())

    // Permission request deferral flags (to avoid showing dialogs in onCreate)
    private var shouldRequestCriticalPermissions = false
    private var criticalPermissionsRequestedOnce = false

    companion object {
        private const val TAG = "MainActivity"
        private const val VOICE_ASSISTANT_ENABLED = "voice_assistant_enabled"
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VoiceAssistantService.VoiceAssistantBinder
            voiceService = binder.getService()
            isBound = true
            Log.d(TAG, "✅ Voice Assistant Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
            isBound = false
            Log.d(TAG, "❌ Voice Assistant Service disconnected")
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val microphoneGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            true
        }

        when {
            microphoneGranted && notificationGranted -> {
                Log.d(TAG, "✅ All permissions granted")
                startVoiceAssistant()
            }
            microphoneGranted && !notificationGranted -> {
                Log.w(TAG, "⚠️ Microphone granted but notification denied")
                startVoiceAssistant()
            }
            else -> {
                Log.e(TAG, "❌ Microphone permission denied")
                Toast.makeText(this, "Microphone permission required for voice commands", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== MAIN ACTIVITY DEBUG START ===")
        Log.d(TAG, "Build.VERSION.SDK_INT: ${Build.VERSION.SDK_INT}")

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "✅ View Binding setup completed")

            // Initialize managers with Activity context (not application)
            batteryManager = BatteryOptimizationManager(this)
            permissionManager = SpecialPermissionManager(this)
            Log.d(TAG, "✅ Permission managers initialized")

            // Use the property navController (no local shadow)
            navController = findNavController(R.id.nav_host_fragment)
            binding.bottomNavView.setupWithNavController(navController)

            navController.addOnDestinationChangedListener { _, destination, _ ->
                val shouldHide = destination.id in hideBottomNavDestinations
                binding.navViewContainer.isVisible = !shouldHide
            }

            setupNavigation()
            setupBackPressedHandler()

            // Handle incoming intent actions
            handleVoiceIntent(intent)

            currentUsername = getCurrentUsername()

            // Mark user as logged-in (your flow can replace this)
            simulateLoginSuccess()

            // Defer permission dialog until Activity is RESUMED to avoid window leaks
            shouldRequestCriticalPermissions = true

            Log.d(TAG, "✅ MainActivity setup completed - User: $currentUsername")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR in onCreate", e)
            e.printStackTrace()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        // Show critical permissions dialog only when Activity is in RESUMED state
        if (shouldRequestCriticalPermissions && !criticalPermissionsRequestedOnce && !isFinishing && !isDestroyed) {
            try {
                setupCriticalPermissions()
                criticalPermissionsRequestedOnce = true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error requesting critical permissions in onPostResume", e)
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

    // Handle intents to start screening or reminders
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
                    // Use handler field (cleared in onDestroy) to avoid posting after Activity is gone
                    mainHandler.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this, "⏰ Daily screening time!", Toast.LENGTH_LONG).show()
                            startStrokeScreening()
                        }
                    }, 2000)
                }
            }
            else -> {
                Log.d(TAG, "❓ Unknown voice intent action: ${intent.action}")
            }
        }
    }

    private fun getCurrentUsername(): String {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return prefs.getString("username", "itsLuxra") ?: "itsLuxra"
    }

    private fun setupNavigation() {
        try {
            Log.d(TAG, "🔧 Starting navigation setup...")
            // navController already initialized in onCreate
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

    // Simulate login success (set your real login result here)
    private fun simulateLoginSuccess() {
        isUserLoggedIn = true

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_logged_in", true).apply()

        // Generate non-UI reports/logs only (do not show dialog here)
        try {
            Log.d(TAG, "🔐 Preparing critical permissions (no dialogs in onCreate)...")
            val report = permissionManager.generatePermissionReport()
            Log.d(TAG, "📊 Permission Report:\n$report")

            val isBatteryOptimized = batteryManager.isIgnoringBatteryOptimizations()
            Log.d(TAG, "🔋 Battery optimization status: ${if (isBatteryOptimized) "BYPASSED" else "ACTIVE"}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating permission report", e)
        }

        onLoginSuccess()
    }

    // Actually request critical permissions (may show dialogs) — call only when RESUMED
    private fun setupCriticalPermissions() {
        if (isFinishing || isDestroyed) return
        try {
            permissionManager.requestAllCriticalPermissions(this)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up critical permissions", e)
        }
    }

    private fun onLoginSuccess() {
        Log.d(TAG, "✅ Login successful - checking voice assistant status")
        if (isUserLoggedIn) {
            if (!isVoiceAssistantRunning()) {
                checkPermissionsAndStartVoiceAssistant()
            } else {
                connectToExistingVoiceService()
            }
        }
    }

    private fun isVoiceAssistantRunning(): Boolean {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(VOICE_ASSISTANT_ENABLED, false)
    }

    private fun connectToExistingVoiceService() {
        try {
            val intent = Intent(this, VoiceAssistantService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "🔗 Connected to existing voice assistant service")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error connecting to existing service", e)
        }
    }

    private fun checkPermissionsAndStartVoiceAssistant() {
        val requiredPermissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (requiredPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            startVoiceAssistant()
        }
    }

    private fun startVoiceAssistant() {
        try {
            Log.d(TAG, "🎤 Starting PERSISTENT Voice Assistant Service...")

            val intent = Intent(this, VoiceAssistantService::class.java)
            ContextCompat.startForegroundService(this, intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(VOICE_ASSISTANT_ENABLED, true).apply()

            if (!isFinishing && !isDestroyed) {
                Toast.makeText(
                    this,
                    "🎤 Voice Assistant activated. Say 'Hey Google, start screening'",
                    Toast.LENGTH_LONG
                ).show()
            }

            Log.d(TAG, "✅ PERSISTENT Voice Assistant started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting voice assistant", e)
        }
    }

    // Simplified: Only screening start command
    private fun startStrokeScreening() {
        try {
            Log.d(TAG, "🏥 Starting stroke screening process...")
            Log.d(TAG, "Username: $currentUsername")

            // Launch ScreeningActivity
            ScreeningActivity.start(this, currentUsername)

            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "🏥 Starting screening for $currentUsername", Toast.LENGTH_SHORT).show()
            }
            Log.d(TAG, "✅ ScreeningActivity started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting stroke screening", e)
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "❌ Failed to start screening", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Optional utility to inspect current permission status
    fun debugPermissionStatus() {
        val permissions = permissionManager.getAllPermissionStatus()

        Log.d(TAG, "🔍 Current Permission Status:")
        permissions.forEach { permission ->
            val status = if (permission.isGranted) "✅" else "❌"
            val required = if (permission.isRequired) " (REQUIRED)" else ""
            Log.d(TAG, "$status ${permission.name}$required")
        }

        val criticalMissing = permissions.count { !it.isGranted && it.isRequired }
        if (criticalMissing > 0) {
            Toast.makeText(
                this,
                "⚠️ $criticalMissing critical permissions missing",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this,
                "✅ All critical permissions granted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onStop() {
        super.onStop()
        // Best-effort: dismiss any DialogFragments to avoid leaks from fragments
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

        // Remove any pending callbacks to avoid posting UI work after Activity is gone
        mainHandler.removeCallbacksAndMessages(null)

        if (isBound) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) { /* ignore */ }
            isBound = false
        }

        // If your SpecialPermissionManager exposes a dismiss method, call it here:
        // try { permissionManager.dismissAllDialogs() } catch (_: Exception) {}

        Log.d(TAG, "=== MAIN ACTIVITY DEBUG END ===")
    }
}