package com.pkm.said.screening

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Implementasi default mengandalkan:
 * - Auth: FirebaseAuth.getInstance()
 * - DB: Firebase.firestore
 */
object ScreeningRepository {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { Firebase.firestore }
    private const val TAG = "ScreeningRepository"

    // ---- Helpers Path ----
    private fun usersCol() = db.collection("users")
    private fun userDoc(uid: String) = usersCol().document(uid)
    private fun screeningsCol(uid: String) = userDoc(uid).collection("screenings")

    // ---- Helpers ----
    private fun nowString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun requireUid(): String {
        return auth.currentUser?.uid
            ?: error("User belum login. Pastikan sudah FirebaseAuth.signIn*.()")
    }

    suspend fun saveCompleteScreeningSession(session: ScreeningResult): Boolean {
        return try {
            val uid = requireUid()

            // Convert ke format Firestore
            val firestoreData = mapOf(
                "sessionId" to session.sessionId,
                "userId" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "completedAt" to FieldValue.serverTimestamp(),
                "isCompleted" to true,
                "overallRisk" to session.overallRisk.name,
                "overallScore" to ScreeningDataManager.calculateBEFASTOverallPercent(session) / 100.0,

                // Semua hasil tes disimpan sekaligus
                "tests" to mapOf(
                    "balance" to session.balanceResult?.toFirestoreMap(),
                    "eyes" to session.eyesResult?.toFirestoreMap(),
                    "face" to session.faceResult?.toFirestoreMap(),
                    "arms" to session.armsResult?.toFirestoreMap()
//                    "speech" to session.speechResult?.toFirestoreMap()
                ),

                "metadata" to mapOf(
                    "deviceModel" to android.os.Build.MODEL,
                    "appVersion" to "1.0.0", // Ganti dengan BuildConfig.VERSION_NAME
                    "syncTimestamp" to FieldValue.serverTimestamp()
                )
            )

            // Commit ATOMIC ke Firestore
            screeningsCol(uid).document(session.sessionId)
                .set(firestoreData, SetOptions.merge())
                .await()

            Log.d(TAG, "✅ Screening session berhasil disimpan ke Firestore: ${session.sessionId}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Gagal menyimpan screening session ke Firestore: ${e.message}", e)
            false
        }
    }

    private fun TestResult?.toFirestoreMap(): Map<String, Any>? {
        if (this == null) return null

        return mapOf(
            "testName" to this.testName,
            "isCompleted" to this.isCompleted,
            "isSuccessful" to this.isSuccessful,
            "score" to this.score,
            "notes" to this.notes,
            "duration" to this.duration,
            "timestamp" to this.timestamp,
            "testData" to this.testData
        )
    }

    suspend fun getScreeningHistory(limit: Int = 10): List<ScreeningResult> {
        return try {
            val uid = requireUid()

            val query = screeningsCol(uid)
                .whereEqualTo("isCompleted", true)
                .orderBy("completedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())

            val snapshot = query.get().await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null

                    val tests = data["tests"] as? Map<String, Map<String, Any>> ?: emptyMap()

                    ScreeningResult(
                        sessionId = doc.id,
                        timestamp = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate().toString(),
                        userId = uid,
                        balanceResult = tests["balance"]?.toTestResult("balance"),
                        eyesResult = tests["eyes"]?.toTestResult("eyes"),
                        faceResult = tests["face"]?.toTestResult("face"),
                        armsResult = tests["arms"]?.toTestResult("arms"),
//                        speechResult = tests["speech"]?.toTestResult("speech"),
                        overallRisk = RiskLevel.valueOf(data["overallRisk"] as? String ?: "UNKNOWN"),
                        isCompleted = true,
                        completedAt = data["completedAt"] as? com.google.firebase.Timestamp,
                        completedAtText = (data["completedAt"] as? com.google.firebase.Timestamp)?.toDate()?.let {
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it)
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing document ${doc.id}: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("index") == true) {
                Log.w(TAG, "Firestore index not ready yet, returning empty list")
                emptyList()
            } else {
                Log.e(TAG, "Error getting screening history: ${e.message}")
                emptyList()
            }
        }
    }

    private fun Map<String, Any>.toTestResult(defaultTestName: String): TestResult {
        return TestResult(
            testName = this["testName"] as? String ?: "befast_$defaultTestName",
            isCompleted = this["isCompleted"] as? Boolean ?: false,
            isSuccessful = this["isSuccessful"] as? Boolean ?: false,
            score = (this["score"] as? Double)?.toFloat() ?: 0f,
            notes = this["notes"] as? String ?: "",
            timestamp = this["timestamp"] as? String ?: nowString(),
            duration = (this["duration"] as? Long) ?: 0L,
            testData = this["testData"] as? Map<String, Any> ?: emptyMap()
        )
    }

    /**
     * Ambil N terakhir yang sudah completed, desc by completedAt.
     * Default: hanya 1 (terakhir).
     */
    suspend fun getLastCompletedRemote(): ScreeningResult? {
        return getScreeningHistory(limit = 1).firstOrNull()
    }

    suspend fun getScreeningById(sessionId: String): ScreeningResult? {
        return try {
            val uid = requireUid()

            val document = screeningsCol(uid).document(sessionId).get().await()

            if (document.exists()) {
                val data = document.data ?: return null
                val tests = data["tests"] as? Map<String, Map<String, Any>> ?: emptyMap()

                ScreeningResult(
                    sessionId = document.id,
                    timestamp = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate().toString(),
                    userId = uid,
                    balanceResult = tests["balance"]?.toTestResult("balance"),
                    eyesResult = tests["eyes"]?.toTestResult("eyes"),
                    faceResult = tests["face"]?.toTestResult("face"),
                    armsResult = tests["arms"]?.toTestResult("arms"),
//                    speechResult = tests["speech"]?.toTestResult("speech"),
                    overallRisk = RiskLevel.valueOf(data["overallRisk"] as? String ?: "UNKNOWN"),
                    isCompleted = true,
                    completedAt = data["completedAt"] as? com.google.firebase.Timestamp,
                    completedAtText = (data["completedAt"] as? com.google.firebase.Timestamp)?.toDate()?.let {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it)
                    }
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting screening by ID $sessionId: ${e.message}")
            null
        }
    }
}
