package com.pkm.said.screening

import android.R
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.pkm.said.util.FaceLandmarkerHelper
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// ===================== Domain Models =====================
data class ScreeningResult(
    val sessionId: String,
    val timestamp: String,
    val userId: String,
    val balanceResult: TestResult? = null,      // B - Balance
    val eyesResult: TestResult? = null,         // E - Eyes
    val faceResult: TestResult? = null,         // F - Face
    val armsResult: TestResult? = null,         // A - Arms
//    val speechResult: TestResult? = null,
    val city: String? = null,
    val overallRisk: RiskLevel = RiskLevel.UNKNOWN,
    val isCompleted: Boolean = false,
    val completedAt: Timestamp? = null,
    val completedAtText: String? = null
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

// Extension function untuk formatting
fun ScreeningResult.completedAtFormatted(): String =
    completedAt?.toDate()?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it)
    } ?: completedAtText ?: "-"

// ===================== Cache DTO =====================
private data class ScreeningResultCache(
    val sessionId: String,
    val timestamp: String,
    val userId: String,
    val balanceResult: TestResult? = null,
    val eyesResult: TestResult? = null,
    val faceResult: TestResult? = null,
    val armsResult: TestResult? = null,
//    val speechResult: TestResult? = null,
    val city: String? = null,
    val overallRisk: RiskLevel = RiskLevel.UNKNOWN,
    val isCompleted: Boolean = false,
    val completedAtText: String? = null
)

private fun ScreeningResult.toCache() = ScreeningResultCache(
    sessionId = sessionId,
    timestamp = timestamp,
    userId = userId,
    balanceResult = balanceResult,
    eyesResult = eyesResult,
    faceResult = faceResult,
    armsResult = armsResult,
//    speechResult =  speechResult,
    city = city,
    overallRisk = overallRisk,
    isCompleted = isCompleted,
    completedAtText = completedAtText ?: completedAt?.toDate()?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it)
    }
)

private fun ScreeningResultCache.toDomain() = ScreeningResult(
    sessionId = sessionId,
    timestamp = timestamp,
    userId = userId,
    balanceResult = balanceResult,
    eyesResult = eyesResult,
    faceResult = faceResult,
    armsResult = armsResult,
//    speechResult =  speechResult,
    city = city,
    overallRisk = overallRisk,
    isCompleted = isCompleted,
    completedAt = null,
    completedAtText = completedAtText
)

// ===================== Main Manager =====================
object ScreeningDataManager {
    private const val TAG = "ScreeningDataManager"
    private const val PREFS_NAME = "fast_screening_data"
    private const val KEY_CURRENT_SESSION = "current_session"
    private const val KEY_SESSIONS_HISTORY = "sessions_history"

    private const val TOTAL_BEFAST_TESTS = 5  // Balance, Eyes, Face, Arms

    private var currentSession: ScreeningResult? = null

    /** Mulai sesi baru - HANYA LOKAL */
    fun startNewSession(context: Context, userId: String): ScreeningResult {
        currentSession?.let { return it }

        val sessionId = "BEFAST_${System.currentTimeMillis()}"
        val timestamp = getCurrentTimestamp()
        val session = ScreeningResult(sessionId, timestamp, userId)

        currentSession = session
        saveCurrentSession(context)
        Log.d(TAG, "📝 Sesi screening baru dimulai: $sessionId")
        return session
    }

    fun updateEyesRawData(context: Context, capturedDataList: List<FaceLandmarkerHelper.CapturedExpressionData>) {
        currentSession?.let { session ->
            val existingEyesResult = session.eyesResult

            if (existingEyesResult == null) {
                Log.w(TAG, "Tidak dapat menyimpan data mentah mata: TestResult 'eyes' belum dibuat.")
                return
            }

            // 1. Serialize data mentah menjadi JSON String agar aman disimpan di Map<String, Any>
            val rawDataJson = Gson().toJson(capturedDataList)

            // 2. Buat TestResult baru dengan data mentah ditambahkan
            val updatedTestData = existingEyesResult.testData.toMutableMap()

            // Simpan data mentah di bawah kunci spesifik (misalnya: "eyes_raw_captures_json")
            updatedTestData["eyes_raw_captures_json"] = rawDataJson
            updatedTestData["total_captures"] = capturedDataList.size

            val updatedEyesResult = existingEyesResult.copy(
                testData = updatedTestData
            )

            // 3. Update sesi dan simpan ke SharedPreferences
            val updatedSession = session.copy(eyesResult = updatedEyesResult)
            currentSession = updatedSession
            saveCurrentSession(context)
            Log.d(TAG, "✅ Data mentah mata (${capturedDataList.size} captures) disimpan lokal.")

        } ?: Log.e(TAG, "❌ Tidak ada sesi aktif untuk menyimpan data mentah mata.")
    }

