package com.pkm.said

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkm.said.adapter.MessageAdapter
import com.pkm.said.databinding.ActivityChatbotBinding

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

    // RecyclerView components
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<String>()

    // ✅ ENHANCED KEYBOARD HANDLING VARIABLES
    private var keyboardLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var isKeyboardVisible = false
    private var originalBottomMargin = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "=== CHATBOT ACTIVITY DEBUG ===")
        Log.d(TAG, "Current Date: 2025-09-09 06:36:59")
        Log.d(TAG, "Current User: itsLuxra")
        Log.d(TAG, "onCreate called")

        try {
            // ✅ KEYBOARD RESPONSIVE SETUP
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

    // ✅ ENHANCED WINDOW SETUP FOR KEYBOARD RESPONSIVENESS
    private fun setupWindowForKeyboard() {
        try {
            Log.d(TAG, "Setting up window for keyboard responsiveness...")

            // Method 1: Traditional approach
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            // Method 2: Modern approach with WindowInsets (API 30+)
            WindowCompat.setDecorFitsSystemWindows(window, false)

            Log.d(TAG, "✅ Window setup for keyboard completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up window for keyboard", e)
        }
    }

    private fun setupViews() {
        try {
            setupRecyclerView()
            setupClickListeners()
            addInitialMessage()
            setupKeyboardListener() // ✅ Enhanced keyboard listener
            Log.d(TAG, "✅ ChatbotActivity setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in setupViews", e)
            showError("Terjadi kesalahan saat memuat chatbot")
        }
    }

    // ✅ ENHANCED KEYBOARD EVENT HANDLING WITH WINDOW INSETS
    private fun setupKeyboardListener() {
        Log.d(TAG, "Setting up enhanced keyboard listener...")

        try {
            // Store original margin
            val params = binding.inputContainer.layoutParams as RelativeLayout.LayoutParams
            originalBottomMargin = params.bottomMargin

            // Method 1: WindowInsets Listener (Modern - API 30+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                binding.root.setOnApplyWindowInsetsListener { _, insets ->
                    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

                    Log.d(TAG, "WindowInsets - IME height: ${imeInsets.bottom}, System bars: ${systemBarsInsets.bottom}")

                    if (imeInsets.bottom > 0) {
                        // Keyboard visible
                        if (!isKeyboardVisible) {
                            Log.d(TAG, "🎹 Keyboard opened (WindowInsets)")
                            val newBottomMargin = imeInsets.bottom + dpToPx(16)
                            animateInputContainer(newBottomMargin)
                            isKeyboardVisible = true
                        }
                    } else {
                        // Keyboard hidden
                        if (isKeyboardVisible) {
                            Log.d(TAG, "🎹 Keyboard closed (WindowInsets)")
                            animateInputContainer(originalBottomMargin)
                            isKeyboardVisible = false
                        }
                    }

                    insets
                }
            }

            // Method 2: Fallback GlobalLayoutListener (All APIs)
            keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                val rect = Rect()
                binding.root.getWindowVisibleDisplayFrame(rect)

                val screenHeight = binding.root.rootView.height
                val keypadHeight = screenHeight - rect.bottom

                Log.d(TAG, "GlobalLayout - Screen: $screenHeight, Keypad: $keypadHeight")

                if (keypadHeight > screenHeight * 0.15) {
                    // Keyboard visible
                    if (!isKeyboardVisible) {
                        Log.d(TAG, "🎹 Keyboard opened (GlobalLayout)")
                        val newBottomMargin = keypadHeight + dpToPx(16)
                        animateInputContainer(newBottomMargin)
                        isKeyboardVisible = true
                    }
                } else {
                    // Keyboard hidden
                    if (isKeyboardVisible) {
                        Log.d(TAG, "🎹 Keyboard closed (GlobalLayout)")
                        animateInputContainer(originalBottomMargin)
                        isKeyboardVisible = false
                    }
                }
            }

            binding.root.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
            Log.d(TAG, "✅ Enhanced keyboard listener setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up keyboard listener", e)
        }
    }

    // ✅ ENHANCED SMOOTH ANIMATION FOR INPUT CONTAINER
    private fun animateInputContainer(newBottomMargin: Int) {
        try {
            val params = binding.inputContainer.layoutParams as RelativeLayout.LayoutParams
            val currentMargin = params.bottomMargin

            Log.d(TAG, "Animating input container from $currentMargin to $newBottomMargin")

            // Enhanced smooth animation with easing
            val animator = ValueAnimator.ofInt(currentMargin, newBottomMargin)
            animator.duration = 300 // Slightly longer for smoother feel
            animator.addUpdateListener { animation ->
                params.bottomMargin = animation.animatedValue as Int
                binding.inputContainer.layoutParams = params
            }

            // Add easing for more natural feel
            animator.interpolator = android.view.animation.DecelerateInterpolator()
            animator.start()

            // Scroll RecyclerView to bottom when keyboard opens
            if (newBottomMargin > originalBottomMargin && messageList.isNotEmpty()) {
                binding.rvMessages.postDelayed({
                    binding.rvMessages.smoothScrollToPosition(messageList.size - 1)
                }, 100)
            }

            Log.d(TAG, "✅ Enhanced input container animation started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error animating input container", e)
        }
    }

    // ✅ UTILITY: Convert dp to pixels
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // ✅ VIEW BINDING - Cleaner RecyclerView setup
    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView...")

        try {
            messageAdapter = MessageAdapter(messageList)

            binding.rvMessages.apply {
                layoutManager = LinearLayoutManager(this@ChatbotActivity).apply {
                    stackFromEnd = true // Messages start from bottom
                }
                adapter = messageAdapter
                setHasFixedSize(true)
                // ✅ Add item decoration for better spacing
                addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(
                    this@ChatbotActivity,
                    androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
                ))
            }

            Log.d(TAG, "✅ RecyclerView setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up RecyclerView", e)
            throw e
        }
    }

    // ✅ ENHANCED CLICK LISTENERS
    private fun setupClickListeners() {
        Log.d(TAG, "Setting up click listeners...")

        try {
            binding.btnBack.setOnClickListener {
                Log.d(TAG, "Back button clicked")
                handleBackNavigation()
            }

            binding.btnSend.setOnClickListener {
                Log.d(TAG, "Send button clicked")
                handleSendMessage()
            }

            binding.btnVoice.setOnClickListener {
                Log.d(TAG, "Voice button clicked")
                showToast("Fitur input suara belum diimplementasikan!")
            }

            // ✅ ENHANCED: Enter key handling for input
            binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    handleSendMessage()
                    true
                } else {
                    false
                }
            }

            // Chip listeners
            setupChipListeners()

            Log.d(TAG, "✅ Click listeners setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up click listeners", e)
            throw e
        }
    }

    // ✅ VIEW BINDING - Setup chip listeners
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

    // ✅ ENHANCED BACK NAVIGATION HANDLING
    private fun handleBackNavigation() {
        try {
            Log.d(TAG, "🔄 Handling back navigation...")
            finish() // Simply finish the activity
            Log.d(TAG, "✅ Back navigation completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in back navigation", e)
        }
    }

    // ✅ ENHANCED MESSAGE HANDLING WITH KEYBOARD MANAGEMENT
    private fun handleSendMessage() {
        try {
            val message = binding.etMessage.text.toString().trim()

            if (message.isNotEmpty()) {
                Log.d(TAG, "Sending message: $message")
                sendMessage(message, isUser = true)
                binding.etMessage.text?.clear()

                // ✅ ENHANCED: Hide keyboard after sending (optional)
                // hideKeyboard()

                simulateBotResponse(message)
            } else {
                showToast("Pesan tidak boleh kosong!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling send message", e)
            showError("Gagal mengirim pesan")
        }
    }

    // ✅ ENHANCED MESSAGE HANDLING
    private fun sendMessage(text: String, isUser: Boolean) {
        try {
            val prefix = if (isUser) "Anda: " else "Bot: "
            val message = prefix + text

            messageList.add(message)
            messageAdapter.notifyItemInserted(messageList.size - 1)

            // Enhanced smooth scroll
            binding.rvMessages.post {
                binding.rvMessages.smoothScrollToPosition(messageList.size - 1)
            }

            Log.d(TAG, "✅ Message added: ${if (isUser) "User" else "Bot"} - $text")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding message", e)
            showError("Gagal menambah pesan")
        }
    }

    // ✅ INITIAL MESSAGE
    private fun addInitialMessage() {
        try {
            sendMessage("Hello! Saya Said bot. Ada yang bisa saya bantu tentang stroke?", isUser = false)
            Log.d(TAG, "✅ Initial message added")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding initial message", e)
        }
    }

    // ✅ BOT RESPONSE with delay
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

    // ✅ ENHANCED BOT RESPONSE
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")

        try {
            // ✅ ENHANCED CLEANUP
            keyboardLayoutListener?.let { listener ->
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                Log.d(TAG, "✅ Keyboard listener removed")
            }

            binding.rvMessages.removeCallbacks(null)
            Log.d(TAG, "✅ ChatbotActivity cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in cleanup", e)
        }
    }
}