package com.pkm.said

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import com.pkm.said.adapter.MessageAdapter
import com.pkm.said.databinding.ActivityChatbotBinding
import com.pkm.said.util.InputMode
import kotlinx.coroutines.Job
import java.util.Locale
import kotlin.math.max

class ChatbotActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatbotActivity"

        fun start(context: Context) {
            val intent = Intent(context, ChatbotActivity::class.java)
            context.startActivity(intent)
        }
    }

    // View Binding
    private lateinit var binding: ActivityChatbotBinding

    // State & UI
    private var inputMode: InputMode = InputMode.KEYBOARD
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<String>()

    // STT
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var isListening = false

    // TTS (opsional; belum dipakai)
    private var tts: TextToSpeech? = null

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
            addInitialMessage()
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

    // ---------------------------
    // RecyclerView (chat)
    // ---------------------------
    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messageList)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatbotActivity).apply { stackFromEnd = true }
            adapter = messageAdapter
            setHasFixedSize(true)
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

    // ✅ Chip listeners
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
                    sendMessage(text, isUser = true)
                    simulateBotResponse(text)
                }
            }

            Log.d(TAG, "✅ Chip listeners setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up chip listeners", e)
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

    // ✅ Send message
    private fun handleSendMessage() {
        try {
            val message = binding.etMessage.text.toString().trim()

            if (message.isNotEmpty()) {
                Log.d(TAG, "Sending message: $message")
                sendMessage(message, isUser = true)
                binding.etMessage.text?.clear()
                simulateBotResponse(message)
            } else {
                showToast("Pesan tidak boleh kosong!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling send message", e)
            showError("Gagal mengirim pesan")
        }
    }

    private fun sendMessage(text: String, isUser: Boolean) {
        try {
            val prefix = if (isUser) "Anda: " else "Bot: "
            val message = prefix + text

            messageList.add(message)
            messageAdapter.notifyItemInserted(messageList.size - 1)

            binding.rvMessages.post {
                binding.rvMessages.smoothScrollToPosition(messageList.size - 1)
            }

            Log.d(TAG, "✅ Message added: ${if (isUser) "User" else "Bot"} - $text")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding message", e)
            showError("Gagal menambah pesan")
        }
    }

    private fun addInitialMessage() {
        try {
            sendMessage("Hello! Saya Said bot. Ada yang bisa saya bantu tentang stroke?", isUser = false)
            Log.d(TAG, "✅ Initial message added")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding initial message", e)
        }
    }

    private fun simulateBotResponse(userMessage: String) {
        binding.rvMessages.postDelayed({
            try {
                val botResponse = generateBotResponse(userMessage)
                sendMessage(botResponse, isUser = false)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in bot response", e)
                sendMessage("Maaf, terjadi kesalahan. Coba lagi nanti.", isUser = false)
            }
        }, 1000)
    }

    private fun generateBotResponse(userMessage: String): String {
        return when {
            userMessage.contains("stroke", ignoreCase = true) ||
                    userMessage.contains("apa itu stroke", ignoreCase = true) ->
                "Stroke adalah kondisi medis serius yang terjadi ketika aliran darah ke otak terganggu. Ini dapat menyebabkan kerusakan sel otak yang permanen."

            userMessage.contains("penyebab", ignoreCase = true) ->
                "Penyebab stroke dapat berupa:\n• Penyumbatan pembuluh darah (stroke iskemik)\n• Perdarahan di otak (stroke hemoragik)\n• Tekanan darah tinggi\n• Diabetes\n• Merokok"

            userMessage.contains("deteksi", ignoreCase = true) ||
                    userMessage.contains("metode", ignoreCase = true) ->
                "Metode deteksi stroke meliputi:\n• CT Scan atau MRI\n• Tes FAST (Face, Arms, Speech, Time)\n• Pemeriksaan neurologis\n• Tes darah"

            userMessage.contains("gejala", ignoreCase = true) ->
                "Gejala stroke utama:\n• Kesulitan berbicara atau memahami\n• Kelumpuhan atau mati rasa pada wajah, lengan, atau kaki\n• Masalah penglihatan\n• Sakit kepala parah mendadak\n• Kehilangan keseimbangan"

            userMessage.contains("pengobatan", ignoreCase = true) ->
                "Pengobatan stroke meliputi:\n• Obat pengencer darah\n• Terapi fisik\n• Terapi wicara\n• Operasi jika diperlukan\n• Rehabilitasi medis"

            userMessage.contains("pencegahan", ignoreCase = true) ->
                "Pencegahan stroke:\n• Kontrol tekanan darah\n• Berhenti merokok\n• Olahraga teratur\n• Diet sehat\n• Kelola diabetes dan kolesterol"

            else ->
                "Terima kasih atas pertanyaan Anda. Saya siap membantu menjawab pertanyaan seputar stroke. Silakan pilih topik yang ingin Anda ketahui!"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPressToTalkGesture() {
        var startY = 0f
        val cancelThreshold = 80f * resources.displayMetrics.density

        binding.pressToTalkIdle.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
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
                    true
                }
                else -> false
            }
        }
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
                    sendMessage(text, isUser = true)
                    val reply = generateBotResponse(text)
                    sendMessage(reply, isUser = false)
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")

        try {
            stopWaveAnimation()
            speechRecognizer?.destroy()
            binding.rvMessages.removeCallbacks(null)
            Log.d(TAG, "✅ ChatbotActivity cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in cleanup", e)
        }
    }
}
