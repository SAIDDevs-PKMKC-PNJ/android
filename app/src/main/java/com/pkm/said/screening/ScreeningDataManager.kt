package com.pkm.said.screening

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class ScreeningResult(
    val sessionId: String,
    val timestamp: String,
    val userId: String,
    val faceResult: TestResult? = null,        // F - Face Test
    val armsResult: TestResult? = null,        // A - Arms Test
    val speechResult: TestResult? = null,      // S - Speech Test
//    val timeResult: TestResult? = null,        // T - Time Test
    val overallRisk: RiskLevel = RiskLevel.UNKNOWN,
    val isCompleted: Boolean = false,
    val completedAt: String? = null
)

data class TestResult(
    val testName: String,
    val isCompleted: Boolean,
    val isSuccessful: Boolean,
    val score: Float = 0f,
    val notes: String = "",
    val timestamp: String,
    val duration: Long = 0L,                   // Duration dalam milliseconds
    val testData: Map<String, Any> = emptyMap() // Generic data untuk setiap test
)

enum class RiskLevel(val displayName: String, val description: String) {
    LOW("Risiko Rendah", "Tidak ada indikasi stroke terdeteksi"),
    MEDIUM("Risiko Sedang", "Beberapa gejala ringan terdeteksi"),
    HIGH("Risiko Tinggi", "Beberapa gejala signifikan terdeteksi"),
    CRITICAL("Risiko Kritis", "Segera konsultasi dengan tenaga medis"),
    UNKNOWN("Tidak Diketahui", "Data tidak cukup untuk analisis")
}

object ScreeningDataManager {
    private const val TAG = "ScreeningDataManager"
    private const val PREFS_NAME = "fast_screening_data"
    private const val KEY_CURRENT_SESSION = "current_session"
    private const val KEY_SESSIONS_HISTORY = "sessions_history"
    private const val KEY_USER_PROFILE = "user_profile"

    private var currentSession: ScreeningResult? = null

    /**
     * Memulai sesi screening FAST baru
     */
    fun startNewSession(context: Context, userId: String): ScreeningResult {
        val sessionId = "FAST_${System.currentTimeMillis()}"
        val timestamp = getCurrentTimestamp()

        currentSession = ScreeningResult(
            sessionId = sessionId,
            timestamp = timestamp,
            userId = userId
        )

        saveCurrentSession(context)
        Log.d(TAG, "New FAST screening session started: $sessionId")
        return currentSession!!
    }

    /**
     * Update hasil test berdasarkan nama test
     */
    fun updateTestResult(context: Context, testResult: TestResult) {
        currentSession?.let { session ->
            val updatedSession = when (testResult.testName.lowercase()) {
                "face_test", "face" -> session.copy(faceResult = testResult)
                "arms_test", "arms" -> session.copy(armsResult = testResult)
                "speech_test", "speech" -> session.copy(speechResult = testResult)
//                "time_test", "time" -> session.copy(timeResult = testResult)
                else -> {
                    Log.w(TAG, "Unknown test name: ${testResult.testName}")
                    session
                }
            }

            currentSession = updatedSession
            saveCurrentSession(context)
            Log.d(TAG, "Test result updated: ${testResult.testName}")
        } ?: run {
            Log.e(TAG, "No active session to update")
        }
    }

    /**
     * Menyelesaikan sesi screening
     */
    fun completeSession(context: Context): ScreeningResult? {
        currentSession?.let { session ->
            val completedSession = session.copy(
                isCompleted = true,
                overallRisk = calculateFASTRisk(session),
                completedAt = getCurrentTimestamp()
            )

            saveToHistory(context, completedSession)
            clearCurrentSession(context)
            currentSession = null

            Log.d(TAG, "Screening session completed: ${completedSession.sessionId}")
            return completedSession
        }
        return null
    }

    /**
     * Mendapatkan sesi yang sedang aktif
     */
    fun getCurrentSession(context: Context): ScreeningResult? {
        if (currentSession == null) {
            loadCurrentSession(context)
        }
        return currentSession
    }

    /**
     * Mendapatkan semua hasil test dari sesi aktif
     */
    fun getAllResults(context: Context): List<TestResult> {
        val session = getCurrentSession(context) ?: return emptyList()
        return listOfNotNull(
            session.faceResult,
            session.armsResult,
            session.speechResult
//            ,session.timeResult
        )
    }

