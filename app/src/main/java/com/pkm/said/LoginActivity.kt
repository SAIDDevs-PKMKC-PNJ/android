package com.pkm.said

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.pkm.said.util.SessionManager
import com.pkm.said.CloudinaryUploadResp
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.Request
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val http by lazy { OkHttpClient() }
    private val moshi by lazy { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    private val cldAdapter by lazy { moshi.adapter(CloudinaryUploadResp::class.java) }

    private val tag = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        Log.d(tag, "=== LOGIN ACTIVITY DEBUG ===")
        Log.d(tag, "Current Date and Time (UTC): 2025-09-03 06:56:47")
        Log.d(tag, "Current User's Login: itsLuxra")
        Log.d(tag, "🔄 LoginActivity started")

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // ✅ NEW: Initialize Credential Manager
        credentialManager = CredentialManager.create(this)

        // Handle logout extras
        handleLogoutExtras()

        // Check if user is already logged in
        checkCurrentUser()

        // Setup UI elements
        setupUIElements()
    }

    private fun handleLogoutExtras() {
        try {
            val logoutMessage = intent.getStringExtra("logout_message")
            val showLogoutToast = intent.getBooleanExtra("show_logout_toast", false)

            if (showLogoutToast && !logoutMessage.isNullOrEmpty()) {
                Toast.makeText(this, logoutMessage, Toast.LENGTH_LONG).show()
                Log.d(tag, "Showed logout message: $logoutMessage")
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Error handling logout extras", e)
        }
    }

    private fun checkCurrentUser() {
        try {
            val currentUser = auth.currentUser

            if (currentUser != null) {
                Log.d(tag, "User already logged in: ${currentUser.email}")
                redirectToMainActivity()
            } else {
                Log.d(tag, "No current user, showing login screen")
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Error checking current user", e)
        }
    }

    private fun setupUIElements() {
        try {
            val emailEditText: EditText = findViewById(R.id.emailEditText)
            val passwordEditText: EditText = findViewById(R.id.passwordEditText)
            val forgetPasswordText: TextView = findViewById(R.id.forgetPasswordText)
            val loginButton: Button = findViewById(R.id.loginButton)
            val toRegisterButton: TextView = findViewById(R.id.toRegisterButton)
            val googleSignInButton: Button = findViewById(R.id.googleSignInButton)

            try {
                forgetPasswordText.setOnClickListener {
                    Log.d(tag, "🔑 Forgot password clicked - showing bottom sheet overlay")
                    showForgotPasswordBottomSheet()
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ Error setting up forgot password trigger", e)
            }

            // Email/Password Login
            loginButton.setOnClickListener {
                performEmailPasswordLogin(emailEditText, passwordEditText)
            }

            // Navigate to Register
            toRegisterButton.setOnClickListener {
                Log.d(tag, "🔁 Navigating to RegisterActivity")
                startActivity(Intent(this, RegisterActivity::class.java))
            }

            // ✅ NEW: Google Sign In with Credential Manager
            googleSignInButton.setOnClickListener {
                Log.d(tag, "🔄 Google Sign In button clicked")
                performGoogleSignInWithCredentialManager()
            }

            Log.d(tag, "✅ UI elements setup completed")
        } catch (e: Exception) {
            Log.e(tag, "❌ Error setting up UI elements", e)
        }
    }

    private suspend fun uploadUrlToCloudinary(
        fileUrl: String,
        cloudName: String,
        uploadPreset: String,
        folder: String
    ): CloudinaryUploadResp? = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", fileUrl)
                .addFormDataPart("upload_preset", uploadPreset)
                .addFormDataPart("folder", folder)
                .build()

            val req = okhttp3.Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(body)
                .build()

            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.e(tag, "Cloudinary upload failed code=${res.code} body=${res.body?.string()}")
                    return@use null
                }
                val txt = res.body?.string().orEmpty()
                cldAdapter.fromJson(txt)
            }
        } catch (e: Exception) {
            Log.e(tag, "uploadUrlToCloudinary() error", e)
            null
        }
    }

    private fun performEmailPasswordLogin(emailEditText: EditText, passwordEditText: EditText) {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        Log.d(tag, "📝 Email/Password login attempt for: $email")

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email dan kata sandi wajib diisi", Toast.LENGTH_SHORT).show()
            Log.d(tag, "⚠️ Email or password is empty")
            return
        }

        // Show loading state
        val loginButton: Button = findViewById(R.id.loginButton)
        loginButton.isEnabled = false
        loginButton.text = "Signing in..."

        Log.d(tag, "🔐 Attempting email/password authentication...")
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                // Reset button state
                loginButton.isEnabled = true
                loginButton.text = "Login"

                if (task.isSuccessful) {
                    Log.d(tag, "✅ Email/Password login successful: ${auth.currentUser?.email}")
                    Toast.makeText(this, "Login Berhasil!", Toast.LENGTH_SHORT).show()

                    redirectToMainActivity()
                } else {
                    Log.e(tag, "❌ Email/Password login failed", task.exception)
                    Toast.makeText(this, "Login gagal: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    // ✅  Forgot Password with Overlay
    private fun showForgotPasswordBottomSheet() {
        try {
            // Get current email from login form (if user already typed something)
            val emailEditText: EditText = findViewById(R.id.emailEditText)
            val currentEmail = emailEditText.text.toString().trim()

            // Create bottom sheet instance
            val bottomSheet = ForgotPasswordBottomSheet.newInstance(
                // Only pass email if it's valid, otherwise null
                if (currentEmail.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches())
                    currentEmail
                else
                    null
            )

            // ✅ SHOW BOTTOM SHEET - LoginActivity stays in background
            bottomSheet.show(supportFragmentManager, "ForgotPasswordBottomSheet")

            Log.d(tag, "✅ Bottom sheet overlay displayed, LoginActivity dimmed in background")

        } catch (e: Exception) {
            Log.e(tag, "❌ Error showing forgot password bottom sheet", e)
            Toast.makeText(this, "Error opening reset password form", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ NEW: Google Sign In with Credential Manager
    private fun performGoogleSignInWithCredentialManager() {
        Log.d(tag, "🔄 Starting Google Sign In with Credential Manager...")

        // Show loading state
        val googleSignInButton: Button = findViewById(R.id.googleSignInButton)
        googleSignInButton.isEnabled = false
        googleSignInButton.text = "Signing in with Google..."

        lifecycleScope.launch {
            try {
                val webClientId = getString(R.string.web_client_id)

                // ✅ VALIDATE without logging sensitive data
                if (webClientId.isEmpty() || !webClientId.contains("apps.googleusercontent.com")) {
                    throw IllegalStateException("Invalid Web Client ID configuration")
                }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId) // Use securely
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption as CredentialOption)
                    .build()

                Log.d(tag, "Credential request created successfully")
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity,
                )

                handleCredentialResult(result)

            } catch (e: GetCredentialException) {
                Log.e(tag, "❌ Credential Manager error", e)
                handleCredentialError(e)
            } catch (e: Exception) {
                Log.e(tag, "❌ Unexpected error in Google Sign In", e)
                handleUnexpectedError(e)
            }
        }
    }

    // ✅ Handle Credential Result
    private fun handleCredentialResult(result: GetCredentialResponse) {
        try {
            Log.d(tag, "✅ Credential received, processing...")

            when (val credential = result.credential) {
                is GoogleIdTokenCredential -> {
                    Log.d(tag, "Google ID Token credential received")

                    val googleIdToken = credential.idToken
                    Log.d(tag, "Google ID Token: ${googleIdToken.take(20)}...")

                    // Authenticate with Firebase
                    firebaseAuthWithGoogle(googleIdToken)
                }
                else -> {
                    Log.e(tag, "❌ Unexpected credential type: ${credential::class.java}")
                    resetGoogleButtonState()
                    Toast.makeText(this, "Unexpected credential type", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(tag, "❌ Google ID Token parsing error", e)
            resetGoogleButtonState()
            Toast.makeText(this, "Invalid Google credential", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(tag, "❌ Error handling credential result", e)
            resetGoogleButtonState()
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ Handle Credential Errors
    private fun handleCredentialError(e: GetCredentialException) {
        resetGoogleButtonState()

        when (e::class.java.simpleName) {
            "GetCredentialCancellationException" -> {
                Log.d(tag, "⚠️ User cancelled Google Sign In")
                Toast.makeText(this, "Sign in cancelled", Toast.LENGTH_SHORT).show()
            }
            "GetCredentialInterruptedException" -> {
                Log.e(tag, "❌ Google Sign In interrupted", e)
                Toast.makeText(this, "Sign in interrupted", Toast.LENGTH_SHORT).show()
            }
            "NoCredentialException" -> {
                Log.e(tag, "❌ No Google credentials available", e)
                Toast.makeText(this, "No Google account found. Please add a Google account.", Toast.LENGTH_LONG).show()
            }
            else -> {
                Log.e(tag, "❌ Unknown credential error: ${e::class.java.simpleName}", e)
                Toast.makeText(this, "Google Sign In failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ✅ Handle Unexpected Errors
    private fun handleUnexpectedError(e: Exception) {
        resetGoogleButtonState()
        Log.e(tag, "❌ Unexpected error", e)
        Toast.makeText(this, "An unexpected error occurred: ${e.message}", Toast.LENGTH_LONG).show()
    }

    private fun handlePostAuthFlow(isFromRegistration: Boolean) {
        val user = auth.currentUser
        if (user == null) {
            Log.w(tag, "User null setelah login, abort flow")
            return
        }

        Log.d(tag, "🔎 Mengecek dokumen Firestore user: ${user.uid}")

        val usersCol = firestore.collection("users")
        val docRef = usersCol.document(user.uid)
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val uploadPreset = BuildConfig.CLOUDINARY_UNSIGNED_PRESET

        docRef.get()
            .addOnSuccessListener { doc ->
                val isGoogle = user.providerData.any { it.providerId == "google.com" }
                val loginMethod = if (isGoogle) "google" else "email"
                val googlePhotoUrl = user.photoUrl?.toString()

                if (doc.exists()) {
                    // ---- Dokumen SUDAH ada ----
                    val name = doc.getString("name") ?: user.displayName ?: ""
                    val birthdate = doc.getString("birthdate")
                    val phone = doc.getString("phone")
                    val address = doc.getString("address")
                    val emergency = doc.getString("emergency")
                    val savedPhoto = doc.getString("photoUrl")
                    val hasPhoto = !savedPhoto.isNullOrBlank()

                    // Jika belum ada photoUrl di doc, coba backfill dari Google -> Cloudinary
                    if (!hasPhoto && !googlePhotoUrl.isNullOrBlank()) {
                        lifecycleScope.launch {
                            try {
                                val resp = uploadUrlToCloudinary(
                                    fileUrl = googlePhotoUrl ?: "",
                                    cloudName = cloudName,
                                    uploadPreset = uploadPreset,
                                    folder = "profile_photos/${user.uid}"
                                )

                                val finalUrl = resp?.secure_url ?: googlePhotoUrl
                                val publicId = resp?.public_id ?: ""

                                docRef.set(
                                    mapOf(
                                        "photoUrl" to finalUrl,
                                        "cloudinaryPublicId" to publicId,
                                        "updatedAt" to System.currentTimeMillis()
                                    ),
                                    com.google.firebase.firestore.SetOptions.merge()
                                )

                                SessionManager.saveFullProfile(
                                    context = this@LoginActivity,
                                    name = name,
                                    email = user.email,
                                    photoUrl = finalUrl,
                                    birthdate = birthdate,
                                    phone = phone,
                                    address = address,
                                    emergency = emergency,
                                    loginMethod = doc.getString("loginMethod") ?: loginMethod,
                                    emailVerified = user.isEmailVerified
                                )
                                redirectToMainActivity()
                            } catch (e: Exception) {
                                Log.e(tag, "Backfill Cloudinary failed", e)
                                // Fallback pakai data lama + google url bila ada
                                SessionManager.saveFullProfile(
                                    context = this@LoginActivity,
                                    name = name,
                                    email = user.email,
                                    photoUrl = googlePhotoUrl,
                                    birthdate = birthdate, phone = phone, address = address, emergency = emergency,
                                    loginMethod = doc.getString("loginMethod") ?: loginMethod,
                                    emailVerified = user.isEmailVerified
                                )
                                redirectToMainActivity()
                            }
                        }
                        return@addOnSuccessListener
                    }

                    // Sudah ada doc & (mungkin) sudah ada photo → lanjut seperti biasa
                    SessionManager.saveFullProfile(
                        context = this,
                        name = name,
                        email = user.email,
                        photoUrl = savedPhoto ?: googlePhotoUrl,
                        birthdate = birthdate,
                        phone = phone,
                        address = address,
                        emergency = emergency,
                        loginMethod = doc.getString("loginMethod") ?: loginMethod,
                        emailVerified = user.isEmailVerified
                    )
                    Log.d(tag, "✅ Profil ditemukan & disimpan ke SessionManager → ke MainActivity")
                    redirectToMainActivity()

                } else {
                    // ---- Dokumen BELUM ada ----
                    Log.d(tag, "ℹ️ Doc belum ada. Inisialisasi data + arahkan ke UserInformationActivity.")

                    lifecycleScope.launch {
                        var finalPhotoUrl: String? = null
                        var publicId: String? = null

                        // Jika ada foto Google, salin ke Cloudinary
                        if (!googlePhotoUrl.isNullOrBlank()) {
                            try {
                                val resp = uploadUrlToCloudinary(
                                    fileUrl = googlePhotoUrl ?: "",
                                    cloudName = cloudName,
                                    uploadPreset = uploadPreset,
                                    folder = "profile_photos/${user.uid}"
                                )

                                finalPhotoUrl = resp?.secure_url ?: googlePhotoUrl
                                publicId = resp?.public_id
                            } catch (e: Exception) {
                                Log.e(tag, "Init Cloudinary upload failed", e)
                                finalPhotoUrl = googlePhotoUrl // fallback
                            }
                        }

                        // Upsert minimal doc (biar kedepan sudah ada skeleton)
                        val initData = hashMapOf(
                            "uid" to user.uid,
                            "email" to (user.email ?: ""),
                            "name" to (user.displayName ?: ""),
                            "photoUrl" to (finalPhotoUrl ?: ""),
                            "cloudinaryPublicId" to (publicId ?: ""),
                            "loginMethod" to loginMethod,
                            "emailVerified" to user.isEmailVerified,
                            "createdAt" to System.currentTimeMillis(),
                            "updatedAt" to System.currentTimeMillis()
                        )
                        docRef.set(initData, com.google.firebase.firestore.SetOptions.merge())
                            .addOnCompleteListener {
                                // Simpan basic untuk sesi awal
                                SessionManager.saveBasicFromFirebase(
                                    context = this@LoginActivity,
                                    user = user,
                                    loginMethod = loginMethod
                                )

                                // Lanjut ke form melengkapi profil (bawa photo url yg sudah disalin)
                                val intent = Intent(this@LoginActivity, UserInformationActivity::class.java).apply {
                                    putExtra("user_email", user.email)
                                    putExtra("user_name", user.displayName)
                                    putExtra("user_photo_url", finalPhotoUrl ?: googlePhotoUrl)
                                    putExtra("login_method", loginMethod)
                                    putExtra("email_verified", user.isEmailVerified)
                                    putExtra("from_registration", isFromRegistration)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(intent)
                                finish()
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(tag, "❌ Gagal cek Firestore user doc. Fallback langsung ke Main.", e)
                SessionManager.saveBasicFromFirebase(
                    this,
                    user,
                    loginMethod = if (user.providerData.any { it.providerId == "google.com" }) "google" else "email"
                )
                redirectToMainActivity()
            }
    }


    // ✅ Firebase Auth with Google ID Token
    private fun firebaseAuthWithGoogle(idToken: String) {
        try {
            Log.d(tag, "🔐 Authenticating with Firebase using Google ID Token...")
            val credential = GoogleAuthProvider.getCredential(idToken, null)

            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    resetGoogleButtonState()

                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        Log.d(tag, "✅ Firebase Google authentication successful user=${user?.uid}")
                        Toast.makeText(this, "Welcome ${user?.displayName ?: user?.email}!", Toast.LENGTH_SHORT).show()
                        // Karena Google Sign-In bisa berarti user baru → set isFromRegistration = true
                        handlePostAuthFlow(isFromRegistration = true)
                    } else {
                        Log.e(tag, "❌ Firebase Google authentication failed", task.exception)
                        Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }

        } catch (e: Exception) {
            Log.e(tag, "❌ Error in Firebase Google authentication", e)
            resetGoogleButtonState()
            Toast.makeText(this, "Authentication error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ✅ Reset Google Button State
    private fun resetGoogleButtonState() {
        val googleSignInButton: Button = findViewById(R.id.googleSignInButton)
        googleSignInButton.isEnabled = true
        googleSignInButton.text = "Sign in with Google"
    }

    // ✅ Generate Nonce for Security
    private fun generateNonce(): String {
        val rawNonce = UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    // ✅ Redirect to Main Activity
    private fun redirectToMainActivity() {
        try {
            Log.d(tag, "🔄 Redirecting to MainActivity...")

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            startActivity(intent)
            finish()

            Log.d(tag, "✅ Redirected to MainActivity")
        } catch (e: Exception) {
            Log.e(tag, "❌ Error redirecting to MainActivity", e)
        }
    }

    override fun onStart() {
        super.onStart()

        // Check authentication state when activity starts
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(tag, "User authenticated on start: ${currentUser.email}")
        }
    }
}