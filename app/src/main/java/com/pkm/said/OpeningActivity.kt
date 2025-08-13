package com.pkm.said

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log

class OpeningActivity : AppCompatActivity() {
    private val DELAY: Long = 2000
    private val PREFS_NAME = "MyPrefsFile"
    private val PREF_INTRO_SHOWN = "intro_shown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opening)

        // Menggunakan Handler untuk menunda transisi ke aktivitas berikutnya
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("OpeningActivity", "DEBUG MODE: Always showing IntroActivity")
            startActivity(Intent(this, IntroActivity::class.java))
//            val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
//            val introShown = prefs.getBoolean(PREF_INTRO_SHOWN, false)

//            if (introShown) {
                // sudah pernah dibuka
//                startActivity(Intent(this, MainActivity::class.java))
//            } else {
                // belum pernah dibuka
//                startActivity(Intent(this, IntroActivity::class.java))
//            }
            finish() // Tutup LogoScreenActivity agar tidak bisa kembali dengan tombol back
        }, DELAY)
    }
}