    /** Update hasil test - HANYA LOKAL */
    fun updateTestResult(context: Context, testResult: TestResult) {
        currentSession?.let { session ->
            // Standardize test names untuk konsistensi
            val standardizedTest = when (testResult.testName.lowercase()) {
                "balance_test", "balance", "b" -> testResult.copy(testName = "befast_balance")
                "eyes_test", "eyes", "e" -> testResult.copy(testName = "befast_eyes")
                "face_test", "face", "f" -> testResult.copy(testName = "befast_face")
                "arms_test", "arms", "a" -> testResult.copy(testName = "befast_arms")
//                "speech_test", "speech", "s" -> testResult.copy(testName = "befast_speech")
                else -> testResult
            }

            val name = standardizedTest.testName.lowercase()
            val updatedSession = when {
                "balance" in name -> session.copy(balanceResult = standardizedTest)
                "eyes" in name -> session.copy(eyesResult = standardizedTest)
                "face" in name -> session.copy(faceResult = standardizedTest)
                "arms" in name -> session.copy(armsResult = standardizedTest)
//                "speech" in name -> session.copy(speechResult = standardizedTest)
                else -> {
                    Log.w(TAG, "Unknown test name: ${testResult.testName}")
                    session
                }
            }

            currentSession = updatedSession
            saveCurrentSession(context)
            Log.d(TAG, "✅ Test result disimpan lokal: ${standardizedTest.testName}")
        } ?: Log.e(TAG, "❌ Tidak ada sesi aktif untuk update test")
    }

    /** Selesaikan sesi - HANYA LOKAL, siap untuk dikirim ke Firestore */
    fun completeSession(context: Context, city: String? = null): ScreeningResult? {
        val session = getCurrentSession(context) ?: return null

        // Validasi: semua tes harus selesai
        if (!areAllTestsCompleted(context)) {
            Log.w(TAG, "❌ Tidak bisa complete: ada tes yang belum selesai")
            return null
        }

        val errorValues = setOf("not permitted", "location null", "location error", "getting location...")

        // Tentukan nilai kota final
        val finalCity = if (city.isNullOrBlank() || errorValues.contains(city.lowercase())) {
            "SAID"
        } else {
            city
        }

        val completedSession = session.copy(
            city = finalCity,
            isCompleted = true,
            overallRisk = calculateBEFASTRisk(session),
            completedAt = getCurrentTimestampTs(),
            completedAtText = getCurrentTimestamp()
        )

        // Simpan ke history lokal
        saveToHistory(context, completedSession)

        // Clear current session - user harus mulai dari awal jika mau tes lagi
        clearCurrentSession(context)

        Log.d(TAG, "🎉 Sesi screening completed: ${completedSession.sessionId}")
        return completedSession
    }

    /** Cek apakah semua tes sudah selesai */
    fun areAllTestsCompleted(context: Context): Boolean {
        val session = getCurrentSession(context) ?: return false
        val tests = listOf(
            session.balanceResult,
            session.eyesResult,
            session.faceResult,
            session.armsResult
//            session.speechResult
        )
        return tests.all { it != null && it.isCompleted }
    }

    /** Progress sesi saat ini (0..1) */
    fun getSessionProgress(context: Context): Float {
        val session = getCurrentSession(context) ?: return 0f
        val tests = listOf(
            session.balanceResult,
            session.eyesResult,
            session.faceResult,
            session.armsResult
//            session.speechResult
        )
        val completedTests = tests.count { it?.isCompleted == true }
        return completedTests / TOTAL_BEFAST_TESTS.toFloat()
    }

    /** Ambil sesi berjalan */
    fun getCurrentSession(context: Context): ScreeningResult? {
        if (currentSession == null) loadCurrentSession(context)
        return currentSession
    }

    /** Semua hasil test dari sesi aktif */
    fun getAllResults(context: Context): List<TestResult> {
        val session = getCurrentSession(context) ?: return emptyList()
        return listOfNotNull(
            session.balanceResult,
            session.eyesResult,
            session.faceResult,
            session.armsResult
//            session.speechResult
        )
    }

    /** Tes yang belum selesai */
    fun getPendingTests(context: Context): List<String> {
        val session = getCurrentSession(context) ?: return listOf("balance", "eyes", "face", "arms")
        val pending = mutableListOf<String>()
        if (session.balanceResult?.isCompleted != true) pending.add("balance")
        if (session.eyesResult?.isCompleted != true) pending.add("eyes")
        if (session.faceResult?.isCompleted != true) pending.add("face")
        if (session.armsResult?.isCompleted != true) pending.add("arms")
//        if (session.speechResult?.isCompleted != true) pending.add("speech")
        return pending
    }

