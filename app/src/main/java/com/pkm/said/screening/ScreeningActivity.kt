package com.pkm.said.screening

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.pkm.said.EmergencyActivity
import com.pkm.said.R
import com.pkm.said.Said
import com.pkm.said.databinding.ActivityScreeningBinding
import kotlinx.coroutines.launch

class ScreeningActivity : AppCompatActivity(), Said.VoiceActivityCallback {

    companion object {
        private const val TAG = "ScreeningActivity"
        private const val KEY_USER_ID = "user_id"
        private const val EXTRA_DEST = "dest"
        private const val EXTRA_START_NEW = "startNew"

        /**
         * @param dest: "balance" | "eyes" | "face" | "arms" | "result" (opsional)
         */
        fun start(
            context: Context,
            userId: String? = null,
            dest: String? = null,
            startNew: Boolean = false
        ) {
            val i = Intent(context, ScreeningActivity::class.java).apply {
                putExtra(KEY_USER_ID, userId ?: "unknown")
                dest?.let { putExtra(EXTRA_DEST, it) }
                putExtra(EXTRA_START_NEW, startNew)
            }
            context.startActivity(i)
        }
    }

    private lateinit var binding: ActivityScreeningBinding
    private lateinit var navController: NavController
    private var userId: String = "unknown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== SCREENING ACTIVITY START ===")
        binding = ActivityScreeningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // restore userId dari intent / saved state
        userId = savedInstanceState?.getString(KEY_USER_ID)
            ?: intent.getStringExtra(KEY_USER_ID)
                    ?: "unknown"

        setupFullscreenMode()
        setupNavigation()
        setupBackPressHandler()
        Said.getInstance().registerActivityCallback(this.localClassName, this)

        // Inisialisasi sesi screening
        if (savedInstanceState == null) {
            initializeScreeningSession()
        }
    }

    private fun initializeScreeningSession() {
        val startNew = intent.getBooleanExtra(EXTRA_START_NEW, false)
        val explicitDest = intent.getStringExtra(EXTRA_DEST)?.lowercase()

        lifecycleScope.launch {
            try {
                // SIMPLIFIED: Hanya gunakan ScreeningDataManager, tidak perlu remote check
                val currentSession = ScreeningDataManager.getCurrentSession(this@ScreeningActivity)

                if (startNew || currentSession == null || currentSession.isCompleted) {
                    // Clear session lama yang mungkin stuck
                    ScreeningDataManager.cancelSession(this@ScreeningActivity)

                    // Mulai sesi baru
                    val newSession = ScreeningDataManager.startNewSession(this@ScreeningActivity, userId)
                    Log.d(TAG, "Started NEW screening session: ${newSession.sessionId}")
                } else {
                    Log.d(TAG, "Resuming EXISTING session: ${currentSession.sessionId}")
                }

                // Tentukan tujuan navigasi - PERBAIKI LOGIKA INI
                val targetDest = when {
                    explicitDest != null -> explicitDest
                    ScreeningDataManager.areAllTestsCompleted(this@ScreeningActivity) -> "result"
                    else -> getFirstPendingTest()
                }

                navigateTo(targetDest)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize screening: ${e.message}", e)
                // Emergency fallback
                ScreeningDataManager.cancelSession(this@ScreeningActivity)
                ScreeningDataManager.startNewSession(this@ScreeningActivity, userId)
                navigateTo("balance")
            }
        }
    }

    /**
     * Mendapatkan test pertama yang belum selesai - PERBAIKI
     */
    private fun getFirstPendingTest(): String {
        val pendingTests = ScreeningDataManager.getPendingTests(this@ScreeningActivity)
        Log.d(TAG, "Pending tests: $pendingTests")

        // Urutan BEFAST yang benar
        return when {
            "balance" in pendingTests -> "balance"
            "eyes" in pendingTests -> "eyes"
            "face" in pendingTests -> "face"
            "arms" in pendingTests -> "arms"
            "speech" in pendingTests -> "speech"
            else -> {
                // Jika tidak ada pending, cek apakah semua selesai
                if (ScreeningDataManager.areAllTestsCompleted(this@ScreeningActivity)) {
                    "result"
                } else {
                    "balance" // fallback
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_USER_ID, userId)
    }

    private fun setupFullscreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupNavigation() {
        navController = findNavController(R.id.nav_host_fragment_screening)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d(TAG, "Current fragment: ${destination.label}")
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (navController.currentDestination?.id) {
                    R.id.screeningResultFragment -> {
                        // Sudah selesai → keluar tanpa dialog
                        exitScreening()
                    }
                    else -> showExitConfirmation()
                }
            }
        })
    }

    private fun showExitConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Keluar dari Screening?")
            .setMessage("Apakah Anda yakin ingin keluar dari proses screening?\nProgress akan hilang.")
            .setPositiveButton("Ya, Keluar") { _, _ -> exitScreening() }
            .setNegativeButton("Lanjutkan") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    fun exitScreening() {
        // Hanya batalkan sesi di lokal, tidak perlu sync ke Firestore
        ScreeningDataManager.cancelSession(this)
        finish()
    }

    override fun onVoiceCommand(command: String, extras: Bundle?): Boolean {
        Log.d(TAG, "🎤 Voice command received in Screening: $command")
        return when (command.toLowerCase()) {
            "darurat", "emergency", "tolong"-> {
                handleEmergencyFromVoice()
                true
            }
            else -> false
        }
    }

    override fun onNavigateTo(destination: String): Boolean {
        return when (destination.toLowerCase()) {
            "emergency" -> {
                handleEmergencyFromVoice()
                true
            }
            else -> false
        }
    }

    override fun getSupportedCommands(): List<String> {
        return listOf(
            "darurat", "emergency", "tolong"
        )
    }

    private fun handleEmergencyFromVoice() {
        try {
            Log.d(TAG, "🚨 EMERGENCY DETECTED - Cancelling screening and opening emergency")

            // 1. Batalkan screening session
            ScreeningDataManager.cancelSession(this)

            // 2. Tampilkan notifikasi cepat
            Toast.makeText(this, "🚨 Emergency detected - Screening cancelled", Toast.LENGTH_SHORT).show()

            // 3. Buka EmergencyActivity
            val intent = Intent(this, EmergencyActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_voice_command", true)
                putExtra("from_screening", true)
                putExtra("screening_cancelled", true) // Beri tahu emergency activity
            }
            startActivity(intent)

            // 4. Tutup screening activity
            finish()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to handle emergency from screening", e)
            // Fallback: tetap batalkan screening dan coba buka emergency
            ScreeningDataManager.cancelSession(this)
            try {
                startActivity(Intent(this, EmergencyActivity::class.java))
                finish()
            } catch (e2: Exception) {
                // Ultimate fallback: hanya batalkan screening
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Said.getInstance().unregisterActivityCallback(this.localClassName)
        Log.d(TAG, "=== SCREENING ACTIVITY END ===")
    }

    // ===== Navigasi =====
    private fun navigateTo(destKey: String) {
        val destId = when (destKey.lowercase()) {
            "balance" -> R.id.balanceTestPreviewFragment
            "eyes"    -> R.id.eyesTestPreviewFragment
            "face"    -> R.id.faceTestPreviewFragment
            "arms"    -> R.id.armsTestPreviewFragment
            "result"  -> R.id.screeningResultFragment
            else      -> R.id.balanceTestPreviewFragment // fallback
        }
        safeNavigate(destId)
    }

    private fun safeNavigate(destId: Int) {
        try {
            if (navController.currentDestination?.id == destId) {
                Log.d(TAG, "Navigation ignored: Already at destination $destId")
                return
            }

            if (navController.currentDestination?.id == navController.graph.startDestinationId && destId == navController.graph.startDestinationId) {
                Log.d(TAG, "Navigation ignored: Already at start destination.")
                return
            }

            navController.navigate(destId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ FATAL: Navigation failed to ID $destId", e)
        }
    }
}