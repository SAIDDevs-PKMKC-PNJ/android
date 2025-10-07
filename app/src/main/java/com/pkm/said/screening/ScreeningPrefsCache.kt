package com.pkm.said.screening

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit

class ScreeningPrefsCache(
    context: Context,
    private val prefsName: String = "screening_cache"
) : ScreeningCache {

    private val prefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val gson = Gson()

    private companion object {
        const val KEY_CURRENT_SESSION_ID = "current_session_id"
        const val KEY_CURRENT_SESSION_DATA = "current_session_data"
        const val KEY_LAST_COMPLETED = "last_completed"
        const val KEY_PENDING_SESSIONS = "pending_sessions"
        const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    }

    // === Session ID Management ===
    override fun cacheCurrentSessionId(sessionId: String?) {
        prefs.edit { putString(KEY_CURRENT_SESSION_ID, sessionId) }
    }

    override fun getCachedCurrentSessionId(): String? =
        prefs.getString(KEY_CURRENT_SESSION_ID, null)

    // === Current Session Data ===
    override fun cacheCurrentSessionData(session: ScreeningResult) {
        val dto = ScreeningCacheDTO.from(session)
        prefs.edit { putString(KEY_CURRENT_SESSION_DATA, gson.toJson(dto)) }
    }

    override fun getCachedCurrentSessionData(): ScreeningResult? {
        val raw = prefs.getString(KEY_CURRENT_SESSION_DATA, null) ?: return null
        return runCatching {
            val dto = gson.fromJson(raw, ScreeningCacheDTO::class.java)
            dto?.toDomain()
        }.getOrNull()
    }

    override fun clearCurrentSessionData() {
        prefs.edit {
            remove(KEY_CURRENT_SESSION_ID)
            remove(KEY_CURRENT_SESSION_DATA)
        }
    }

    // === Last Completed ===
    override fun cacheLastCompleted(result: ScreeningResult) {
        val dto = LastCompletedDTO.from(result)
        prefs.edit { putString(KEY_LAST_COMPLETED, gson.toJson(dto)) }
    }

    override fun getCachedLastCompleted(): ScreeningResult? {
        val raw = prefs.getString(KEY_LAST_COMPLETED, null) ?: return null
        return runCatching {
            val dto = gson.fromJson(raw, LastCompletedDTO::class.java)
            dto?.toDomain()
        }.getOrNull()
    }

    // === Pending Sessions ===
    override fun addPendingSession(session: ScreeningResult) {
        val pending = getPendingSessions().toMutableList()
        // Hindari duplikasi
        if (pending.none { it.sessionId == session.sessionId }) {
            pending.add(session)
            savePendingSessions(pending)
        }
    }

    override fun getPendingSessions(): List<ScreeningResult> {
        val raw = prefs.getString(KEY_PENDING_SESSIONS, "[]") ?: "[]"
        return runCatching {
            val type = object : TypeToken<List<ScreeningCacheDTO>>() {}.type
            val dtos: List<ScreeningCacheDTO> = gson.fromJson(raw, type) ?: emptyList()
            dtos.map { it.toDomain() }
        }.getOrElse { emptyList() }
    }

    override fun removePendingSession(sessionId: String) {
        val pending = getPendingSessions().filter { it.sessionId != sessionId }
        savePendingSessions(pending)
    }

    override fun clearAllPendingSessions() {
        prefs.edit { remove(KEY_PENDING_SESSIONS) }
    }

    // === Sync Status ===
    override fun cacheLastSyncTimestamp(timestamp: Long) {
        prefs.edit { putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp) }
    }

    override fun getLastSyncTimestamp(): Long =
        prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)

    // === Helper Methods ===
    private fun savePendingSessions(sessions: List<ScreeningResult>) {
        val dtos = sessions.map { ScreeningCacheDTO.from(it) }
        prefs.edit { putString(KEY_PENDING_SESSIONS, gson.toJson(dtos)) }
    }

    // NEW: Clear semua data cache (untuk logout/clear data)
    fun clearAllCache() {
        prefs.edit {
            clear()
        }
    }

    // NEW: Get cache info untuk debug
    fun getCacheInfo(): CacheInfo {
        return CacheInfo(
            hasCurrentSession = getCachedCurrentSessionData() != null,
            hasLastCompleted = getCachedLastCompleted() != null,
            pendingSessionsCount = getPendingSessions().size,
            lastSyncTimestamp = getLastSyncTimestamp()
        )
    }

    data class CacheInfo(
        val hasCurrentSession: Boolean,
        val hasLastCompleted: Boolean,
        val pendingSessionsCount: Int,
        val lastSyncTimestamp: Long
    )

    // === DTO Classes ===

    /** DTO untuk current session dan pending sessions */
    private data class ScreeningCacheDTO(
        val sessionId: String,
        val timestamp: String,
        val userId: String,
        val balanceResult: TestResult?,
        val eyesResult: TestResult?,
        val faceResult: TestResult?,
        val armsResult: TestResult?,
        val overallRisk: String,
        val isCompleted: Boolean,
        val completedAtText: String?
    ) {
        fun toDomain(): ScreeningResult = ScreeningResult(
            sessionId = sessionId,
            timestamp = timestamp,
            userId = userId,
            balanceResult = balanceResult,
            eyesResult = eyesResult,
            faceResult = faceResult,
            armsResult = armsResult,
            overallRisk = enumValueOf(overallRisk),
            isCompleted = isCompleted,
            completedAt = null,
            completedAtText = completedAtText
        )

        companion object {
            fun from(domain: ScreeningResult) = ScreeningCacheDTO(
                sessionId = domain.sessionId,
                timestamp = domain.timestamp,
                userId = domain.userId,
                balanceResult = domain.balanceResult,
                eyesResult = domain.eyesResult,
                faceResult = domain.faceResult,
                armsResult = domain.armsResult,
                overallRisk = domain.overallRisk.name,
                isCompleted = domain.isCompleted,
                completedAtText = domain.completedAtText
            )
        }
    }

    /** DTO untuk last completed (tetap dipertahankan) */
    private data class LastCompletedDTO(
        val sessionId: String,
        val timestamp: String,
        val userId: String,
        val balanceResult: TestResult?,
        val eyesResult: TestResult?,
        val faceResult: TestResult?,
        val armsResult: TestResult?,
        val overallRisk: String,
        val isCompleted: Boolean,
        val completedAtText: String?
    ) {
        fun toDomain(): ScreeningResult = ScreeningResult(
            sessionId = sessionId,
            timestamp = timestamp,
            userId = userId,
            balanceResult = balanceResult,
            eyesResult = eyesResult,
            faceResult = faceResult,
            armsResult = armsResult,
            overallRisk = enumValueOf(overallRisk),
            isCompleted = isCompleted,
            completedAt = null,
            completedAtText = completedAtText
        )

        companion object {
            fun from(domain: ScreeningResult) = LastCompletedDTO(
                sessionId = domain.sessionId,
                timestamp = domain.timestamp,
                userId = domain.userId,
                balanceResult = domain.balanceResult,
                eyesResult = domain.eyesResult,
                faceResult = domain.faceResult,
                armsResult = domain.armsResult,
                overallRisk = domain.overallRisk.name,
                isCompleted = domain.isCompleted,
                completedAtText = domain.completedAtText
            )
        }
    }
}