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
import com.pkm.said.adapter.ListHistoryAdapter
import com.pkm.said.screening.ScreeningActivity
import com.pkm.said.screening.ScreeningDataManager
import com.pkm.said.screening.ScreeningRepository
import com.pkm.said.screening.ScreeningResult
import com.pkm.said.screening.completedAtFormatted
import com.pkm.said.util.AuthManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ScreeningManagementActivity : AppCompatActivity() {

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
        Log.d(TAG, "=== SCREENING MANAGEMENT END ===")
    }
}