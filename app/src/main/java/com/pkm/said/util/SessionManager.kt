package com.pkm.said.util

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import androidx.core.content.edit

object SessionManager {

    private const val PREF_NAME = "user_prefs"
    private const val KEY_UID = "uid"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"
    private const val KEY_PHOTO = "photo_url"
    private const val KEY_LOGIN_METHOD = "login_method"
    private const val KEY_EMAIL_VERIFIED = "email_verified"
    private const val KEY_PHONE = "phone"
    private const val KEY_ADDRESS = "address"
    private const val KEY_EMERGENCY = "emergency"
    private const val KEY_BIRTHDATE = "birthdate"

    /** Simpan user yang login dari Firebase */
    fun saveBasicFromFirebase(context: Context, user: FirebaseUser, loginMethod: String? = null) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_UID, user.uid)
                .putString(KEY_USERNAME, user.displayName)
                .putString(KEY_EMAIL, user.email)
                .putString(KEY_PHOTO, user.photoUrl?.toString())
                .putString(KEY_LOGIN_METHOD, loginMethod)
                .putBoolean(KEY_EMAIL_VERIFIED, user.isEmailVerified)
        }
    }

    fun saveFullProfile(
        context: Context,
        uid: String?,
        name: String,
        email: String?,
        photoUrl: String?,
        birthdate: String?,
        phone: String?,
        address: String?,
        loginMethod: String?,
        emailVerified: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_UID, uid)
                .putString(KEY_USERNAME, name)
                .putString(KEY_EMAIL, email)
                .putString(KEY_PHOTO, photoUrl)
                .putString(KEY_BIRTHDATE, birthdate)
                .putString(KEY_PHONE, phone)
                .putString(KEY_ADDRESS, address)
                .putString(KEY_LOGIN_METHOD, loginMethod)
                .putBoolean(KEY_EMAIL_VERIFIED, emailVerified)
        }
    }

    fun getUserId(context: Context): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_UID, null)

    fun getUserName(context: Context): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USERNAME, null)

    fun isLoggedIn(context: Context): Boolean {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getString(KEY_UID, null)
        return user != null && uid != null && user.uid == uid
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                clear()
            }
    }
}
