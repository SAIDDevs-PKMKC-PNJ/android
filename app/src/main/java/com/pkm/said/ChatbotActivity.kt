package com.pkm.said

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.pkm.said.util.ChatViewModel
import kotlinx.coroutines.launch
import com.pkm.said.adapter.MessageAdapter
import com.pkm.said.databinding.ActivityChatbotBinding
import com.pkm.said.screening.ScreeningActivity
import com.pkm.said.service.VoiceActivationService
import com.pkm.said.util.AuthManager
import com.pkm.said.util.InputMode
import com.pkm.said.util.SessionManager
import kotlinx.coroutines.Job
import java.util.Locale
import kotlin.math.max

class ChatbotActivity : AppCompatActivity(), Said.VoiceActivityCallback {

    companion object {
        private const val TAG = "ChatbotActivity"
        private const val EXTRA_INITIAL_MESSAGE = "extra_initial_message"

        fun start(context: Context, initialMessage: String? = null) {
            val intent = Intent(context, ChatbotActivity::class.java).apply {
                if (!initialMessage.isNullOrBlank()) {
                    putExtra(EXTRA_INITIAL_MESSAGE, initialMessage)
                }
            }
            context.startActivity(intent)
        }
    }

    // View Binding
    private lateinit var binding: ActivityChatbotBinding

    // State & UI
    private var inputMode: InputMode = InputMode.KEYBOARD
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<MessageAdapter.ChatMessage>()
    private val viewModel: ChatViewModel by viewModels()

    // STT
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var isListening = false
    private var wasVoiceServiceRunning = false

    // Overlay wave (jika sebelumnya ada dummy animasi, sekarang dimatikan)
    private var waveJob: Job? = null

