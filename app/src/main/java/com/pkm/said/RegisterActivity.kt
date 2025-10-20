package com.pkm.said

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.Request
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class RegisterActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private val TAG = "RegisterActivity"
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val http by lazy { OkHttpClient() }
    private val moshi by lazy { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    private val cldAdapter by lazy { moshi.adapter(CloudinaryUploadResp::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        Log.d(TAG, "=== REGISTER ACTIVITY DEBUG ===")
        Log.d(TAG, "Current Date and Time (UTC): 2025-09-03 09:48:18")
        Log.d(TAG, "Current User's Login: itsLuxra")
        Log.d(TAG, "🔄 RegisterActivity started")

        auth = FirebaseAuth.getInstance()

        // ✅ Initialize Credential Manager for Google Sign In
        credentialManager = CredentialManager.create(this)

        // ✅ Update to use correct IDs from your layout
        val nameEditText: EditText = findViewById(R.id.fullNameEditText) // Updated ID
        val emailEditText: EditText = findViewById(R.id.emailEditText)
        val passwordEditText: EditText = findViewById(R.id.passwordEditText)
        val confirmPasswordEditText: EditText =
            findViewById(R.id.confirmPasswordEditText) // New field
        val registerButton: Button = findViewById(R.id.registerButton)
        val toLoginButton: TextView = findViewById(R.id.toLoginButton) // Changed to TextView

        // ✅ Add Google Sign In button
        val googleSignInButton: Button = findViewById(R.id.googleSignInButton)

        // ✅ Email/Password Registration
        registerButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            Log.d(TAG, "📝 Register button clicked with name: $name, email: $email")

            // ✅ Enhanced validation
            if (!validateRegistrationInputs(name, email, password, confirmPassword)) {
                return@setOnClickListener
            }

            // ✅ Show loading state
            registerButton.isEnabled = false
            registerButton.text = getString(R.string.creating_account)

            Log.d(TAG, "🔐 Attempting to register user...")
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    // ✅ Reset button state
                    registerButton.isEnabled = true
                    registerButton.text = getString(R.string.sign_up_notif)

                    if (task.isSuccessful) {
                        Log.d(TAG, "✅ Registration successful for user: $email")
                        Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT)
                            .show()

                        // Set display name
                        val user = auth.currentUser
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build()
                        user?.updateProfile(profileUpdates)
                            ?.addOnCompleteListener { updateTask ->
                                if (updateTask.isSuccessful) {
                                    Log.d(TAG, "✅ User profile updated with name: $name")
                                } else {
                                    Log.e(
                                        TAG,
                                        "❌ Failed to update user profile",
                                        updateTask.exception
                                    )
                                }
                                // Lanjut ke MainActivity
                                redirectToMainActivity()
                            }
                    } else {
                        val exception = task.exception
                        if (exception is FirebaseAuthUserCollisionException) {
                            Log.d(TAG, "⚠️ Akun sudah ada: ${exception.message}")
                            Toast.makeText(
                                this,
                                "Akun dengan email ini sudah terdaftar. Silahkan login.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Log.e(TAG, "❌ Registration failed", exception)
                            Toast.makeText(
                                this,
                                "Registrasi gagal: ${exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
        }

        // ✅ Navigate to Login
        toLoginButton.setOnClickListener {
            Log.d(TAG, "🔁 Navigating to LoginActivity")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // ✅ NEW: Google Sign In for Registration
        googleSignInButton.setOnClickListener {
            Log.d(TAG, "🔄 Google Sign In button clicked for registration")
            performGoogleSignInForRegister()
        }

        // ✅ Setup optional social logins
        setupOptionalSocialLogins()
    }

    private suspend fun uploadUrlToCloudinary(
        fileUrl: String,
        cloudName: String,
        uploadPreset: String,
        folder: String
    ): CloudinaryUploadResp? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📤 Uploading with Cloudinary auto-unique naming...")

            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", fileUrl)
                .addFormDataPart("upload_preset", uploadPreset)
                .addFormDataPart("folder", folder)
                .build()

            val req = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(body)
                .build()

            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.e(TAG, "❌ Upload failed: ${res.code}")
                    return@use null
                }

                val responseText = res.body?.string().orEmpty()
                val result = cldAdapter.fromJson(responseText)

                if (result != null) {
                    Log.d(TAG, "✅ Upload Success!")
                    Log.d(TAG, "   - Public ID: ${result.public_id}")
                    Log.d(TAG, "   - Secure URL: ${result.secure_url?.take(50)}...")

                    // Safe logging untuk optional fields:
                    result::class.java.declaredFields.forEach { field ->
                        field.isAccessible = true
                        try {
                            val value = field.get(result)
                            if (value != null) {
                                Log.d(TAG, "   - ${field.name}: $value")
                            } else {
                                Log.d(TAG, "   - ${field.name}: null")
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "   - ${field.name}: [cannot access]")
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Failed to parse Cloudinary response")
                }

                return@use result
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload error", e)
            null
        }
    }

    // ✅ ENHANCED VALIDATION
    private fun validateRegistrationInputs(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        when {
            name.isEmpty() -> {
                showValidationError("Full name is required")
                return false
            }

            name.length < 2 -> {
                showValidationError("Full name must be at least 2 characters")
                return false
            }

            email.isEmpty() -> {
                showValidationError("Email is required")
                return false
            }

            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                showValidationError("Please enter a valid email address")
                return false
            }

            password.isEmpty() -> {
                showValidationError("Password is required")
                return false
            }

            password.length < 6 -> {
                showValidationError("Password must be at least 6 characters")
                return false
            }

            confirmPassword.isEmpty() -> {
                showValidationError("Please confirm your password")
                return false
            }

            password != confirmPassword -> {
                showValidationError("Passwords do not match")
                return false
            }
        }
        return true
    }

    private fun showValidationError(message: String) {
        Log.d(TAG, "⚠️ Validation error: $message")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ✅ GOOGLE SIGN IN FOR REGISTRATION
    private fun performGoogleSignInForRegister() {
        Log.d(TAG, "🔄 Starting Google Sign In for registration...")

        showLoading("Preparing Google Sign In...")

        // Show loading state
        val googleSignInButton: Button = findViewById(R.id.googleSignInButton)
        googleSignInButton.isEnabled = false
        googleSignInButton.text = getString(R.string.signing_up)

        lifecycleScope.launch {
            try {
                // ✅ Check Web Client ID configuration
                val webClientId = getString(R.string.web_client_id)

                if (webClientId.isEmpty() || !webClientId.contains("apps.googleusercontent.com")) {
                    throw IllegalStateException("Invalid Web Client ID configuration")
                }

                Log.d(TAG, "Web Client ID configured: ${webClientId.length} characters")

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false) // Show account chooser
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .setNonce(generateNonce()) // Security nonce
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption as CredentialOption)
                    .build()

                Log.d(TAG, "Credential request created, launching Google Sign In...")
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@RegisterActivity,
                )

                handleGoogleCredentialResult(result)

            } catch (e: GetCredentialException) {
                Log.e(TAG, "❌ Credential Manager error", e)
                hideLoading()
                handleCredentialError(e)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Unexpected error in Google Sign In", e)
                hideLoading()
                handleUnexpectedError(e)
            }
        }
    }

    // ✅ HANDLE GOOGLE CREDENTIAL RESULT
    private fun handleGoogleCredentialResult(result: GetCredentialResponse) {
        try {
            Log.d(TAG, "✅ Google credential received, processing...")

            showLoading("Authenticating with Google...")

            when (val credential = result.credential) {
                is GoogleIdTokenCredential -> {
                    Log.d(TAG, "Google ID Token credential received")
                    val googleIdToken = credential.idToken
                    Log.d(TAG, "Google ID Token length: ${googleIdToken.length}")

                    showLoading("Connecting to Firebase...")
                    firebaseAuthWithGoogleForRegister(googleIdToken)
                }

                is CustomCredential -> {
                    Log.d(TAG, "CustomCredential received, checking type...")

                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        try {
                            Log.d(TAG, "Converting CustomCredential to GoogleIdTokenCredential")
                            showLoading("Processing Google account...")

                            val googleIdTokenCredential = GoogleIdTokenCredential
                                .createFrom(credential.data)

                            val googleIdToken = googleIdTokenCredential.idToken
                            Log.d(TAG, "Google ID Token: ${googleIdToken.take(20)}...")

                            showLoading("Connecting to Firebase...")
                            firebaseAuthWithGoogleForRegister(googleIdToken)
                        } catch (e: GoogleIdTokenParsingException) {
                            Log.e(TAG, "❌ Google ID Token parsing error", e)
                            hideLoading()
                            resetGoogleButtonState()
                            Toast.makeText(this, "Invalid Google credential", Toast.LENGTH_SHORT)
                                .show()
                        }
                    } else {
                        Log.e(TAG, "❌ Unexpected CustomCredential type: ${credential.type}")
                        hideLoading()
                        resetGoogleButtonState()
                        Toast.makeText(this, "Unexpected credential type", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                else -> {
                    Log.e(TAG, "❌ Unexpected credential type: ${credential::class.java}")
                    hideLoading()
                    resetGoogleButtonState()
                    Toast.makeText(this, "Unexpected credential type", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "❌ Google ID Token parsing error", e)
            hideLoading()
            resetGoogleButtonState()
            Toast.makeText(this, "Invalid Google credential", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling credential result", e)
            hideLoading()
            resetGoogleButtonState()
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ FIREBASE AUTH WITH GOOGLE FOR REGISTRATION
    private fun firebaseAuthWithGoogleForRegister(idToken: String) {
        try {
            Log.d(TAG, "🔐 Authenticating with Firebase using Google ID Token...")

            showLoading("Finalizing authentication...")

            val credential = GoogleAuthProvider.getCredential(idToken, null)

            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    hideLoading()
                    resetGoogleButtonState()

                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        resetGoogleButtonState()

                        if (user == null) {
                            redirectToMainActivity(); return@addOnCompleteListener
                        }

                        val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false
                        Toast.makeText(
                            this,
                            if (isNewUser) "Account created!" else "Welcome back!",
                            Toast.LENGTH_SHORT
                        ).show()

                        showLoading("Setting up your profile...")

                        lifecycleScope.launch {
                            val googlePhoto = user.photoUrl?.toString()
                            var finalUrl: String? = null
                            var publicId: String? = null

                            // HANYA upload jika ada URL foto Google
                            if (!googlePhoto.isNullOrBlank()) {
                                try {
                                    val resp = uploadUrlToCloudinary(
                                        fileUrl = googlePhoto,
                                        cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME,
                                        uploadPreset = BuildConfig.CLOUDINARY_UNSIGNED_PRESET,
                                        folder = "profile_photos/${user.uid}"
                                    )
                                    finalUrl = resp?.secure_url ?: googlePhoto
                                    publicId = resp?.public_id
                                } catch (e: Exception) {
                                    Log.e(TAG, "Cloudinary upload failed", e)
                                    finalUrl = googlePhoto // fallback, atau biarkan kosong
                                }
                            }

                            // Upsert dokumen user di Firestore
                            val data = hashMapOf(
                                "uid" to user.uid,
                                "email" to (user.email ?: ""),
                                "name" to (user.displayName ?: ""),
                                "photoUrl" to (finalUrl ?: ""),           // jika null → simpan ""
                                "cloudinaryPublicId" to (publicId ?: ""),
                                "loginMethod" to "google",
                                "emailVerified" to user.isEmailVerified,
                                "updatedAt" to System.currentTimeMillis(),
                                "createdAt" to System.currentTimeMillis()
                            )

                            firestore.collection("users").document(user.uid)
                                .set(data, SetOptions.merge())
                                .addOnCompleteListener {
                                    // teruskan ke form melengkapi profil
                                    hideLoading()
                                    redirectToMainActivity()
                                }
                        }
                    } else {
                        Log.e(TAG, "❌ Firebase Google authentication failed", task.exception)
                        Toast.makeText(
                            this,
                            "Authentication failed: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in Firebase Google authentication", e)
            hideLoading()
            resetGoogleButtonState()
            Toast.makeText(this, "Authentication error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ✅ ERROR HANDLING
    private fun handleCredentialError(e: GetCredentialException) {
        resetGoogleButtonState()

        when (e::class.java.simpleName) {
            "GetCredentialCancellationException" -> {
                Log.d(TAG, "⚠️ User cancelled Google Sign In")
                Toast.makeText(this, "Sign up cancelled", Toast.LENGTH_SHORT).show()
            }

            "GetCredentialInterruptedException" -> {
                Log.e(TAG, "❌ Google Sign In interrupted", e)
                Toast.makeText(this, "Sign up interrupted", Toast.LENGTH_SHORT).show()
            }

            "NoCredentialException" -> {
                Log.e(TAG, "❌ No Google credentials available", e)
                Toast.makeText(
                    this,
                    "No Google account found. Please add a Google account.",
                    Toast.LENGTH_LONG
                ).show()
            }

            else -> {
                Log.e(TAG, "❌ Unknown credential error: ${e::class.java.simpleName}", e)
                Toast.makeText(this, "Google Sign In failed: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun handleUnexpectedError(e: Exception) {
        resetGoogleButtonState()
        Log.e(TAG, "❌ Unexpected error", e)
        Toast.makeText(this, "An unexpected error occurred: ${e.message}", Toast.LENGTH_LONG).show()
    }

    // ✅ HELPER FUNCTIONS
    private fun resetGoogleButtonState() {
        try {
            val googleSignInButton: Button = findViewById(R.id.googleSignInButton)
            googleSignInButton.isEnabled = true
            googleSignInButton.text = getString(R.string.google_btn_helper)
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting Google button state", e)
        }
    }

    private fun generateNonce(): String {
        return try {
            val rawNonce = UUID.randomUUID().toString()
            val bytes = rawNonce.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            digest.fold("") { str, it -> str + "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating nonce, using fallback", e)
            UUID.randomUUID().toString().replace("-", "")
        }
    }

    private fun setupOptionalSocialLogins() {
        try {
            // Facebook (optional)
            findViewById<Button>(R.id.facebookSignInButton)?.setOnClickListener {
                Toast.makeText(this, "Facebook Sign Up coming soon!", Toast.LENGTH_SHORT).show()
            }

            // Twitter (optional)
            findViewById<Button>(R.id.twitterSignInButton)?.setOnClickListener {
                Toast.makeText(this, "Twitter Sign Up coming soon!", Toast.LENGTH_SHORT).show()
            }

            // LinkedIn (optional)
            findViewById<Button>(R.id.linkedinSignInButton)?.setOnClickListener {
                Toast.makeText(this, "LinkedIn Sign Up coming soon!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Optional social login buttons not found in layout")
        }
    }

    // ✅ REDIRECT TO MAIN ACTIVITY
    private fun redirectToMainActivity() {
        try {
            Log.d(TAG, "🔄 Redirecting to user information...")

            val intent = Intent(this, UserInformationActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            // Add user info
            auth.currentUser?.let { user ->
                intent.putExtra("user_email", user.email)
                intent.putExtra("user_name", user.displayName)
                intent.putExtra("user_photo_url", user.photoUrl?.toString())
                intent.putExtra(
                    "login_method",
                    if (user.providerData.any { it.providerId == "google.com" }) "google" else "email"
                )
                intent.putExtra("email_verified", user.isEmailVerified)
                intent.putExtra("from_registration", true)
            }

            startActivity(intent)
            finish()

            Log.d(TAG, "✅ Redirected to MainActivity")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error redirecting to MainActivity", e)
        }
    }

    private fun showLoading(message: String = "Signing in with Google...") {
        try {
            val loadingOverlay: FrameLayout = findViewById(R.id.loadingOverlay)
            val loadingText: TextView = findViewById(R.id.loadingText)

            loadingText.text = message
            loadingOverlay.visibility = View.VISIBLE
            loadingOverlay.isClickable = true
            loadingOverlay.isFocusable = true

            Log.d(TAG, "⏳ Loading shown: $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing loading", e)
        }
    }

    private fun hideLoading() {
        try {
            val loadingOverlay: FrameLayout = findViewById(R.id.loadingOverlay)
            loadingOverlay.visibility = View.GONE
            loadingOverlay.isClickable = false
            loadingOverlay.isFocusable = false

            Log.d(TAG, "⏳ Loading hidden")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error hiding loading", e)
        }
    }
}