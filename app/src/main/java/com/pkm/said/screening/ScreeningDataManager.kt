package com.pkm.said.screening

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlin.math.roundToInt

data class ScreeningResult(
    val sessionId: String,
    val timestamp: String,
    val userId: String,
    val faceResult: TestResult? = null,        // F - Face
    val armsResult: TestResult? = null,        // A - Arms
    val speechResult: TestResult? = null,      // S - Speech
    val overallRisk: RiskLevel = RiskLevel.UNKNOWN,
    val isCompleted: Boolean = false,
    val completedAt: String? = null
)

data class TestResult(
    val testName: String,
    val isCompleted: Boolean,
    val isSuccessful: Boolean,                 // true = normal
    val score: Float = 0f,                     // severity 0..1 (0 normal → 1 sangat abnormal)
    val notes: String = "",
    val timestamp: String,
    val duration: Long = 0L,                   // ms
    val testData: Map<String, Any> = emptyMap()
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

    // FAST coverage maksimum (tanpa B/E)
    private const val FAST_MAX_COVERAGE = 0.80f   // 80%
    private const val TOTAL_FAST_TESTS = 3        // Face, Arms, Speech

    private var currentSession: ScreeningResult? = null

    /** Memulai sesi screening baru (idempotent: panggil kalau belum ada sesi) */
    fun startNewSession(context: Context, userId: String): ScreeningResult {
        val existing = getCurrentSession(context)
        if (existing != null) return existing

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

    /** Update hasil test; mapping nama tes dibuat fleksibel */
    fun updateTestResult(context: Context, testResult: TestResult) {
        currentSession?.let { session ->
            val name = testResult.testName.lowercase()
            val updatedSession = when (name) {
                // FACE
                "face_test", "face", "befast_face" -> session.copy(faceResult = testResult)
                // ARMS
                "arms_test", "arms", "arm_test", "arm", "befast_arm" -> session.copy(armsResult = testResult)
                // SPEECH
                "speech_test", "speech", "befast_speech" -> session.copy(speechResult = testResult)
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

    /** Menyelesaikan sesi: hitung risiko, simpan ke history, bersihkan sesi aktif */
    fun completeSession(context: Context): ScreeningResult? {
        val session = getCurrentSession(context) ?: return null
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

    /** Mengambil sesi berjalan (lazy-load dari prefs) */
    fun getCurrentSession(context: Context): ScreeningResult? {
        if (currentSession == null) loadCurrentSession(context)
        return currentSession
    }

    /** Semua hasil test dari sesi aktif */
    fun getAllResults(context: Context): List<TestResult> {
        val session = getCurrentSession(context) ?: return emptyList()
        return listOfNotNull(session.faceResult, session.armsResult, session.speechResult)
    }

    /** Progress sesi saat ini (0..1) — 3 tes FAST */
    fun getSessionProgress(context: Context): Float {
        val results = getAllResults(context)
        val completedTests = results.count { it.isCompleted }
        return completedTests / TOTAL_FAST_TESTS.toFloat()
    }

    fun getScreeningById(context: Context, sessionId: String): ScreeningResult? {
        return getScreeningHistory(context).firstOrNull { it.sessionId == sessionId }
    }

    /** Daftar tes yang belum selesai (tanpa T) */
    fun getPendingTests(context: Context): List<String> {
        val session = getCurrentSession(context)
            ?: return listOf("face_test", "arms_test", "speech_test")
        val pending = mutableListOf<String>()
        if (session.faceResult?.isCompleted != true) pending.add("face_test")
        if (session.armsResult?.isCompleted != true) pending.add("arms_test")
        if (session.speechResult?.isCompleted != true) pending.add("speech_test")
        return pending
    }

    /** Riwayat screening (maks 20 entri) */
    fun getScreeningHistory(context: Context): List<ScreeningResult> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val existingJson = prefs.getString(KEY_SESSIONS_HISTORY, "[]")
        val listType = object : TypeToken<List<ScreeningResult>>() {}.type
        return gson.fromJson(existingJson, listType) ?: emptyList()
    }

    /** Hitung FAST overall percent (0..80) dari rata-rata severity 0..1 */
    fun calculateFASTOverallPercent(session: ScreeningResult): Int {
        val severities = collectFastSeverities(session)
        if (severities.isEmpty()) return 0
        val meanSeverity = severities.average().toFloat()               // 0..1
        val percent = (meanSeverity * FAST_MAX_COVERAGE * 100f)         // 0..80
        return percent.roundToInt().coerceIn(0, 80)
    }

    /** ====================== INTERNALS ====================== */

    /** Kumpulkan severity 0..1 dari tes FAST yang selesai */
    private fun collectFastSeverities(session: ScreeningResult): List<Float> {
        val s = mutableListOf<Float>()
        session.faceResult?.let { if (it.isCompleted) s.add(it.score.coerceIn(0f, 1f)) }
        session.armsResult?.let { if (it.isCompleted) s.add(it.score.coerceIn(0f, 1f)) }
        session.speechResult?.let { if (it.isCompleted) s.add(it.score.coerceIn(0f, 1f)) }
        return s
    }

    /** Hitung level risiko berdasarkan overall FAST % dan jumlah segmen fail */
    private fun calculateFASTRisk(session: ScreeningResult): RiskLevel {
        val severities = collectFastSeverities(session)
        if (severities.isEmpty()) return RiskLevel.UNKNOWN

        val overallPercent = calculateFASTOverallPercent(session) // 0..80
        val failed = listOfNotNull(session.faceResult, session.armsResult, session.speechResult)
            .count { it.isCompleted && !it.isSuccessful }

        // Ambang dapat kamu tuning sesuai kebutuhan
        return when {
            overallPercent >= 60 || failed >= 2 -> RiskLevel.CRITICAL
            overallPercent >= 40 || failed >= 1 -> RiskLevel.HIGH
            overallPercent >= 20 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    /** Simpan sesi aktif ke prefs */
    private fun saveCurrentSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(currentSession)
        prefs.edit().putString(KEY_CURRENT_SESSION, json).apply()
    }

    /** Load sesi aktif dari prefs */
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

    /** Simpan hasil selesai ke history */
    private fun saveToHistory(context: Context, session: ScreeningResult) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val existingJson = prefs.getString(KEY_SESSIONS_HISTORY, "[]")
        val listType = object : TypeToken<MutableList<ScreeningResult>>() {}.type
        val history: MutableList<ScreeningResult> =
            gson.fromJson(existingJson, listType) ?: mutableListOf()

        history.add(0, session)                 // newest first
        if (history.size > 20) history.removeAt(history.size - 1)

        val updatedJson = gson.toJson(history)
        prefs.edit().putString(KEY_SESSIONS_HISTORY, updatedJson).apply()
    }

    fun getLastCompletedSession(context: Context): ScreeningResult? {
        return getScreeningHistory(context).firstOrNull { it.isCompleted }
    }

    fun getRiskLabel(result: ScreeningResult): String {
        return result.overallRisk.displayName
    }

    /** Hapus sesi aktif dari prefs */
    private fun clearCurrentSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CURRENT_SESSION).apply()
    }

    fun cancelSession(context: Context) {
        currentSession?.let {
            Log.d(TAG, "Session cancelled: ${it.sessionId}")
        }
        clearCurrentSession(context)
        currentSession = null
    }

    /** Timestamp helper */
    fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    /** Debug helper */
    fun getSessionInfo(context: Context): String {
        val session = getCurrentSession(context) ?: return "No active session"
        val results = getAllResults(context)
        val progress = getSessionProgress(context)
        val fastPercent = calculateFASTOverallPercent(session)
        return """
            Session ID: ${session.sessionId}
            User ID   : ${session.userId}
            Started   : ${session.timestamp}
            Progress  : ${(progress * 100).roundToInt()}%
            Completed : ${results.count { it.isCompleted }}/$TOTAL_FAST_TESTS
            FAST %    : $fastPercent% (max 80%)
            Current Risk: ${calculateFASTRisk(session).displayName}
        """.trimIndent()
    }

    fun clearAll(context: Context) {
        currentSession = null

        // bersihkan semua key yang dipakai manager ini
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_CURRENT_SESSION)
            .remove(KEY_SESSIONS_HISTORY)
            .remove(KEY_USER_PROFILE) // kalau memang dipakai
            .apply()
    }
}
