package com.pkm.said.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import com.pkm.said.BuildConfig
import com.pkm.said.adapter.MessageAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val apiKey = BuildConfig.GEMINI_API_KEY

    private var systemInstructionString = """
        Anda adalah Said, asisten chatbot kesehatan yang informatif dan ramah. Tugas Anda adalah memberikan informasi kesehatan umum, tips gaya hidup sehat, dan analisis gejala dasar. 
        PERINGATAN UTAMA: Anda BUKAN dokter. Selalu sertakan penafian di akhir setiap respons yang berkaitan dengan gejala atau diagnosis, yang mengingatkan pengguna untuk berkonsultasi dengan profesional medis. 
        Gunakan nada yang optimis dan mendukung.
    """.trimIndent()

    private val systemInstructionContent: Content = content(role = "system") {
        text(systemInstructionString)
    }

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash", // Model cepat dan cocok untuk chat
        apiKey = apiKey,
        systemInstruction = systemInstructionContent
    )

    private val _messages = MutableStateFlow<List<MessageAdapter.ChatMessage>>(emptyList())
    val messages: StateFlow<List<MessageAdapter.ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    // ======================================================================

    /**
     * Mengirim pesan dari pengguna ke model Gemini
     */
    fun sendMessage(prompt: String, isReload: Boolean = false) {
        if (prompt.isBlank() || _isLoading.value) return

        _isLoading.value = true

        // 1. Tambahkan pesan pengguna ke daftar HANYA JIKA BUKAN RELOAD
        if (!isReload) {
            val userMessage = MessageAdapter.ChatMessage(prompt, isUser = true, isLoading = false)
            _messages.value = _messages.value + userMessage
        }

        // 2. Tambahkan placeholder Said dengan status LOADING
        // Gunakan isLoading = true untuk memicu VIEW_TYPE_BOT_LOADING
        val botPlaceholder = MessageAdapter.ChatMessage("", isUser = false, isLoading = true)
        val placeholderIndex = _messages.value.size

        _messages.value = _messages.value + botPlaceholder

        viewModelScope.launch {
            try {
                val currentHistory = _messages.value
                    .filter { it.isUser || !it.isLoading }
                    .map {
                        if (it.isUser) content(role = "user") { text(it.text) }
                        else content(role = "model") { text(it.text) }
                    }
                    .toMutableList()

                val tempChat = generativeModel.startChat(history = currentHistory)

                val responseStream = tempChat.sendMessageStream(prompt)

                var fullResponseText = ""

                responseStream.collect { chunk ->
                    fullResponseText += chunk.text ?: ""

                    // 5. Update streaming: Text berubah, tapi isLoading tetap TRUE
                    _messages.value = _messages.value.toMutableList().apply {
                        this[placeholderIndex] = botPlaceholder.copy(
                            text = fullResponseText,
                            isLoading = true
                        )
                    }
                }

                // ⭐ 6. UPDATE FINAL: Set isLoading menjadi FALSE
                _messages.value = _messages.value.toMutableList().apply {
                    this[placeholderIndex] = botPlaceholder.copy(
                        text = fullResponseText,
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                // Pastikan isLoading = false saat error
                _messages.value = _messages.value.toMutableList().apply {
                    this[placeholderIndex] = MessageAdapter.ChatMessage(
                        text = "Error: Terjadi kesalahan. ${e.localizedMessage}.",
                        isUser = false,
                        isLoading = false
                    )
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendInitialPrompt(prompt: String) {
        if (_isLoading.value) return

        _isLoading.value = true

        val botPlaceholder = MessageAdapter.ChatMessage("", isUser = false, isLoading = true)
        val currentSize = _messages.value.size
        _messages.value = _messages.value + botPlaceholder
        val placeholderIndex = currentSize

        viewModelScope.launch {
            try {


                val currentHistory = _messages.value
                    .filter { it.isUser || !it.isLoading }
                    .map {
                        if (it.isUser) content(role = "user") { text(it.text) }
                        else content(role = "model") { text(it.text) }
                    }
                    .toMutableList()

                val tempChat = generativeModel.startChat(history = currentHistory)
                val responseStream = tempChat.sendMessageStream(prompt)
                var fullResponseText = ""

                responseStream.collect { chunk ->
                    fullResponseText += chunk.text ?: ""

                    // 5. Update streaming: Text berubah, tapi isLoading tetap TRUE
                    _messages.value = _messages.value.toMutableList().apply {
                        this[placeholderIndex] = botPlaceholder.copy(
                            text = fullResponseText,
                            isLoading = true
                        )
                    }
                }

                _messages.value = _messages.value.toMutableList().apply {
                    this[placeholderIndex] = botPlaceholder.copy(
                        text = fullResponseText,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _messages.value = _messages.value.toMutableList().apply {
                    this[placeholderIndex] = MessageAdapter.ChatMessage(
                        text = "Error: Terjadi kesalahan. ${e.localizedMessage}.",
                        isUser = false,
                        isLoading = false
                    )
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reloadMessage(promptToReload: String, botMessageIndex: Int) {
        if (botMessageIndex > 0 && _messages.value.size > botMessageIndex) {
            val mutableList = _messages.value.toMutableList()
            mutableList.removeAt(botMessageIndex) // Hapus pesan bot lama
            _messages.value = mutableList.toList()
        }

        sendMessage(promptToReload, isReload = true)
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            _messages.value = emptyList()
        }
    }
}