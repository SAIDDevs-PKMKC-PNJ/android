package com.pkm.said.util

import kotlin.math.*

class FeatureExtractor {
    companion object {
        // Extract 88 features as described in CareFall paper
        fun extractCareFallFeatures(window: List<Pair<FloatArray, FloatArray>>): FloatArray {
            if (window.isEmpty()) return FloatArray(88)

            val features = mutableListOf<Float>()

            // Extract accelerometer data
            val accX = window.map { it.first[0] }
            val accY = window.map { it.first[1] }
            val accZ = window.map { it.first[2] }

            // Extract gyroscope data
            val gyroX = window.map { it.second[0] }
            val gyroY = window.map { it.second[1] }
            val gyroZ = window.map { it.second[2] }

            // Calculate Signal Magnitude Vector (SMV) for accelerometer and gyroscope
            val accSMV = window.map {
                sqrt(it.first[0].pow(2) + it.first[1].pow(2) + it.first[2].pow(2))
            }
            val gyroSMV = window.map {
                sqrt(it.second[0].pow(2) + it.second[1].pow(2) + it.second[2].pow(2))
            }

            // Extract 11 statistical features for each signal (4 accelerometer signals)
            // AccX, AccY, AccZ, AccSMV - total 44 features
            features.addAll(extractStatisticalFeatures(accX))
            features.addAll(extractStatisticalFeatures(accY))
            features.addAll(extractStatisticalFeatures(accZ))
            features.addAll(extractStatisticalFeatures(accSMV))

            // Extract 11 statistical features for each signal (4 gyroscope signals)
            // GyroX, GyroY, GyroZ, GyroSMV - total 44 features
            features.addAll(extractStatisticalFeatures(gyroX))
            features.addAll(extractStatisticalFeatures(gyroY))
            features.addAll(extractStatisticalFeatures(gyroZ))
            features.addAll(extractStatisticalFeatures(gyroSMV))

            return features.toFloatArray()
        }

        private fun extractStatisticalFeatures(signal: List<Float>): List<Float> {
            if (signal.isEmpty()) return List(11) { 0f }

            val sortedSignal = signal.sorted()
            val mean = signal.average().toFloat()
            val variance = signal.map { (it - mean).pow(2) }.average().toFloat()
            val stdDev = sqrt(variance)

            return listOf(
                mean,                                    // Mean
                variance,                               // Variance
                sortedSignal[sortedSignal.size / 2],   // Median
                signal.maxOrNull()!! - signal.minOrNull()!!, // Delta (Range)
                stdDev,                                 // Standard Deviation
                signal.maxOrNull()!!,                   // Maximum Value
                signal.minOrNull()!!,                   // Minimum Value
                percentile(sortedSignal, 0.25f),       // 25th Percentile
                percentile(sortedSignal, 0.75f),       // 75th Percentile
                calculatePSD(signal),                   // Power Spectral Density
                calculatePSE(signal)                    // Power Spectral Entropy
            )
        }

        private fun percentile(sortedList: List<Float>, percentile: Float): Float {
            if (sortedList.isEmpty()) return 0f
            val index = ((sortedList.size - 1) * percentile).toInt()
            return sortedList[index.coerceIn(0, sortedList.size - 1)]
        }

        private fun calculatePSD(signal: List<Float>): Float {
            // Simplified PSD calculation - sum of squared values
            return signal.map { it * it }.average().toFloat()
        }

        private fun calculatePSE(signal: List<Float>): Float {
            // Simplified Power Spectral Entropy calculation
            val psd = signal.map { abs(it) + 1e-10f } // Add small value to avoid log(0)
            val totalPower = psd.sum()
            val normalizedPsd = psd.map { it / totalPower }
            return -normalizedPsd.map { it * ln(it) }.sum()
        }

        // Additional features for threshold-based approach
        fun calculateFallIndex(accData: FloatArray): Float {
            val magnitude = sqrt(accData[0].pow(2) + accData[1].pow(2) + accData[2].pow(2))
            return magnitude
        }

        fun calculateAVD(accData: FloatArray): Float {
            // Absolute Vertical Direction - simplified as Z-axis component
            return abs(accData[2])
        }
    }
}