    /** Get last completed session untuk dashboard */
    fun getLastCompletedSession(context: Context): ScreeningResult? {
        return getScreeningHistory(context).firstOrNull { it.isCompleted }
    }

    /** Riwayat screening (maks 20 entri) */
    fun getScreeningHistory(context: Context): List<ScreeningResult> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SESSIONS_HISTORY, "[]") ?: "[]"
        val type = object : TypeToken<List<ScreeningResultCache>>() {}.type
        return runCatching {
            val caches: List<ScreeningResultCache> = Gson().fromJson(json, type) ?: emptyList()
            caches.map { it.toDomain() }
        }.getOrElse { emptyList() }
    }

    /** BEFAST overall percent 0..100 */
    fun calculateBEFASTOverallPercent(session: ScreeningResult): Int {
        val severities = listOfNotNull(
            session.balanceResult,
            session.eyesResult,
            session.faceResult,
            session.armsResult
//            session.speechResult
        )
            .filter { it.isCompleted }
            .map { it.score.coerceIn(0f, 1f) }

        if (severities.isEmpty()) return 0
        return (severities.average().toFloat() * 100f).roundToInt().coerceIn(0, 100)
    }

    /** Hitung risiko berdasarkan tes BEFAST */
    private fun calculateBEFASTRisk(session: ScreeningResult): RiskLevel {
        val failed = listOfNotNull(
            session.balanceResult,
            session.eyesResult,
            session.faceResult,
            session.armsResult
//            session.speechResult
        ).count { it.isCompleted && !it.isSuccessful }

        val percent = calculateBEFASTOverallPercent(session)

        return when {
            percent >= 60 || failed >= 3 -> RiskLevel.CRITICAL
            percent >= 40 || failed >= 2 -> RiskLevel.HIGH
            percent >= 20 || failed >= 1 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    fun getRiskLabel(result: ScreeningResult): String = result.overallRisk.displayName

    // ===================== SharedPreferences Helpers =====================
    private fun saveCurrentSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = currentSession?.toCache()?.let { Gson().toJson(it) }
        prefs.edit { putString(KEY_CURRENT_SESSION, json) }
    }

    private fun loadCurrentSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CURRENT_SESSION, null) ?: return
        currentSession = runCatching {
            Gson().fromJson(json, ScreeningResultCache::class.java)?.toDomain()
        }.getOrNull()
    }

    private fun saveToHistory(context: Context, session: ScreeningResult) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SESSIONS_HISTORY, "[]") ?: "[]"
        val type = object : TypeToken<MutableList<ScreeningResultCache>>() {}.type
        val history: MutableList<ScreeningResultCache> = Gson().fromJson(json, type) ?: mutableListOf()

        history.add(0, session.toCache())
        if (history.size > 20) history.removeAt(history.size - 1)

        prefs.edit { putString(KEY_SESSIONS_HISTORY, Gson().toJson(history)) }
    }

    private fun clearCurrentSession(context: Context) {
        currentSession = null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(KEY_CURRENT_SESSION) }
    }

    // ===================== Public Management Methods =====================
    fun cancelSession(context: Context) {
        Log.d(TAG, "🗑️ Membatalkan sesi screening")
        clearCurrentSession(context)
    }

    fun clearAll(context: Context) {
        Log.d(TAG, "🧹 Membersihkan semua data screening...")
        currentSession = null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            remove(KEY_CURRENT_SESSION)
            remove(KEY_SESSIONS_HISTORY)
        }
        Log.d(TAG, "✅ Semua data screening cleared")
    }

    // ===================== Timestamp Helpers =====================
    fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getCurrentTimestampTs(): Timestamp = Timestamp.now()

    // ===================== Debug Helpers =====================
    fun getSessionInfo(context: Context): String {
        val session = getCurrentSession(context) ?: return "No active session"
        val progress = getSessionProgress(context)
        val befastPercent = calculateBEFASTOverallPercent(session)

        return """
            Session ID: ${session.sessionId}
            User ID   : ${session.userId}
            Started   : ${session.timestamp}
            Progress  : ${(progress * 100).roundToInt()}%
            Completed : ${getAllResults(context).count { it.isCompleted }}/$TOTAL_BEFAST_TESTS
            BEFAST %  : $befastPercent%
            Current Risk: ${calculateBEFASTRisk(session).displayName}
        """.trimIndent()
    }
}