package com.pkm.said

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.pkm.said.adapter.ListHistoryAdapter
import com.pkm.said.screening.ScreeningActivity
import com.pkm.said.screening.ScreeningDataManager
import com.pkm.said.screening.ScreeningRepository
import com.pkm.said.screening.ScreeningResult
import com.pkm.said.screening.completedAtFormatted
import com.pkm.said.util.AuthManager
import com.pkm.said.util.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ScreeningManagementActivity : AppCompatActivity(), Said.VoiceActivityCallback {

    private lateinit var buttonScan: CardView
    private lateinit var btnBack: ImageButton
    private lateinit var btnSettings: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var groupEmptyHistory: android.widget.LinearLayout
    private lateinit var recyclerViewHistory: RecyclerView
    private lateinit var progressBarHistory: androidx.core.widget.ContentLoadingProgressBar

    private lateinit var historyAdapter: ListHistoryAdapter

    companion object {
        private const val TAG = "ScreeningManagement"

        fun start(context: Context) {
            val intent = Intent(context, ScreeningManagementActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "=== SCREENING MANAGEMENT START ===")
        Log.d(TAG, "Current Time: ${getCurrentTimestamp()}")

        try {
            setContentView(R.layout.activity_screening_management)
            Log.d(TAG, "✅ Layout set successfully")

            Said.getInstance().registerActivityCallback(this.localClassName, this)
            initViews()
            setupViews()
            loadScreeningHistory()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up activity", e)
            Toast.makeText(this, "Error loading screening management", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initViews() {
        Log.d(TAG, "🔧 Initializing views...")

        buttonScan = findViewById(R.id.buttonScan)
        btnBack = findViewById(R.id.btnBack)
        btnSettings = findViewById(R.id.fabSettings)
        recyclerViewHistory = findViewById(R.id.recyclerViewHistory)
        progressBarHistory = findViewById(R.id.progressBarHistory)
        groupEmptyHistory = findViewById(R.id.layoutEmptyHistory)

        // Setup RecyclerView
        historyAdapter = ListHistoryAdapter { historyItem ->
            // Handle item click - navigate to detail atau restart screening
            Log.d(TAG, "History item clicked: ${historyItem.timestamp}")
        }

        recyclerViewHistory.apply {
            layoutManager = LinearLayoutManager(this@ScreeningManagementActivity)
            adapter = historyAdapter
        }

        Log.d(TAG, "✅ Views initialized successfully")
    }

    private fun setupViews() {
        Log.d(TAG, "🔧 Setting up view listeners...")

        try {
            // Scan button dengan dialog konfirmasi
            buttonScan.setOnClickListener {
                Log.d(TAG, "🔘 Scan button clicked at ${getCurrentTimestamp()}")
                showOutdoorConfirmationDialog()
            }

            // Back button
            btnBack.setOnClickListener {
                Log.d(TAG, "🔙 Back button clicked")
                onBackPressedDispatcher.onBackPressed()
            }

            // Settings button untuk notification reminder
            btnSettings.setOnClickListener {
                Log.d(TAG, "⚙️ Settings button clicked")
                NotificationSettingsActivity.start(this)
            }

            // See all history
            findViewById<android.widget.TextView>(R.id.tvSeeAll)?.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("navigate_to", "history")
                }
                startActivity(intent)
            }

            Log.d(TAG, "✅ View listeners set successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up view listeners", e)
        }
    }

    override fun onVoiceCommand(command: String, extras: Bundle?): Boolean {
        Log.d(TAG, "🎤 Voice command received in screening management: $command")
        return when (command.toLowerCase()) {
            // SCREENING - sama seperti MainActivity
            "tes stroke", "mulai screening", "screening", "mulai tes" -> {
                startStrokeScreening()
                true
            }
            // EMERGENCY - sama seperti MainActivity
            "darurat", "emergency", "tolong" -> {
                handleEmergencyFromVoice()
                true
            }
            // DASHBOARD - kembali ke MainActivity
            "dashboard", "home", "kembali" -> {
                navigateToDashboard()
                true
            }
            else -> false
        }
    }

    // ✅ NAVIGATION - UPDATE UNTUK SCREENING & EMERGENCY
    override fun onNavigateTo(destination: String): Boolean {
        Log.d(TAG, "🧭 Navigation command in screening management: $destination")
        return when (destination.toLowerCase()) {
            "dashboard", "home" -> {
                navigateToDashboard()
                true
            }
            "screening" -> {
                startStrokeScreening()
                true
            }
            "emergency" -> {
                handleEmergencyFromVoice()
                true
            }
            else -> false
        }
    }

    // ✅ SUPPORTED COMMANDS - UPDATE DENGAN SCREENING & EMERGENCY
    override fun getSupportedCommands(): List<String> {
        return listOf(
            "tes stroke", "mulai screening", "screening", "mulai tes",
            "darurat", "emergency", "tolong",
            "dashboard", "home", "kembali"
        )
    }

    // ✅ STROKE SCREENING - SAMA SEPERTI DI MAINACTIVITY
    private fun startStrokeScreening() {
        try {
            Log.d(TAG, "🏥 Starting stroke screening from screening management...")

            // Dapatkan username seperti di MainActivity
            val username = getCurrentUsername()
            Log.d(TAG, "Username: $username")

            // ✅ GUNAKAN METHOD start() DARI SCREENINGACTIVITY - sama seperti MainActivity
            ScreeningActivity.start(this, username, startNew = true)

            if (!isFinishing && !isDestroyed) {
                Toast.makeText(
                    this,
                    "🏥 Starting screening for $username",
                    Toast.LENGTH_SHORT
                ).show()
            }
            Log.d(TAG, "✅ ScreeningActivity started successfully from screening management")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting stroke screening from screening management", e)
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "❌ Failed to start screening", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ EMERGENCY HANDLER - SAMA SEPERTI DI MAINACTIVITY
    private fun handleEmergencyFromVoice() {
        try {
            Log.d(TAG, "🚨 Emergency from voice command in screening management - starting EmergencyActivity")
            val intent = Intent(this, EmergencyActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_voice_command", true)
                putExtra("from_screening_management", true) // Tambahkan identifier
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start EmergencyActivity from screening management, using fallback", e)
            // Fallback ke dialog emergency
            showEmergencyFallbackDialog()
        }
    }

    // ✅ EMERGENCY FALLBACK DIALOG - SAMA SEPERTI DI MAINACTIVITY
    private fun showEmergencyFallbackDialog() {
        Log.d(TAG, "🚨 Emergency fallback in screening management - showing dialog")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🚨 Emergency Detected")
            .setMessage("Voice assistant detected emergency situation. Please manually open emergency features.")
            .setPositiveButton("Open Emergency") { _, _ ->
                // Try to start EmergencyActivity again dengan approach berbeda
                try {
                    val intent = Intent(this, EmergencyActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("from_screening_management", true)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open emergency screen", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "❌ Emergency fallback also failed in screening management", e)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    // ✅ GET CURRENT USERNAME - SAMA SEPERTI DI MAINACTIVITY
    private fun getCurrentUsername(): String {
        return try {
            SessionManager.getUserName(this) ?: getFallbackUsername()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting username in screening management", e)
            getFallbackUsername()
        }
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
        Log.d(TAG, "Generated anonymous username in screening management: $anonymousUser")
        return anonymousUser
    }

    private fun navigateToDashboard() {
        try {
            Log.d(TAG, "🚀 Navigating to Dashboard - finishing ScreeningManagementActivity")

            // Cukup finish() karena MainActivity sudah default ke dashboard
            finish()

            // Optional: smooth transition animation
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

            Log.d(TAG, "✅ Navigation to dashboard completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to dashboard", e)
            // Fallback - tetap coba finish
            finish()
        }
    }

    private fun showOutdoorConfirmationDialog() {
        Log.d(TAG, "🏡 Showing outdoor confirmation dialog")

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_screening_confirmation, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<android.widget.Button>(R.id.btnCancel).setOnClickListener {
            Log.d(TAG, "❌ User cancelled outdoor confirmation")
            dialog.dismiss()
        }

        dialogView.findViewById<android.widget.Button>(R.id.btnContinue).setOnClickListener {
            Log.d(TAG, "✅ User confirmed outdoor location")
            dialog.dismiss()
            startScreeningProcess()
        }

        dialog.show()
    }

    private fun startScreeningProcess() {
        try {
            Log.d(TAG, "🚀 Starting screening process at ${getCurrentTimestamp()}")

            // Launch screening activity
            ScreeningActivity.start(this, userId = null)

            Log.d(TAG, "✅ Screening activity launched successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting screening process", e)
            Toast.makeText(this, "Error starting screening: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadScreeningHistory() {
        Log.d(TAG, "📚 Loading screening history...")

        progressBarHistory.isVisible = true
        groupEmptyHistory.isVisible = false
        recyclerViewHistory.isVisible = false

        lifecycleScope.launch {
            try {
                // Ambil remote dan lokal secara paralel
                val remoteHistory = runCatching { ScreeningRepository.getScreeningHistory(5) }
                    .getOrElse { e ->
                        Log.e(TAG, "Error fetching remote history", e)
                        emptyList<ScreeningResult>()
                    }

                val localHistory = runCatching { ScreeningDataManager.getScreeningHistory(this@ScreeningManagementActivity) }
                    .getOrElse { e ->
                        Log.e(TAG, "Error fetching local history", e)
                        emptyList<ScreeningResult>()
                    }

                // Gabungkan, remove duplikat, sort by timestamp desc, ambil 5 terakhir
                val combinedHistory = (remoteHistory + localHistory)
                    .distinctBy { it.sessionId } // sessionId unik
                    .sortedByDescending { it.completedAtFormatted() } // descending timestamp string
                    .take(5)

                progressBarHistory.isVisible = false

                if (combinedHistory.isEmpty()) {
                    groupEmptyHistory.isVisible = true
                    recyclerViewHistory.isVisible = false
                    Log.d(TAG, "📭 No history found - showing empty state")
                } else {
                    groupEmptyHistory.isVisible = false
                    recyclerViewHistory.isVisible = true

                    val historyItems = combinedHistory.map { session ->
                        ScreeningHistoryItem(
                            id = session.sessionId,
                            timestamp = session.timestamp,
                            riskStatus = ScreeningDataManager.getRiskLabel(session),
                            scorePercent = ScreeningDataManager.calculateBEFASTOverallPercent(session),
                            riskLevel = session.overallRisk.name,
                            formattedDate = formatTimestamp(session.completedAtFormatted())
                        )
                    }

                    historyAdapter.submitList(historyItems)
                    Log.d(TAG, "✅ History displayed successfully: ${historyItems.size} items")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading screening history", e)
                progressBarHistory.isVisible = false
                groupEmptyHistory.isVisible = true
                recyclerViewHistory.isVisible = false

                Toast.makeText(
                    this@ScreeningManagementActivity,
                    "Error loading history: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private fun formatTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            Log.w(TAG, "Error formatting timestamp: $timestamp", e)
            timestamp
        }
    }

    private fun getCurrentTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date())
    }

    override fun onResume() {
        super.onResume()
        // Refresh history when returning from screening
        loadScreeningHistory()
        if (!AuthManager.ensureUserLoggedIn(this)) return
    }

    override fun onDestroy() {
        super.onDestroy()
        Said.getInstance().unregisterActivityCallback(this.localClassName)
        Log.d(TAG, "=== SCREENING MANAGEMENT END ===")
    }
}