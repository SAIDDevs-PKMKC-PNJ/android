package com.pkm.said

import android.content.Context.INPUT_METHOD_SERVICE
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthException

class ForgotPasswordBottomSheet : BottomSheetDialogFragment() {

    // ✅ STEP 4A: Declare variables
    private lateinit var auth: FirebaseAuth
    private val tag = "ForgotPasswordBottomSheet"

    // View references
    private lateinit var emailInput: EditText
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var sendButton: Button
    private lateinit var cancelButton: Button
    private lateinit var closeButton: ImageButton
    private lateinit var progressBar: ProgressBar

    // ✅ STEP 4B: Create View
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(tag, "=== FORGOT PASSWORD BOTTOM SHEET ===")
        Log.d(tag, "Current Date and Time (UTC): 2025-09-03 13:40:36")
        Log.d(tag, "Current User's Login: itsLuxra")
        Log.d(tag, "🎭 Bottom Sheet overlay created on LoginActivity")

        return inflater.inflate(R.layout.overlay_forgot_password, container, false)
    }

    // ✅ STEP 4C: Setup Views and Logic
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()

        // Find views
        findViews(view)

        // Configure bottom sheet behavior
        setupBottomSheetBehavior()

        // Setup click listeners
        setupClickListeners()

        // Pre-fill email if provided
        prefillEmailIfAvailable()

        Log.d(tag, "✅ Bottom Sheet setup completed")
    }

    // ✅ STEP 4D: Find all views
    private fun findViews(view: View) {
        emailInput = view.findViewById(R.id.emailInput)
        emailInputLayout = view.findViewById(R.id.emailInputLayout)
        sendButton = view.findViewById(R.id.sendButton)
        cancelButton = view.findViewById(R.id.cancelButton)
        closeButton = view.findViewById(R.id.closeButton)
        progressBar = view.findViewById(R.id.progressBar)

        Log.d(tag, "📱 All views found and referenced")
    }

    // ✅ STEP 4E: Configure bottom sheet sliding behavior
    private fun setupBottomSheetBehavior() {
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED // Start fully opened
                behavior.skipCollapsed = true // Don't allow half-collapsed state
                behavior.isDraggable = true // Allow swipe to dismiss

                Log.d(tag, "🎭 Bottom sheet behavior: slides from bottom, dismissible by swipe")
            }
        }
    }

    // ✅ STEP 4F: Setup all click listeners
    private fun setupClickListeners() {
        // Send reset email button
        sendButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            Log.d(tag, "📤 Send button clicked for email: ${email.take(5)}...")

            if (validateEmail(email)) {
                sendPasswordResetEmail(email)
            } else {
            showShortToast("Please enter your email address")
            Log.d(tag, "⚠️ Email validation failed - showing user feedback")
        }
        }

        // Cancel button - dismiss bottom sheet
        cancelButton.setOnClickListener {
            Log.d(tag, "❌ Cancel clicked - returning to LoginActivity")
            dismiss() // Bottom sheet disappears, LoginActivity shows
        }

        // Close button (X) - same as cancel
        closeButton.setOnClickListener {
            Log.d(tag, "✖️ Close clicked - returning to LoginActivity")
            dismiss()
        }

        emailInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val email = emailInput.text.toString().trim()
                if (email.isNotEmpty()) {
                    validateEmail(email) // Show error immediately when user leaves field
                }
            }
        }
    }

    // ✅ STEP 4G: Pre-fill email from LoginActivity if available
    private fun prefillEmailIfAvailable() {
        arguments?.getString(ARG_EMAIL)?.let { email ->
            emailInput.setText(email)
            emailInput.setSelection(email.length) // Put cursor at end
            Log.d(tag, "📧 Pre-filled email from LoginActivity: ${email.take(5)}...")
        }
    }

    // ✅ STEP 4H: Validate email input
    private fun validateEmail(email: String): Boolean {
        return when {
            email.isEmpty() -> {
                emailInputLayout.error = "Email is required"
                emailInputLayout.requestFocus()
                Log.d(tag, "⚠️ Validation failed: Email is empty")
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailInputLayout.error = "Please enter a valid email address"
                emailInputLayout.requestFocus()
                Log.d(tag, "⚠️ Validation failed: Invalid email format")
                false
            }
            else -> {
                emailInputLayout.error = null // Clear any previous error
                Log.d(tag, "✅ Email validation passed")
                true
            }
        }
    }

    // ✅ STEP 4I: Send password reset email via Firebase
    private fun sendPasswordResetEmail(email: String) {
        Log.d(tag, "📧 Sending password reset email to: ${email.take(5)}...")

        hideKeyboard()
        setLoadingState(true)

        // Firebase send email
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                // Hide loading state
                setLoadingState(false)

                if (task.isSuccessful) {
                    Log.d(tag, "✅ Password reset email sent successfully")
                    showSuccessAndDismiss(email)
                } else {
                    Log.e(tag, "❌ Failed to send password reset email", task.exception)
                    showErrorMessage(task.exception)

                    if (isNetworkError(task.exception)) {
                        showShortToast("Network error. Periksa koneksi internet")
                    }
                }
            }
    }

    // ✅ STEP 4J: Show loading state
    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            sendButton.isEnabled = false
            sendButton.text = "Sending..."
            progressBar.visibility = View.VISIBLE
            Log.d(tag, "⏳ Loading state: ON")
        } else {
            sendButton.isEnabled = true
            sendButton.text = "Send Reset Email"
            progressBar.visibility = View.GONE
            Log.d(tag, "⏳ Loading state: OFF")
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager = context?.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(emailInput.windowToken, 0)
    }

    private fun showShortToast(message: String) {
        context?.let { ctx ->
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ STEP 4K: Show success message and dismiss
    private fun showSuccessAndDismiss(email: String) {
        context?.let { ctx ->
            androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle("Email Sent! 📧")
                .setMessage("We've sent password reset instructions to:\n\n$email\n\nPlease check your email and follow the link to reset your password.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    dismiss() // Close bottom sheet, back to LoginActivity
                }
                .setCancelable(false)
                .show()
        }

        Log.d(tag, "✅ Success dialog shown, will return to LoginActivity after user clicks OK")
    }

    // ✅ STEP 4L: Handle and show error messages
    private fun showErrorMessage(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthInvalidUserException -> {
                "No account found with this email address"
            }
            is FirebaseAuthException -> {
                when (exception.errorCode) {
                    "ERROR_INVALID_EMAIL" -> "Please enter a valid email address"
                    "ERROR_USER_NOT_FOUND" -> "No account found with this email"
                    "ERROR_TOO_MANY_REQUESTS" -> "Too many reset attempts. Please try again later"
                    else -> "Failed to send reset email. Please try again"
                }
            }
            else -> "Network error. Please check your connection and try again"
        }

        emailInputLayout.error = errorMessage
        Log.d(tag, "❌ Error displayed: $errorMessage")

        // Bottom sheet stays open for user to retry
    }

    private fun isNetworkError(exception: Exception?): Boolean {
        return exception?.message?.contains("network", ignoreCase = true) == true ||
                exception?.message?.contains("connection", ignoreCase = true) == true ||
                exception?.message?.contains("timeout", ignoreCase = true) == true
    }

    // ✅ STEP 4M: Companion object for creating instances
    companion object {
        private const val ARG_EMAIL = "email"

        fun newInstance(email: String? = null): ForgotPasswordBottomSheet {
            val fragment = ForgotPasswordBottomSheet()
            val args = Bundle()
            email?.let { args.putString(ARG_EMAIL, it) }
            fragment.arguments = args
            return fragment
        }
    }
}