package com.pkm.said.screening

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.pkm.said.R
import com.pkm.said.databinding.ActivityScreeningBinding

class ScreeningActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScreeningActivity"
        private const val KEY_USER_ID = "user_id"
        private const val EXTRA_DEST = "dest"
        private const val EXTRA_START_NEW = "startNew"

        fun start(context: Context, userId: String? = null, dest: String? = null,
                  startNew: Boolean = false) {
            val i = Intent(context, ScreeningActivity::class.java).apply {
                putExtra(KEY_USER_ID, userId ?: "itsLuxra")
                dest?.let { putExtra(EXTRA_DEST, it) }
                putExtra(EXTRA_START_NEW, startNew)
            }
            context.startActivity(i)
        }
    }

    private lateinit var binding: ActivityScreeningBinding
    private lateinit var navController: NavController
    private var userId: String = "itsLuxra"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== SCREENING ACTIVITY START ===")
        binding = ActivityScreeningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // restore userId aman dari intent / saved state
        userId = savedInstanceState?.getString(KEY_USER_ID)
            ?: intent.getStringExtra(KEY_USER_ID)
                    ?: "itsLuxra"

        setupFullscreenMode()
        setupNavigation()
        setupBackPressHandler()

        // Mulai sesi HANYA sekali (hindari start ulang saat rotasi/recreate)
        if (savedInstanceState == null) {
            val startNew = intent.getBooleanExtra(EXTRA_START_NEW, false)
            val existing = ScreeningDataManager.getCurrentSession(this)

            // Mulai sesi baru hanya jika diminta, atau belum ada sesi, atau sesi sebelumnya sudah completed
            if (startNew || existing == null || existing.isCompleted) {
                ScreeningDataManager.startNewSession(this, userId)
                Log.d(TAG, "Start NEW session for user: $userId (startNew=$startNew, existing=${existing != null})")
            } else {
                Log.d(TAG, "Resume existing session: ${existing.sessionId}")
            }

            val explicitDest = intent.getStringExtra(EXTRA_DEST)
            val target = explicitDest ?: firstPendingKeyOrNull()

            when (target) {
                "face"   -> navController.navigate(R.id.faceTestPreviewFragment)
                "arms"   -> navController.navigate(R.id.armsTestPreviewFragment)
                "speech" -> navController.navigate(R.id.speechTestPreviewFragment)
                "result" -> navController.navigate(R.id.screeningResultFragment)
                null     -> { /* stay at startDestination */ }
                else     -> { /* unknown dest, ignore */ }
            }
        }
    }

    private fun firstPendingKeyOrNull(): String? {
        val active = ScreeningDataManager.getCurrentSession(this) ?: return null
        if (active.isCompleted) return null
        val pending = ScreeningDataManager.getPendingTests(this)
        if (pending.isEmpty()) return null
        return when (pending.first().lowercase()) {
            "face_test","face" -> "face"
            "arms_test","arms","arm_test","arm","befast_arm" -> "arms"
            "speech_test","speech","befast_speech" -> "speech"
            else -> "face"
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

    private fun initializeScreeningSession() {
        val existing = ScreeningDataManager.getCurrentSession(this)
        if (existing?.isCompleted == true) {
            ScreeningDataManager.cancelSession(this)
        }
        ScreeningDataManager.startNewSession(this, userId)
        Log.d(TAG, "Screening session initialized for user: $userId")
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
        ScreeningDataManager.cancelSession(this)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== SCREENING ACTIVITY END ===")
    }
}
