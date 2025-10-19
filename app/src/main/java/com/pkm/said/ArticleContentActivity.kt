package com.pkm.said

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.pkm.said.databinding.ActivityArticleContentBinding
import com.pkm.said.screening.ScreeningActivity
import com.pkm.said.util.SessionManager

class ArticleContentActivity : AppCompatActivity(), Said.VoiceActivityCallback {

    private lateinit var binding: ActivityArticleContentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArticleContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val json = intent.getStringExtra(KEY_JSON)
        val article = try {
            Gson().fromJson(json, ArticleItem::class.java)
        } catch (e: Exception) {
            null
        }

        if (article == null || article.title.isEmpty()) {
            handleNullArticle()
            return
        }

        Said.getInstance().registerActivityCallback(this.localClassName, this)
        bindArticle(article)
        setupClickListeners(article)
    }

    override fun onVoiceCommand(command: String, extras: Bundle?): Boolean {
        Log.d(TAG, "🎤 Voice command received in News: $command")
        return when (command.toLowerCase()) {
            // SCREENING - sama seperti MainActivity
            "tes stroke", "mulai screening", "screening", "mulai tes" -> {
                startStrokeScreening()
                true
            }
            // EMERGENCY - sama seperti MainActivity
            "darurat", "emergency", "tolong" -> {
                handleEmergencyFromVoice()
                true
            }
            // DASHBOARD - kembali ke MainActivity
            "dashboard", "home", "kembali" -> {
                navigateToDashboard()
                true
            }
            else -> false
        }
    }

    // ✅ NAVIGATION - UPDATE UNTUK SCREENING & EMERGENCY
    override fun onNavigateTo(destination: String): Boolean {
        Log.d(TAG, "🧭 Navigation command in News: $destination")
        return when (destination.toLowerCase()) {
            "dashboard", "home" -> {
                navigateToDashboard()
                true
            }
            "screening" -> {
                startStrokeScreening()
                true
            }
            "emergency" -> {
                handleEmergencyFromVoice()
                true
            }
            else -> false
        }
    }

    // ✅ SUPPORTED COMMANDS - UPDATE DENGAN SCREENING & EMERGENCY
    override fun getSupportedCommands(): List<String> {
        return listOf(
            "tes stroke", "mulai screening", "screening", "mulai tes",
            "darurat", "emergency", "tolong",
            "dashboard", "home", "kembali"
        )
    }

    // ✅ STROKE SCREENING - SAMA SEPERTI DI MAINACTIVITY
    private fun startStrokeScreening() {
        try {
            Log.d(TAG, "🏥 Starting stroke screening from News...")

            // Dapatkan username seperti di MainActivity
            val username = getCurrentUsername()
            Log.d(TAG, "Username: $username")

            // ✅ GUNAKAN METHOD start() DARI SCREENINGACTIVITY - sama seperti MainActivity
            ScreeningActivity.start(this, username, startNew = true)

            if (!isFinishing && !isDestroyed) {
                Toast.makeText(
                    this,
                    "🏥 Starting screening for $username",
                    Toast.LENGTH_SHORT
                ).show()
            }
            Log.d(TAG, "✅ ScreeningActivity started successfully from News")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting stroke screening from News", e)
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "❌ Failed to start screening", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ EMERGENCY HANDLER - SAMA SEPERTI DI MAINACTIVITY
    private fun handleEmergencyFromVoice() {
        try {
            Log.d(TAG, "🚨 Emergency from voice command in News - starting EmergencyActivity")
            val intent = Intent(this, EmergencyActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_voice_command", true)
                putExtra("from_article_content", true) // Tambahkan identifier
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start EmergencyActivity from News, using fallback", e)
            // Fallback ke dialog emergency
            showEmergencyFallbackDialog()
        }
    }

    // ✅ EMERGENCY FALLBACK DIALOG - SAMA SEPERTI DI MAINACTIVITY
    private fun showEmergencyFallbackDialog() {
        Log.d(TAG, "🚨 Emergency fallback in News - showing dialog")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🚨 Emergency Detected")
            .setMessage("Voice assistant detected emergency situation. Please manually open emergency features.")
            .setPositiveButton("Open Emergency") { _, _ ->
                // Try to start EmergencyActivity again dengan approach berbeda
                try {
                    val intent = Intent(this, EmergencyActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("from_article_content", true)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open emergency screen", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "❌ Emergency fallback also failed in News", e)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    // ✅ GET CURRENT USERNAME - SAMA SEPERTI DI MAINACTIVITY
    private fun getCurrentUsername(): String {
        return try {
            SessionManager.getUserName(this) ?: getFallbackUsername()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting username in News", e)
            getFallbackUsername()
        }
    }

    private fun getFallbackUsername(): String {
        return try {
            // Coba dapatkan dari Firebase Auth sebagai fallback
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            when {
                firebaseUser?.displayName != null -> {
                    val username = firebaseUser.displayName!!
                    // Simpan ke SessionManager untuk konsistensi
                    saveUsernameToSessionManager(username)
                    username
                }

                firebaseUser?.email != null -> {
                    val email = firebaseUser.email!!
                    val usernameFromEmail = email.substringBefore("@")
                    saveUsernameToSessionManager(usernameFromEmail)
                    usernameFromEmail
                }

                else -> generateAnonymousUsername()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting fallback username", e)
            generateAnonymousUsername()
        }
    }

    private fun saveUsernameToSessionManager(username: String) {
        try {
            // Jika user sudah login di Firebase, update SessionManager
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            firebaseUser?.let { user ->
                SessionManager.saveBasicFromFirebase(this, user, "auto_detected")
            }
            Log.d(TAG, "✅ Username saved to SessionManager: $username")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving username to SessionManager", e)
        }
    }

    private fun generateAnonymousUsername(): String {
        val anonymousUser = "user_${System.currentTimeMillis()}"
        Log.d(TAG, "Generated anonymous username in News: $anonymousUser")
        return anonymousUser
    }

    private fun navigateToDashboard() {
        try {
            Log.d(TAG, "🚀 Navigating to Dashboard - finishing NewsActivity")

            // Cukup finish() karena MainActivity sudah default ke dashboard
            finish()

            // Optional: smooth transition animation
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

            Log.d(TAG, "✅ Navigation to dashboard completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to dashboard", e)
            // Fallback - tetap coba finish
            finish()
        }
    }

    private fun bindArticle(article: ArticleItem) {
        binding.tvTitle.text = article.title
        binding.tvDate.text = article.date

        // Use author field instead of source for author name
        binding.tvAuthorName.text = article.author.ifBlank {
            getString(R.string.article_author_default)
        }
        binding.tvAuthorRole.text = getString(R.string.article_author_role)

        val content = when {
            article.content.isNotBlank() -> article.content
            article.description.isNotBlank() -> article.description
            else -> getString(R.string.article_lorem_long)
        }
        binding.tvContent.text = content

        binding.tvBadge.text = if (article.category.isNotBlank()) {
            article.category.uppercase()
        } else {
            getString(R.string.HeadlineTemplate)
        }

        Glide.with(this)
            .load(article.imageUrl)
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.placeholder_oval)
            .into(binding.ivHero)
    }

    private fun setupClickListeners(article: ArticleItem) {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.fabShare.setOnClickListener {
            shareArticle(article)
        }
    }

    private fun shareArticle(article: ArticleItem) {
        val preview = when {
            article.description.isNotBlank() -> article.description
            article.content.isNotBlank() -> article.content.take(160)
            else -> ""
        }
        val shareText = buildString {
            append(article.title)
            append("\n\n")
            append(preview)
            // Include URL if available
            if (article.url.isNotBlank()) {
                append("\n\n")
                append(article.url)
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, article.title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_article)))
    }

    private fun handleNullArticle() {
        showToast("Artikel tidak ditemukan")

        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "dashboard")
        }.also { startActivity(it) }

        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Said.getInstance().unregisterActivityCallback(this.localClassName)
    }

    companion object {

        private const val TAG = "ArticleContentActivity"
        const val KEY_JSON = "article_json"

        fun start(context: Context, article: ArticleItem) {
            val intent = Intent(context, ArticleContentActivity::class.java).apply {
                putExtra(KEY_JSON, Gson().toJson(article))
            }
            context.startActivity(intent)
        }
    }

}