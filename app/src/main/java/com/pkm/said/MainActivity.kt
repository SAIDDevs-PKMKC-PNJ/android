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
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.pkm.said.databinding.ActivityMainBinding
import com.pkm.said.screening.ScreeningActivity
import com.pkm.said.service.VoiceActivationService
import com.pkm.said.util.SessionManager
import com.pkm.said.util.SpecialPermissionManager


interface NavigationCallback {
    // Definisi fungsi yang ingin kamu panggil di Activity
    fun navigateToTopLevel(destinationId: Int)
}


class MainActivity : AppCompatActivity(), NavigationCallback, Said.VoiceActivityCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var onBackPressedCallback: OnBackPressedCallback? = null

    // Voice Assistant Integration
    private var voiceService: VoiceActivationService? = null
    private var isBound = false
    private var isUserLoggedIn = false
    private var currentUsername: String = ""

    private val topLevelDestinations = setOf(
        R.id.navigation_dashboard,
        R.id.navigation_history,
        R.id.navigation_profile
    )

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

    companion object {
        private const val TAG = "MainActivity"
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

//            debugPicovoiceAssets()
            Said.getInstance().registerActivityCallback(this.localClassName, this)

            try {
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                if (navHostFragment == null) {
                    Log.e(
                        TAG,
                        "❌ NavHostFragment not found with ID R.id.nav_host_fragment. Crash imminent."
                    )
                    // Lakukan penanganan yang lebih baik, misalnya Toast dan finish()
                    Toast.makeText(
                        this,
                        "Navigasi gagal: NavHost tidak ditemukan.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                this.navController = navHostFragment.navController
//                binding.bottomNavView.setupWithNavController(this.navController)

                binding.bottomNavView.setOnItemSelectedListener { item ->
                    // Hanya tangani tujuan yang merupakan tab utama
                    if (topLevelDestinations.contains(item.itemId)) {
                        // Panggil fungsi inti yang menjamin back stack bersih dan single top
                        executeToTopLevel(item.itemId)
                        true // Event dikonsumsi
                    } else {
                        // Biarkan default handling (untuk item yang mungkin bukan Fragment)
                        false
                    }
                }

                navController.addOnDestinationChangedListener { _, destination, _ ->

                    // 1. Logika sembunyikan Navbar untuk detail
                    val shouldHide = destination.id in hideBottomNavDestinations
                    binding.navViewContainer.isVisible = !shouldHide

                    // 2. Logika SINKRONISASI SOROTAN (HIGHLIGHT)
                    if (topLevelDestinations.contains(destination.id)) {
                        // Panggil selectBottomTab dengan ID destinasi yang baru
                        selectBottomTab(destination.id)
                        Log.d(
                            TAG,
                            "✅ Nav sync: Highlight set to ${
                                resources.getResourceEntryName(destination.id)
                            }"
                        )
                    }
                }

                mainHandler.postDelayed({
                    handleIntentNavigation(intent)
                }, 500)
            } catch (e: Exception) {
                Log.e(TAG, "❌ CRITICAL: NavController setup failed", e)
                throw e
            }

            setupBackPressedHandler()

            // ✅ CEK SERVICE YANG SUDAH JALAN
            checkAndConnectToExistingService()

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

    private fun selectBottomTab(@IdRes destinationId: Int) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav_view)
        if (bottomNav.selectedItemId != destinationId) {
            // Ini adalah langkah kritis: Secara manual mengatur item menu yang dicentang
            bottomNav.menu.findItem(destinationId)?.isChecked = true
            // Kamu juga mungkin perlu memicu setelannya sebagai "terpilih" jika menggunakan efek visual kustom
            // Meskipun setChecked=true biasanya sudah cukup, item terpilih harus diset
            bottomNav.selectedItemId = destinationId // <--- PASTIKAN KAMU MEMANGGIL INI
        }
    }

    private fun handleIntentNavigation(intent: Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo == "history") {
            Log.d(TAG, "Intent: Navigating to History")
            // Panggil fungsi yang sudah diperbaiki
            executeToTopLevel(R.id.navigation_history)
        } else if (navigateTo == "profile") {
            Log.d(TAG, "Intent: Navigating to Profile")
            executeToTopLevel(R.id.navigation_profile)
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
            val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
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
        if (!isFinishing && !isDestroyed) {
            Log.d(TAG, "🔄 MainActivity onPostResume - Re-checking permissions for UI update.")
            checkPermissionStatus() // Cek status izin lagi

            if (isUserLoggedIn) {
                onLoginSuccess()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "🔄 onNewIntent called!")
        setIntent(intent)
    }

    //legacy untuk picovoice
//    private fun debugPicovoiceAssets() {
//        val picovoiceManager = PicovoiceManager(
//            context = this,
//            onIntentDetected = { /* dummy */ },
//            onListeningStatusChange = { /* dummy */ }
//        )
//
//        Log.d(TAG, "Starting asset copy debug...")
//
//        try {
//            // Coba copy HANYA file PPN
//            val keywordPath = picovoiceManager.copyAssetToFiles("hi-said_en_android_v3_0_0.ppn")
//            Log.d(TAG, "SUCCESS: Keyword file path: $keywordPath")
//
//        } catch (e: Exception) {
//            // Log error copy aset secara terpisah dan eksplisit
//            Log.e(TAG, "❌ FATAL ASSET ERROR: Asset copy failed during debug", e)
//            Toast.makeText(this, "FATAL ERROR: Asset Copy Failed! Check Logcat", Toast.LENGTH_LONG)
//                .show()
//        }
//    }

    override fun onVoiceCommand(command: String, extras: Bundle?): Boolean {
        Log.d(TAG, "🎤 Voice command received: $command")
        return when (command.toLowerCase()) {
            // SCREENING - gunakan fungsi existing startStrokeScreening()
            "mulai tes", "mulai screening", "screening", "tes" -> {
                startStrokeScreening()
                true
            }
            // EMERGENCY - gunakan fungsi baru handleEmergencyFromVoice()
            "darurat", "emergency", "tolong" -> {
                handleEmergencyFromVoice()
                true
            }
            else -> false
        }
    }

    override fun onNavigateTo(destination: String): Boolean {
        Log.d(TAG, "🧭 Navigation command: $destination")
        return when (destination.toLowerCase()) {
            "dashboard", "home" -> {
                navigateToDashboard()
                true
            }
            else -> false
        }
    }

    // SUPPORTED COMMANDS
    override fun getSupportedCommands(): List<String> {
        return listOf(
            "tes stroke", "mulai screening", "screening",
            "darurat", "emergency", "tolong",
            "dashboard", "home"
        )
    }

    private fun handleEmergencyFromVoice() {
        try {
            Log.d(TAG, "🚨 Emergency from voice command - starting EmergencyActivity")
            val intent = Intent(this, EmergencyActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_voice_command", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start EmergencyActivity, using fallback", e)
            // Fallback ke dialog emergency
            showEmergencyFallbackDialog()
        }
    }

    private fun showEmergencyFallbackDialog() {
        Log.d(TAG, "🚨 Emergency fallback - showing dialog")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🚨 Emergency Detected")
            .setMessage("Voice assistant detected emergency situation. Please manually open emergency features.")
            .setPositiveButton("Open Emergency") { _, _ ->
                // Try to start EmergencyActivity again dengan approach berbeda
                try {
                    val intent = Intent(this, EmergencyActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open emergency screen", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "❌ Emergency fallback also failed", e)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    fun triggerEmergencyPermissionRequest() {
        val emergencyPermissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val missingEmergencyPermissions = emergencyPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingEmergencyPermissions.isNotEmpty()) {
            emergencyPermissionLauncher.launch(missingEmergencyPermissions.toTypedArray())
        } else {
            Toast.makeText(this, "Izin darurat sudah lengkap.", Toast.LENGTH_SHORT).show()
        }
    }

    private val emergencyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "📞 Emergency permission results handled.")
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Izin darurat telah diberikan.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "⚠️ Izin darurat ditolak. Fitur terbatas.", Toast.LENGTH_LONG)
                .show()
        }
    }


    // ✅ STROKE SCREENING
    private fun startStrokeScreening() {
        try {
            Log.d(TAG, "🏥 Starting stroke screening process...")
            Log.d(TAG, "Username: $currentUsername")

            // ✅ GUNAKAN METHOD start() DARI SCREENINGACTIVITY
            ScreeningActivity.start(this, currentUsername, startNew = true)

            if (!isFinishing && !isDestroyed) {
                Toast.makeText(
                    this,
                    "🏥 Starting screening for $currentUsername",
                    Toast.LENGTH_SHORT
                ).show()
            }
            Log.d(TAG, "✅ ScreeningActivity started successfully via companion method")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting stroke screening", e)
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "❌ Failed to start screening", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun navigateToTopLevel(destinationId: Int) {
        // Panggil fungsi navigateToTopLevel yang sudah kamu perbaiki
        // dengan NavOptions dan sinkronisasi navbar.
        this.executeToTopLevel(destinationId)
    }

    fun executeToTopLevel(@IdRes destinationId: Int) {
        if (!topLevelDestinations.contains(destinationId)) {
            Log.e(
                TAG,
                "❌ Destination ID $destinationId is not a top-level destination. Aborting navigation."
            )
            return
        }

        try {
            val options = NavOptions.Builder()
                .setPopUpTo(navController.graph.startDestinationId, false) // <-- PENTING
                .setLaunchSingleTop(true)
                .build()

            navController.navigate(destinationId, null, options)

            Log.d(
                TAG,
                "✅ Top-Level Nav: Moved to ${resources.getResourceEntryName(destinationId)} with pop-up options."
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during top-level navigation to $destinationId", e)
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
            val startDestId =
                navController.graph.startDestinationId // Atau navController.graph.startDestinationId

            // ID dari semua destinasi level teratas Anda
            val topLevelDestinations = setOf(
                R.id.navigation_dashboard,
                R.id.navigation_history,
                R.id.navigation_profile
            )

            if (topLevelDestinations.contains(currentDestId)) {
                // Jika sedang di tab utama
                if (currentDestId == startDestId) {
                    // Jika di Dashboard (Root), minimalkan aplikasi
                    Log.d(TAG, "At Dashboard (Start) - minimizing app.")
                    minimizeApp()
                } else {
                    // Jika di tab lain, coba pop backstack ke Dashboard.
                    if (!navController.popBackStack(startDestId, false)) {
                        Log.d(TAG, "Failed to pop back to Dashboard, minimizing.")
                        stopVoiceServiceFromActivity()
                        minimizeApp()
                    } else {
                        Log.d(TAG, "Popped back to Dashboard successfully.")
                    }
                }
            } else {
                // Jika sedang di Fragment Detail:
                if (!navController.navigateUp()) {
                    Log.d(TAG, "Navigate up failed, minimizing.")
                    minimizeApp()
                } else {
                    Log.d(TAG, "Navigated up successfully.")
                }
            }
        } catch (e: Exception) {
            // Ini menangkap UninitializedPropertyAccessException jika masih ada (tapi seharusnya sudah beres)
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
        prefs.edit { putBoolean("user_logged_in", true) }

        // Generate reports menggunakan permissionManager
        try {
            Log.d(TAG, "🔐 Preparing critical permissions...")
            val report = permissionManager.generatePermissionReport()
            Log.d(TAG, "📊 Permission Report:\n$report")

            // ✅ CEK BATTERY OPTIMIZATION DARI PERMISSION MANAGER
            val isBatteryOptimized = permissionManager.getAllPermissionStatus()
                .find { it.name == "BATTERY_OPTIMIZATION_IGNORED" }?.isGranted == true
            Log.d(
                TAG,
                "🔋 Battery optimization status: ${if (isBatteryOptimized) "BYPASSED" else "ACTIVE"}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating permission report", e)
        }

        onLoginSuccess()
    }

    // ✅ ENHANCED LOGIN SUCCESS
    private fun onLoginSuccess() {
        Log.d(TAG, "✅ Login successful - checking voice assistant status")

        if (isVoiceServiceRunning()) {
            Log.d(TAG, "🎤 Voice service already running, just connecting...")
            connectToExistingVoiceService()
        } else {
            Log.d(TAG, "🎤 Voice service not running, checking permissions...")

            // ✅ DEBUG: Cek status permissions dulu
            val needsAudio = !permissionManager.isRecordAudioGranted()
            val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !permissionManager.isNotificationPermissionGranted()

            Log.d(TAG, "📊 Permission Status Check:")
            Log.d(TAG, "   - Needs RECORD_AUDIO: $needsAudio")
            Log.d(TAG, "   - Needs POST_NOTIFICATIONS: $needsNotification")

            if (needsAudio || needsNotification) {
                Log.d(TAG, "🚀 Requesting permissions via SpecialPermissionManager...")
                permissionManager.requestBasicPermissions(this)
            } else {
                Log.d(TAG, "✅ All basic permissions already granted, starting voice assistant...")
            }

            triggerEmergencyPermissionRequest()
        }
    }

    private fun stopVoiceServiceFromActivity() {
        val intent = Intent(this, VoiceActivationService::class.java).apply {
            // Gunakan ACTION_STOP yang Anda tetapkan untuk penghentian non-permanen
            action = VoiceActivationService.ACTION_STOP
        }
        try {
            stopService(intent) // Memberi tahu Service untuk mematikan dirinya
            Log.d(TAG, "✅ Explicitly sent ACTION_STOP to Voice Service.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping service from MainActivity", e)
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

        Log.d(TAG, "🎯 ON_REQUEST_PERMISSIONS_RESULT CALLED!")
        Log.d(TAG, "   Request Code: $requestCode")
        Log.d(TAG, "   Permissions: ${permissions.joinToString()}")
        Log.d(TAG, "   Grant Results: ${grantResults.joinToString()}")
        Log.d(TAG, "   Expected Code: ${SpecialPermissionManager.REQUEST_CODE_BASIC_PERMISSIONS}")


        // Forward ke Permission Manager
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults, this)

        // Check jika permissions sudah granted dan start service
        if (requestCode == SpecialPermissionManager.REQUEST_CODE_BASIC_PERMISSIONS) {
            val allBasicGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allBasicGranted) {
                Log.d(TAG, "✅ All basic permissions granted - Starting voice assistant immediately")
            } else {
                Log.w(TAG, "⚠️ Some basic permissions were denied")
                // Tampilkan pesan bahwa fitur voice akan terbatas
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this,
                        "Voice assistant limited without microphone permission",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Forward ke Permission Manager
        permissionManager.onActivityResult(requestCode, resultCode, data, this)
    }

    override fun onResume() {
        super.onResume()

        // ✅ PERBAIKI: Hanya update status, tidak check permissions berulang
        Log.d(TAG, "🔄 MainActivity onResume - updating status")
        updateVoiceServiceStatus()

        if (isUserLoggedIn) {
            onLoginSuccess() // Fungsi ini akan memanggil startVoiceAssistant() jika izin OK.
        }
    }


    // ✅ PERMISSION STATUS CHECK (untuk logging)
    fun checkPermissionStatus() {
        val permissions = permissionManager.getAllPermissionStatus()
        val grantedCount = permissions.count { it.isGranted }
        val totalCount = permissions.size

        Log.d("PermissionStatus", "Granted: $grantedCount/$totalCount")

        permissions.forEach { permission ->
            Log.d(
                "PermissionStatus",
                "${permission.name}: ${if (permission.isGranted) "GRANTED" else "MISSING"}"
            )
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

        Said.getInstance().unregisterActivityCallback(this.localClassName)

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

        onBackPressedCallback?.remove()
        onBackPressedCallback = null

        Log.d(TAG, "=== MAIN ACTIVITY DEBUG END ===")
    }
}