    /**
     * Mendapatkan progress sesi saat ini (0.0 - 1.0)
     */
    fun getSessionProgress(context: Context): Float {
        val results = getAllResults(context)
        val completedTests = results.count { it.isCompleted }
        return completedTests / 4f // 4 adalah total test FAST
    }

    /**
     * Mendapatkan test yang belum selesai
     */
    fun getPendingTests(context: Context): List<String> {
        val session = getCurrentSession(context) ?: return listOf("face_test", "arms_test", "speech_test", "time_test")
        val pending = mutableListOf<String>()

        if (session.faceResult?.isCompleted != true) pending.add("face_test")
        if (session.armsResult?.isCompleted != true) pending.add("arms_test")
        if (session.speechResult?.isCompleted != true) pending.add("speech_test")
//        if (session.timeResult?.isCompleted != true) pending.add("time_test")

        return pending
    }

    /**
     * Membatalkan sesi yang sedang berjalan
     */
    fun cancelSession(context: Context) {
        currentSession?.let {
            Log.d(TAG, "Session cancelled: ${it.sessionId}")
        }
        clearCurrentSession(context)
        currentSession = null
    }

    /**
     * Mendapatkan riwayat screening
     */
    fun getScreeningHistory(context: Context): List<ScreeningResult> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val existingJson = prefs.getString(KEY_SESSIONS_HISTORY, "[]")
        val listType = object : TypeToken<List<ScreeningResult>>() {}.type
        return gson.fromJson(existingJson, listType) ?: emptyList()
    }

    /**
     * Menghitung risiko berdasarkan hasil FAST test
     */
    private fun calculateFASTRisk(session: ScreeningResult): RiskLevel {
        val allTests = listOfNotNull(
            session.faceResult,
            session.armsResult,
            session.speechResult
//            ,session.timeResult
        )

        val completedTests = allTests.filter { it.isCompleted }
        if (completedTests.isEmpty()) return RiskLevel.UNKNOWN

        val failedTests = completedTests.count { !it.isSuccessful }
        val averageScore = completedTests.map { it.score }.average()

        return when {
            failedTests == 0 && averageScore >= 0.8 -> RiskLevel.LOW
            failedTests == 1 || averageScore >= 0.6 -> RiskLevel.MEDIUM
            failedTests == 2 || averageScore >= 0.4 -> RiskLevel.HIGH
            failedTests >= 3 || averageScore < 0.4 -> RiskLevel.CRITICAL
            else -> RiskLevel.UNKNOWN
        }
    }

    /**
     * Menyimpan sesi saat ini ke SharedPreferences
     */
    private fun saveCurrentSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(currentSession)
        prefs.edit().putString(KEY_CURRENT_SESSION, json).apply()
    }

    /**
     * Memuat sesi dari SharedPreferences
     */
    private fun loadCurrentSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString(KEY_CURRENT_SESSION, null)
        if (json != null) {
            try {
                currentSession = gson.fromJson(json, ScreeningResult::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading current session", e)
                clearCurrentSession(context)
            }
        }
    }

    /**
     * Menyimpan ke riwayat
     */
    private fun saveToHistory(context: Context, session: ScreeningResult) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val existingJson = prefs.getString(KEY_SESSIONS_HISTORY, "[]")
        val listType = object : TypeToken<MutableList<ScreeningResult>>() {}.type
        val history: MutableList<ScreeningResult> = gson.fromJson(existingJson, listType) ?: mutableListOf()

        // Tambah ke awal list (terbaru di atas)
        history.add(0, session)

        // Batasi maksimal 20 riwayat
        if (history.size > 20) {
            history.removeAt(history.size - 1)
        }

        val updatedJson = gson.toJson(history)
        prefs.edit().putString(KEY_SESSIONS_HISTORY, updatedJson).apply()
    }

    /**
     * Menghapus sesi saat ini
     */
    private fun clearCurrentSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CURRENT_SESSION).apply()
    }

    /**
     * Mendapatkan timestamp saat ini
     */
    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * Helper untuk debugging
     */
    fun getSessionInfo(context: Context): String {
        val session = getCurrentSession(context) ?: return "No active session"
        val results = getAllResults(context)
        val progress = getSessionProgress(context)

        return """
            Session ID: ${session.sessionId}
            User ID: ${session.userId}
            Started: ${session.timestamp}
            Progress: ${(progress * 100).toInt()}%
            Completed Tests: ${results.count { it.isCompleted }}/4
            Current Risk: ${session.overallRisk.displayName}
        """.trimIndent()
    }
}