package com.pkm.said

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

class OpeningActivity : AppCompatActivity() {
    private val delay: Long = 2000
    private val tag = "OpeningActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opening)

        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = FirebaseAuth.getInstance().currentUser
            Log.d(tag, "👤 Checking Firebase login status")

            if (currentUser != null) {
                // User sudah login, langsung ke MainActivity
                Log.d(tag, "✅ User is already logged in: ${currentUser.email}")
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // User belum login, langsung ke intro
                Log.d(tag, "✅ User non logged in, navigating to intro")
                startActivity(Intent(this, IntroActivity::class.java))
            }
            finish()
        }, delay)
    }
}