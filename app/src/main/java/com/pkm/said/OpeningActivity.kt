package com.pkm.said

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.pkm.said.util.SessionManager

class OpeningActivity : AppCompatActivity() {
    private val delay: Long = 2000
    private val tag = "OpeningActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opening)

        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = FirebaseAuth.getInstance().currentUser
            Log.d(tag, "👤 Checking Firebase login status")

            // Cek jika voice service sudah jalan (untuk debugging)
            val isServiceRunning = isVoiceServiceRunning()
            Log.d(tag, "🎤 Voice service status: ${if (isServiceRunning) "RUNNING" else "STOPPED"}")

            if (currentUser != null) {
                Log.d(tag, "✅ User is already logged in: ${currentUser.email}")
                if (!SessionManager.isLoggedIn(this)) {
                    SessionManager.saveBasicFromFirebase(this, currentUser)
                }
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                Log.d(tag, "✅ User non logged in, navigating to intro")
                SessionManager.clear(this)
                startActivity(Intent(this, IntroActivity::class.java))
            }
            finish()
        }, delay)
    }

    @Suppress("DEPRECATION")
    private fun isVoiceServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == "com.pkm.said.service.VoiceActivationService" }
    }
}