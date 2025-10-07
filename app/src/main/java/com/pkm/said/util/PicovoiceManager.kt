package com.pkm.said.util

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.rhino.RhinoInference
import ai.picovoice.rhino.RhinoManager
import ai.picovoice.rhino.RhinoManagerCallback
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pkm.said.BuildConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File

class PicovoiceManager(
    private val context: Context,
    private val onIntentDetected: (RhinoInference) -> Unit
) {
    private var porcupineManager: PorcupineManager? = null
    private var rhinoManager: RhinoManager? = null

    private var isInIntentMode = false
    private var currentState = VoiceState.IDLE
    private val TAG = "PicovoiceManager"

    enum class VoiceState {
        IDLE,
        WAKE_WORD_LISTENING,
        INTENT_PROCESSING
    }

    companion object {
        private const val ACCESS_KEY = BuildConfig.ACCESS_KEY

        private fun validateAccessKey(): Boolean {
            return when {
                ACCESS_KEY.isBlank() -> {
                    Log.e("PicovoiceManager", "Access key is blank")
                    false
                }
                false -> {
                    Log.e("PicovoiceManager", "Access key is wrong, please set actual access key in BuildConfig")
                    false
                }
                else -> true
            }
        }
    }

    fun initPicovoice(): Boolean {
        if (!validateAccessKey()) {
            return false
        }

        return try {
            val keywordPath = copyAssetToFiles("hi-said_en_android_v3_0_0.ppn")
            val contextPath = copyAssetToFiles("said-activation_en_android_v3_0_0.rhn")

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setKeywordPath(keywordPath)
                .setSensitivity(0.7f)
                .build(context, porcupineCallback)

            rhinoManager = RhinoManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setContextPath(contextPath)
                .setSensitivity(0.5f)
                .build(context, rhinoCallback)

            Log.i(TAG, "Picovoice initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Picovoice", e)
            false
        }
    }

    private val porcupineCallback = PorcupineManagerCallback { keywordIndex ->
        try {
            Log.d(TAG, "Wake word detected! Switching to intent mode")
            isInIntentMode = true
            setState(VoiceState.INTENT_PROCESSING)

            porcupineManager?.stop()
            showListeningUI(true)
            broadcastWakeWordDetected()
        } catch (e: Exception) {
            Log.e(TAG, "Error in wake word callback", e)
            switchBackToWakeWordMode()
        }
    }

    private val rhinoCallback = RhinoManagerCallback { inference ->
        try {
            Log.d(TAG, "Rhino callback invoked")

            if (inference.isUnderstood) {
                Log.d(TAG, "Intent understood: ${inference.intent}")
                onIntentDetected(inference)
                broadcastIntentResult(inference)
            } else {
                Log.d(TAG, "Intent not understood")
                broadcastNotUnderstood()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in intent callback", e)
        } finally {
            switchBackToWakeWordMode()
        }
    }

    private fun switchBackToWakeWordMode() {
        try {
            isInIntentMode = false
            setState(VoiceState.WAKE_WORD_LISTENING)
            porcupineManager?.start()
            showListeningUI(false)
            Log.d(TAG, "Switched back to wake word mode")
        } catch (e: Exception) {
            Log.e(TAG, "Error switching to wake word mode", e)
            setState(VoiceState.IDLE)
        }
    }

    private fun setState(newState: VoiceState) {
        currentState = newState
        Log.d(TAG, "State: $newState")
    }

    private fun showListeningUI(show: Boolean) {
        try {
            val intent = Intent("VOICE_LISTENING_STATE").apply {
                putExtra("is_listening", show)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing listening UI", e)
        }
    }

    private fun broadcastWakeWordDetected() {
        try {
            val intent = Intent("WAKE_WORD_DETECTED")
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting wake word", e)
        }
    }

    private fun broadcastIntentResult(inference: RhinoInference) {
        try {
            val intent = Intent("INTENT_DETECTED").apply {
                putExtra("intent", inference.intent)
                putExtra("is_understood", inference.isUnderstood)
                val slots = HashMap(inference.slots)
                putExtra("slots", slots)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting intent", e)
        }
    }

    private fun broadcastNotUnderstood() {
        try {
            val intent = Intent("INTENT_NOT_UNDERSTOOD")
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting not understood", e)
        }
    }

    private fun copyAssetToFiles(assetName: String): String {
        return try {
            val assetFile = context.assets.open(assetName)
            val internalFile = File(context.filesDir, assetName)

            BufferedInputStream(assetFile).use { input ->
                BufferedOutputStream(internalFile.outputStream()).use { output ->
                    input.copyTo(output)
                }
            }
            internalFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset: $assetName", e)
            throw e
        }
    }

    fun start() {
        try {
            porcupineManager?.start()
            isInIntentMode = false
            setState(VoiceState.WAKE_WORD_LISTENING)
            Log.d(TAG, "Wake word detection started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Picovoice", e)
            setState(VoiceState.IDLE)
        }
    }

    fun stop() {
        try {
            porcupineManager?.stop()
            isInIntentMode = false
            setState(VoiceState.IDLE)
            Log.d(TAG, "Voice activation stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Picovoice", e)
        }
    }

    fun release() {
        try {
            stop()
            porcupineManager?.delete()
            rhinoManager?.delete()
            Log.d(TAG, "All resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error during release", e)
        } finally {
            porcupineManager = null
            rhinoManager = null
            setState(VoiceState.IDLE)
        }
    }

    // Utility methods
    fun isListening(): Boolean = currentState != VoiceState.IDLE
    fun isInIntentMode(): Boolean = isInIntentMode
    fun getCurrentState(): VoiceState = currentState
}