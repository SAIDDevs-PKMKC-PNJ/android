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
    val sensorResult: TestResult? = null,
    val cameraResult: TestResult? = null,
    val micResult: TestResult? = null,
    val overallRisk: RiskLevel = RiskLevel.UNKNOWN,
    val isCompleted: Boolean = false
)

data class TestResult(
    val testName: String,
    val isCompleted: Boolean,
    val isSuccessful: Boolean,
    val score: Float = 0f,
    val notes: String = "",
    val timestamp: String,
    val sensorData: Map<String, Any> = emptyMap()
)

enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN
}

object ScreeningDataManager {
    private const val TAG = "ScreeningDataManager"
    private const val PREFS_NAME = "screening_data"
    private const val KEY_CURRENT_SESSION = "current_session"
    private const val KEY_SESSIONS_HISTORY = "sessions_history"

    private var currentSession: ScreeningResult? = null

    fun startNewSession(context: Context, userId: String): ScreeningResult {
        val sessionId = UUID.randomUUID().toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        currentSession = ScreeningResult(
            sessionId = sessionId,
            timestamp = timestamp,
            userId = userId
        )
        saveCurrentSession(context)
        return currentSession!!
    }

    fun updateTestResult(context: Context, testResult: TestResult) {
        currentSession?.let { session ->
            val updatedSession = when (testResult.testName.lowercase()) {
                "sensor" -> session.copy(sensorResult = testResult)
                "camera" -> session.copy(cameraResult = testResult)
                "mic" -> session.copy(micResult = testResult)
                else -> session
            }
            currentSession = updatedSession
            saveCurrentSession(context)
        }
    }

    fun completeSession(context: Context): ScreeningResult? {
        currentSession?.let { session ->
            val completedSession = session.copy(
                isCompleted = true,
                overallRisk = calculateOverallRisk(session)
            )
            saveToHistory(context, completedSession)
            clearCurrentSession(context)
            return completedSession
        }
        return null
    }

    fun getCurrentSession(): ScreeningResult? = currentSession

    fun getAllResults(context: Context): List<TestResult> {
        val session = getCurrentSession() ?: return emptyList()
        return listOfNotNull(
            session.sensorResult,
            session.cameraResult,
            session.micResult
        )
    }

    fun cancelSession(context: Context) {
        clearCurrentSession(context)
        currentSession = null
    }

    private fun calculateOverallRisk(session: ScreeningResult): RiskLevel {
        val completedTests = listOfNotNull(
            session.sensorResult,
            session.cameraResult,
            session.micResult
        ).filter { it.isCompleted }

        if (completedTests.isEmpty()) return RiskLevel.UNKNOWN

        val failedTests = completedTests.count { !it.isSuccessful }
        val totalTests = completedTests.size

        return when {
            failedTests == 0 -> RiskLevel.LOW
            failedTests == 1 -> RiskLevel.MEDIUM
            failedTests == 2 -> RiskLevel.HIGH
            failedTests == 3 -> RiskLevel.CRITICAL
            else -> RiskLevel.UNKNOWN
        }
    }

    private fun saveCurrentSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(currentSession)
        prefs.edit().putString(KEY_CURRENT_SESSION, json).apply()
    }

    private fun saveToHistory(context: Context, session: ScreeningResult) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val existingJson = prefs.getString(KEY_SESSIONS_HISTORY, "[]")
        val listType = object : TypeToken<MutableList<ScreeningResult>>() {}.type
        val history: MutableList<ScreeningResult> = gson.fromJson(existingJson, listType)
        history.add(session)
        if (history.size > 10) history.removeAt(0)
        val updatedJson = gson.toJson(history)
        prefs.edit().putString(KEY_SESSIONS_HISTORY, updatedJson).apply()
    }

    private fun clearCurrentSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CURRENT_SESSION).apply()
    }
}