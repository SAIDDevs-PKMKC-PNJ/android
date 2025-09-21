package com.pkm.said

import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.widget.TextView

class WelcomeDialog(context: Context, userName: String, onFinish: () -> Unit) : Dialog(context) {
    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_welcome)
        setCancelable(false)

        val tvWelcome = findViewById<TextView>(R.id.tvWelcomeMsg)
        tvWelcome.text = "Selamat datang, $userName!"

        // Bisa ganti dengan LottieAnimationView jika ingin animasi
        Handler(Looper.getMainLooper()).postDelayed({
            dismiss()
            onFinish()
        }, 2000) // 2 detik
    }
}