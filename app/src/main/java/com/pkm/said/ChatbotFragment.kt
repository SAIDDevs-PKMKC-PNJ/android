package com.pkm.said

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkm.said.databinding.FragmentChatbotBinding

class ChatbotFragment : Fragment() {

    companion object {
        private const val TAG = "ChatbotFragment"
    }

    // View Binding
    private var _binding: FragmentChatbotBinding? = null
    private val binding get() = _binding!!

    // RecyclerView components
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "=== CHATBOT FRAGMENT DEBUG ===")
        Log.d(TAG, "Current Date: 2025-07-30 18:28:32")
        Log.d(TAG, "Current User: itsLuxra")
        Log.d(TAG, "onCreateView called")

        return try {
            _binding = FragmentChatbotBinding.inflate(inflater, container, false)
            Log.d(TAG, "✅ View Binding setup completed")
            binding.root
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up View Binding", e)
            throw e
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        try {
            setupRecyclerView()
            setupClickListeners()
            addInitialMessage()
            Log.d(TAG, "✅ ChatbotFragment setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onViewCreated", e)
            showError("Terjadi kesalahan saat memuat chatbot")
        }
    }

    // ✅ VIEW BINDING - Cleaner RecyclerView setup
    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView...")

        try {
            messageAdapter = MessageAdapter(messageList)

            binding.rvMessages.apply {
                layoutManager = LinearLayoutManager(context).apply {
                    stackFromEnd = true // Messages start from bottom
                }
                adapter = messageAdapter
                setHasFixedSize(true)
            }

            Log.d(TAG, "✅ RecyclerView setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up RecyclerView", e)
            throw e
        }
    }

    // ✅ FIXED - Direct view access without include binding
    private fun setupClickListeners() {
        Log.d(TAG, "Setting up click listeners...")

        try {
            binding.btnBack.setOnClickListener {
                Log.d(TAG, "Back button clicked")
                handleBackNavigation()
            }

            // ✅ FIXED: Direct access to views (no floatingInputLayout)
            binding.btnSend.setOnClickListener {
                Log.d(TAG, "Send button clicked")
                handleSendMessage()
            }

            binding.btnVoice.setOnClickListener {
                Log.d(TAG, "Voice button clicked")
                showToast("Fitur input suara belum diimplementasikan!")
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

    // ✅ BACK NAVIGATION HANDLING
    private fun handleBackNavigation() {
        try {
            Log.d(TAG, "🔄 Handling back navigation...")

            // Method 1: Use NavController (Recommended)
            val navController = findNavController()
            val currentDestination = navController.currentDestination?.id

            Log.d(TAG, "Current destination: $currentDestination")

            when (currentDestination) {
                R.id.navigation_chatbot -> {
                    Log.d(TAG, "Navigating back to Home from ChatBot")
                    navController.navigate(R.id.navigation_home)
                }
                else -> {
                    Log.d(TAG, "Using navigateUp for other destinations")
                    if (!navController.navigateUp()) {
                        Log.d(TAG, "NavigateUp failed, using activity back press")
                        requireActivity().onBackPressed()
                    }
                }
            }

            Log.d(TAG, "✅ Back navigation completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in back navigation", e)
            // Fallback: use activity back press
            try {
                requireActivity().onBackPressed()
            } catch (fallbackError: Exception) {
                Log.e(TAG, "❌ Fallback back press also failed", fallbackError)
            }
        }
    }

    // ✅ Direct view access for input handling
    private fun handleSendMessage() {
        try {
            // ✅ FIXED: Direct access to etMessage (no floatingInputLayout)
            val message = binding.etMessage.text.toString().trim()

            if (message.isNotEmpty()) {
                Log.d(TAG, "Sending message: $message")
                sendMessage(message, isUser = true)
                binding.etMessage.text?.clear() // ✅ FIXED: Direct access
                simulateBotResponse(message)
            } else {
                showToast("Pesan tidak boleh kosong!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling send message", e)
            showError("Gagal mengirim pesan")
        }
    }

    // ✅ SAFE MESSAGE HANDLING
    private fun sendMessage(text: String, isUser: Boolean) {
        try {
            val prefix = if (isUser) "Anda: " else "Bot: "
            val message = prefix + text

            messageList.add(message)
            messageAdapter.notifyItemInserted(messageList.size - 1)

            // Smooth scroll using binding
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
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing toast", e)
        }
    }

    private fun showError(message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error shown to user: $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing error message", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called")

        try {
            _binding?.let {
                it.rvMessages.removeCallbacks(null)
            }
            _binding = null
            Log.d(TAG, "✅ ChatbotFragment cleaned up, binding nullified")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in cleanup", e)
        }
    }
}