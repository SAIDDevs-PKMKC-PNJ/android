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
        private const val TAG: String = "ScreeningActivity"

        fun start(context: Context, userId: String? = null) {
            val intent = Intent(context, ScreeningActivity::class.java).apply {
                putExtra("user_id", userId ?: "itsLuxra")
            }
            context.startActivity(intent)
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

        userId = intent.getStringExtra("user_id") ?: "itsLuxra"
        setupFullscreenMode()
        setupNavigation()
        setupBackPressHandler()
        initializeScreeningSession()
    }

    private fun setupFullscreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupNavigation() {
        navController = findNavController(R.id.nav_host_fragment_screening)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateScreeningProgress(destination.id)
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmation()
            }
        })
    }

    private fun initializeScreeningSession() {
        ScreeningDataManager.startNewSession(this, userId)
    }

    private fun updateScreeningProgress(destinationId: Int) {
        val progressSteps = mapOf(
            R.id.sensorTestFragment to "Sensor Test",
            R.id.cameraTestFragment to "Camera Test",
            R.id.micTestFragment to "Mic Test",
            R.id.screeningResultFragment to "Results"
        )
        val currentStep = progressSteps[destinationId] ?: ""
        binding.tvProgressIndicator?.text = currentStep
    }

    private fun showExitConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Keluar dari Screening?")
            .setMessage("Apakah Anda yakin ingin keluar dari proses screening?\nProgress akan hilang.")
            .setPositiveButton("Ya, Keluar") { _, _ ->
                exitScreening()
            }
            .setNegativeButton("Lanjutkan") { dialog, _ ->
                dialog.dismiss()
            }
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