    // Permission launcher
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Mulai benar-benar rekam
            switchInputMode(InputMode.RECORDING)
            startListening()
            stopWaveAnimation() // pastikan tidak ada dummy animasi
        } else {
            Toast.makeText(this, "Izin mikrofon dibutuhkan untuk input suara", Toast.LENGTH_LONG).show()
            switchInputMode(InputMode.MIC_IDLE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "=== CHATBOT ACTIVITY DEBUG ===")
        Log.d(TAG, "onCreate called")

        try {
            // ✅ Keyboard resize + edge-to-edge
            setupWindowForKeyboard()

            binding = ActivityChatbotBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "✅ View Binding setup completed")

            Said.getInstance().registerActivityCallback(this.localClassName, this)

            setupViews()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up activity", e)
            finish()
        }
    }

    private fun setupViews() {
        try {
            setupRecyclerView()
            setupSTT()
            setupPressToTalkGesture()

            setupChipListeners()
            observeViewModel()
            setupUiListeners()
            switchInputMode(InputMode.KEYBOARD)

            // ✅ Terapkan system/IME insets sekali (menggantikan keyboard listener lama)
            applySystemInsetsOnce()

            Log.d(TAG, "✅ ChatbotActivity setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in setupViews", e)
            showError("Terjadi kesalahan saat memuat chatbot")
        }
    }

    // ✅ VOICE COMMAND HANDLER - SCREENING, EMERGENCY, DAN DASHBOARD
    override fun onVoiceCommand(command: String, extras: Bundle?): Boolean {
        Log.d(TAG, "🎤 Voice command received in Chatbot: $command")
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
        Log.d(TAG, "🧭 Navigation command in Chatbot: $destination")
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
            Log.d(TAG, "🏥 Starting stroke screening from Chatbot...")

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
            Log.d(TAG, "✅ ScreeningActivity started successfully from Chatbot")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting stroke screening from Chatbot", e)
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "❌ Failed to start screening", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ EMERGENCY HANDLER - SAMA SEPERTI DI MAINACTIVITY
    private fun handleEmergencyFromVoice() {
        try {
            Log.d(TAG, "🚨 Emergency from voice command in Chatbot - starting EmergencyActivity")
            val intent = Intent(this, EmergencyActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_voice_command", true)
                putExtra("from_chatbot", true) // Tambahkan identifier
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start EmergencyActivity from Chatbot, using fallback", e)
            // Fallback ke dialog emergency
            showEmergencyFallbackDialog()
        }
    }

    // ✅ EMERGENCY FALLBACK DIALOG - SAMA SEPERTI DI MAINACTIVITY
    private fun showEmergencyFallbackDialog() {
        Log.d(TAG, "🚨 Emergency fallback in Chatbot - showing dialog")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🚨 Emergency Detected")
            .setMessage("Voice assistant detected emergency situation. Please manually open emergency features.")
            .setPositiveButton("Open Emergency") { _, _ ->
                // Try to start EmergencyActivity again dengan approach berbeda
                try {
                    val intent = Intent(this, EmergencyActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("from_chatbot", true)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open emergency screen", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "❌ Emergency fallback also failed in Chatbot", e)
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
            Log.e(TAG, "Error getting username in Chatbot", e)
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
        Log.d(TAG, "Generated anonymous username in Chatbot: $anonymousUser")
        return anonymousUser
    }

    private fun navigateToDashboard() {
        try {
            Log.d(TAG, "🚀 Navigating to Dashboard - finishing ChatbotActivity")

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

    // --- Edge-to-edge + resize oleh sistem saat IME muncul
    @Suppress("DEPRECATION")
    private fun setupWindowForKeyboard() {
        try {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            WindowCompat.setDecorFitsSystemWindows(window, true)
        } catch (_: Exception) { }
    }

    // --- Terapkan insets (status/nav/IME) ke header & input/overlay
    private fun applySystemInsetsOnce() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = max(sys.bottom, ime.bottom)

            // Header: padding top = tinggi status bar
            binding.customHeaderCard.updatePadding(top = sys.top)

            // Input & overlay: bottom margin = nav/IME + 20dp (sesuai baseline XML)
            fun setBottomMargin(v: View) {
                val lp = v.layoutParams as RelativeLayout.LayoutParams
                lp.bottomMargin = bottom + dpToPx(20)
                v.layoutParams = lp
            }
            setBottomMargin(binding.inputContainer)
            setBottomMargin(binding.recordingOverlay)

            insets
        }
    }

    // ✅ Chip listeners dengan auto-hide
    private fun setupChipListeners() {
        Log.d(TAG, "Setting up chip listeners...")

        try {
            val chips = mapOf(
                binding.chipStroke to "Apa itu stroke?",
                binding.chipPenyebab to "Penyebab stroke?",
                binding.chipDeteksi to "Bagaimana metode deteksi stroke?",
                binding.chipGejala1 to "Apa saja gejala stroke?",
                binding.chipGejala2 to "Bagaimana pengobatan stroke?",
                binding.chipGejala3 to "Cara pencegahan stroke?"
            )

            chips.forEach { (chip, text) ->
                chip.setOnClickListener {
                    Log.d(TAG, "Chip clicked: $text")

                    // ✅ Sembunyikan chips setelah diklik
                    hideSuggestionChips()

                    viewModel.sendMessage(text)
                }
            }

            Log.d(TAG, "✅ Chip listeners setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up chip listeners", e)
        }
    }

    // ✅ Hide dengan animation
    private fun hideSuggestionChips() {
        binding.suggestionCard.animate()
            .translationY(-binding.suggestionCard.height.toFloat())
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                binding.suggestionCard.visibility = View.GONE
            }
            .start()
    }

    // ✅ Show dengan animation
    private fun showSuggestionChips() {
        binding.suggestionCard.visibility = View.VISIBLE
        binding.suggestionCard.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    // ---------------------------
    // RecyclerView (chat)
    // ---------------------------
    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messageList) { position ->
            onReloadResponse(position)
        }
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatbotActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
            setHasFixedSize(true)

            // ✅ Hide chips ketika scroll
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 5) { // Scroll down
                        hideSuggestionChips()
                    } else if (dy < -5) { // Scroll up
                        showSuggestionChips()
                    }
                }
            })
        }
    }

    // ---------------------------
    // UI Listeners (buttons)
    // ---------------------------
    private fun setupUiListeners() {
        binding.btnBack.setOnClickListener { handleBackNavigation() }

        // Toggle Keyboard <-> Mic Idle
        binding.btnVoice.setOnClickListener {
            if (inputMode == InputMode.KEYBOARD) switchInputMode(InputMode.MIC_IDLE)
            else switchInputMode(InputMode.KEYBOARD)
        }

        // Kirim text dari keyboard
        binding.btnSend.setOnClickListener { handleSendMessage() }

        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                handleSendMessage()
                true
            } else false
        }

        // Tombol keyboard (di MIC_IDLE row)
        binding.btnKeyboard.setOnClickListener { switchInputMode(InputMode.KEYBOARD) }
    }

    private fun switchInputMode(mode: InputMode) {
        inputMode = mode
        when (mode) {
            InputMode.KEYBOARD -> {
                binding.inputAnimator.displayedChild = 0
                binding.recordingOverlay.visibility = View.GONE
            }
            InputMode.MIC_IDLE -> {
                binding.inputAnimator.displayedChild = 1
                binding.recordingOverlay.visibility = View.GONE
                binding.tvMicHint.text = getString(R.string.mic_hint)
            }
            InputMode.RECORDING -> {
                binding.recordingOverlay.visibility = View.VISIBLE
                binding.tvRecHint.text = getString(R.string.rec_hint)
            }
            InputMode.CANCEL_HINT -> {
                binding.recordingOverlay.visibility = View.VISIBLE
                binding.tvRecHint.text = getString(R.string.rec_hint_2)
                val colorStateList = ContextCompat.getColorStateList(this, R.color.warning_color)
                binding.tvRecHint.setTextColor(colorStateList)
            }
        }
    }


    // ✅ Back navigation
    private fun handleBackNavigation() {
        try {
            Log.d(TAG, "🔄 Handling back navigation...")
            finish()
            Log.d(TAG, "✅ Back navigation completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in back navigation", e)
        }
    }

    private fun observeViewModel() {
        val initial = intent.getStringExtra(EXTRA_INITIAL_MESSAGE)

        // Flag untuk memastikan pesan awal HANYA dikirim sekali
        var initialMessageSent = false

        lifecycleScope.launch {
            // Mengamati StateFlow messages dari ViewModel
            viewModel.messages.collect { updatedMessages ->

                // ⭐ Update data di adapter (tanpa removePrefix, karena ViewModel sudah mengirim data bersih)
                messageList.clear()
                messageList.addAll(updatedMessages)
                messageAdapter.notifyDataSetChanged()

                // Gulir ke bawah
                if (messageList.isNotEmpty()) {
                    binding.rvMessages.post {
                        binding.rvMessages.smoothScrollToPosition(messageList.size - 1)
                    }
                }

                // Update visibilitas chip setelah setiap update
                updateChipsVisibility()

                if (!initial.isNullOrBlank() && !initialMessageSent && updatedMessages.isEmpty()) {
                    viewModel.sendMessage(initial)
                    initialMessageSent = true
                }
            }
        }
        val welcomePrompt = "TOLONG JAWAB SEKARANG DENGAN PESAN SELAMAT DATANG: \"Halo! Saya Said. Apa yang bisa saya bantu hari ini seputar topik kesehatan dan stroke?\""
        viewModel.sendInitialPrompt(welcomePrompt)
    }

    // ✅ Send message
    private fun handleSendMessage() {
        try {
            val message = binding.etMessage.text.toString().trim()

            if (message.isNotEmpty()) {
                Log.d(TAG, "Sending message: $message")
                viewModel.sendMessage(message)
                binding.etMessage.text?.clear()
            } else {
                showToast("Pesan tidak boleh kosong!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling send message", e)
            showError("Gagal mengirim pesan")
        }
    }

    // ✅ Update clear messages function
    fun clearMessagesAndShowChips() {
        viewModel.clearChatHistory()
        showSuggestionChips()
    }

    // ✅ Atau sembunyikan chips hanya ketika ada cukup banyak pesan
    private fun shouldShowChips(): Boolean {
        return messageList.size <= 2 // Hanya tampilkan chips jika <= 2 pesan
    }

    private fun updateChipsVisibility() {
        if (shouldShowChips()) {
            showSuggestionChips()
        } else {
            hideSuggestionChips()
        }
    }

    private fun onReloadResponse(position: Int) {
        if (position > 0 && position < messageList.size) {
            val userPromptMessage = messageList[position - 1]

            if (userPromptMessage.isUser) {
                viewModel.reloadMessage(
                    promptToReload = userPromptMessage.text,
                    botMessageIndex = position
                )
            } else {
                showToast("Gagal me-reload. Pesan sebelumnya bukan dari Anda.")
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPressToTalkGesture() {
        var startY = 0f
        val cancelThreshold = 80f * resources.displayMetrics.density

        binding.pressToTalkIdle.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    wasVoiceServiceRunning = isVoiceServiceActive()

                    // 2. Jika Service aktif, hentikan sementaara
                    if (wasVoiceServiceRunning) {
                        pauseVoiceService() // <-- Fungsi yang akan kita buat
                    }

                    startY = ev.rawY
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    // Biarkan UI masuk ke RECORDING saat izin granted (di launcher)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = startY - ev.rawY
                    if (dy > cancelThreshold) {
                        switchInputMode(InputMode.CANCEL_HINT)
                    } else {
                        if (inputMode != InputMode.RECORDING) switchInputMode(InputMode.RECORDING)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dy = startY - ev.rawY
                    val cancelled = dy > cancelThreshold
                    stopListening(cancelled)
                    switchInputMode(InputMode.MIC_IDLE)

                    if (wasVoiceServiceRunning) {
                        resumeVoiceService() // <-- Fungsi yang akan kita buat
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun isVoiceServiceActive(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any {
            it.service.className == VoiceActivationService::class.java.name
        }
    }

    private fun pauseVoiceService() {
        val intent = Intent(this, VoiceActivationService::class.java).apply {
            action = VoiceActivationService.ACTION_STOP
        }
        startService(intent)
        Log.d(TAG, "Sent ACTION_STOP to Voice Service.")
    }

    private fun resumeVoiceService() {
        val intent = Intent(this, VoiceActivationService::class.java).apply {
            action = VoiceActivationService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        Log.d(TAG, "Sent ACTION_START to Voice Service to resume.")
    }

    // ---------------------------
    // STT: SpeechRecognizer
    // ---------------------------
    private fun setupSTT() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition tidak tersedia", Toast.LENGTH_LONG).show()
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "STT: Ready for speech")
            }
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "STT: Beginning of speech")
            }
            override fun onRmsChanged(rmsdB: Float) {
                // rentang tipikal 0..10
                val clamped = rmsdB.coerceIn(0f, 10f)
                val amp = (clamped / 10f) // 0..1

                // Pastikan di UI thread
                binding.waveView.post {
                    try {
                        val spectrum = List(128) { i -> amp * (i / 128f) }
                        binding.waveView.setVoiceAmplitudes(spectrum)
                    } catch (_: Exception) {}
                }
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d(TAG, "STT: End of speech")
            }
            override fun onError(error: Int) {
                Log.e(TAG, "STT Error: $error")
                isListening = false
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = texts?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    // ✅ Sembunyikan chips ketika voice input berhasil
                    hideSuggestionChips()

                    viewModel.sendMessage(text)
                } else {
                    Toast.makeText(this@ChatbotActivity, "Tidak ada hasil suara", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    private fun startListening() {
        if (isListening) return
        try {
            speechRecognizer?.startListening(speechIntent)
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            isListening = false
        }
    }

    private fun stopListening(cancel: Boolean) {
        try {
            if (cancel) speechRecognizer?.cancel()
            else speechRecognizer?.stopListening()
        } catch (_: Exception) { }
        isListening = false
    }

    private fun stopWaveAnimation() {
        waveJob?.cancel()
        waveJob = null
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing toast", e)
        }
    }

    private fun showError(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error shown to user: $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing error message", e)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        if (!AuthManager.ensureUserLoggedIn(this)) return

        updateChipsVisibility()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")

        try {
            Said.getInstance().unregisterActivityCallback(this.localClassName)
            stopWaveAnimation()
            speechRecognizer?.destroy()
            binding.rvMessages.removeCallbacks(null)
            if (isFinishing){
                clearMessagesAndShowChips()
            }
            Log.d(TAG, "✅ ChatbotActivity cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in cleanup", e)
        }
    }
}
