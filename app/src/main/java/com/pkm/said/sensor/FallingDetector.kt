package com.pkm.said.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pkm.said.ml.FallDetectionModel
import com.pkm.said.util.FeatureExtractor
import kotlin.math.sqrt

class FallingDetector {
    private val window: MutableList<Pair<FloatArray, FloatArray>> = mutableListOf()
    private val model = FallDetectionModel()
    private var fallDetected: Boolean = false

    private val samplingRate = 25
    private val windowSizeSeconds = 60
    private val maxWindowSize = samplingRate * windowSizeSeconds

    companion object {
        const val ML_THRESHOLD = 0.7f
        const val ENSEMBLE_THRESHOLD = 0.8f
    }

    fun updateAccel(accel: FloatArray) {
        val gyro = if (window.isNotEmpty()) window.last().second else FloatArray(3)
        window.add(Pair(accel.clone(), gyro.clone()))
        maintainWindowSize()
    }

    fun updateGyro(gyro: FloatArray) {
        if (window.isNotEmpty()) {
            val last = window.removeAt(window.lastIndex)
            window.add(Pair(last.first, gyro.clone()))
        } else {
            val accel = FloatArray(3)
            window.add(Pair(accel, gyro.clone()))
        }
        maintainWindowSize()
    }

    private fun maintainWindowSize() {
        if (window.size > maxWindowSize) {
            window.removeAt(0)
        }
    }

    fun detectFall(): Boolean {
        return detectFallQuick()
    }

    fun detectFallML(): Boolean {
        if (window.size < 50) return false
        val features = FeatureExtractor.extractCareFallFeatures(window)
        val probability = model.predictWithML(features)
        fallDetected = probability > ML_THRESHOLD
        return fallDetected
    }

    fun detectFallThreshold(): Boolean {
        if (window.isEmpty()) return false
        return model.predictWithThreshold(window)
    }

    fun detectFallEnsemble(): Boolean {
        if (window.size < 50) return false
        val features = FeatureExtractor.extractCareFallFeatures(window)
        val probability = model.predictEnsemble(features, window)
        fallDetected = probability > ENSEMBLE_THRESHOLD
        return fallDetected
    }

    fun detectFallQuick(): Boolean {
        if (window.isEmpty()) return false
        val latest = window.last()
        val accelMagnitude = sqrt(
            latest.first[0] * latest.first[0] +
                    latest.first[1] * latest.first[1] +
                    latest.first[2] * latest.first[2]
        )
        if (accelMagnitude > 3.0f * 9.81f) {
            return detectFallEnsemble()
        }
        return false
    }

    fun getLastPredictionProbability(): Float {
        if (window.size < 50) return 0f
        val features = FeatureExtractor.extractCareFallFeatures(window)
        return model.predictWithML(features)
    }

    fun reset() {
        window.clear()
        fallDetected = false
    }
}
