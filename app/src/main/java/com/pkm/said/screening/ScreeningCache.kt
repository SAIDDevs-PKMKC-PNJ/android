package com.pkm.said.screening

interface ScreeningCache {
    // Session management
    fun cacheCurrentSessionId(sessionId: String?)
    fun getCachedCurrentSessionId(): String?

    // Current session data (untuk sesi yang sedang berjalan)
    fun cacheCurrentSessionData(session: ScreeningResult)
    fun getCachedCurrentSessionData(): ScreeningResult?
    fun clearCurrentSessionData()

    // Last completed (untuk dashboard)
    fun cacheLastCompleted(result: ScreeningResult)
    fun getCachedLastCompleted(): ScreeningResult?

    // NEW: Pending sessions management (opsional, untuk retry mechanism)
    fun addPendingSession(session: ScreeningResult)
    fun getPendingSessions(): List<ScreeningResult>
    fun removePendingSession(sessionId: String)
    fun clearAllPendingSessions()

    // NEW: Sync status
    fun cacheLastSyncTimestamp(timestamp: Long)
    fun getLastSyncTimestamp(): Long
}