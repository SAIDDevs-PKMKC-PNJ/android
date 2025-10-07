package com.pkm.said.util

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.pkm.said.LoginActivity

object AuthManager {

    fun ensureUserLoggedIn(activity: Activity): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        return if (currentUser == null) {
            Toast.makeText(activity, "Silakan login terlebih dahulu", Toast.LENGTH_SHORT).show()
            val intent = Intent(activity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            activity.startActivity(intent)
            false
        } else {
            true
        }
    }
}
