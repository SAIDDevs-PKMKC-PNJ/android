package com.pkm.said.ml


import kotlin.math.*
import kotlin.random.Random

class FallDetectionModel {
    // Pre-trained weights (in real implementation, load from file or TensorFlow Lite)
    private val weights = FloatArray(88) { Random.nextFloat() * 0.1f - 0.05f }
    private val bias = -2.0f

    // Threshold values for different approaches
    companion object {
        const val SMV_THRESHOLD = 3.0f * 9.81f  // 3g threshold for SMV
        const val FI_THRESHOLD = 2.5f * 9.81f   // Fall Index threshold
        const val AVD_THRESHOLD = 0.5f * 9.81f  // Absolute Vertical Direction threshold
    }

    fun predictWithML(features: FloatArray): Float {
        if (features.size != 88) {
            throw IllegalArgumentException("Feature vector must have 88 elements")
        }

        // Logistic regression prediction
        var logit = bias
        for (i in features.indices) {
            logit += features[i] * weights[i]
        }

        // Sigmoid activation
        return 1.0f / (1.0f + exp(-logit))
    }

    fun predictWithThreshold(window: List<Pair<FloatArray, FloatArray>>): Boolean {
        if (window.isEmpty()) return false

        val votes = mutableListOf<Boolean>()

        // SMV-based detection
        val smvVotes = window.map { (acc, _) ->
            val smv = sqrt(acc[0].pow(2) + acc[1].pow(2) + acc[2].pow(2))
            smv > SMV_THRESHOLD
        }
        votes.add(smvVotes.any { it })

        // Fall Index detection
        val fiVotes = window.map { (acc, _) ->
            val fi = sqrt(acc[0].pow(2) + acc[1].pow(2) + acc[2].pow(2))
            fi > FI_THRESHOLD
        }
        votes.add(fiVotes.any { it })

        // AVD detection
        val avdVotes = window.map { (acc, _) ->
            abs(acc[2]) > AVD_THRESHOLD
        }
        votes.add(avdVotes.any { it })

        // Majority voting
        return votes.count { it } >= 2
    }

    // Ensemble prediction combining both approaches
    fun predictEnsemble(features: FloatArray, window: List<Pair<FloatArray, FloatArray>>): Float {
        val mlProbability = predictWithML(features)
        val thresholdResult = if (predictWithThreshold(window)) 1.0f else 0.0f

        // Weighted combination (70% ML, 30% threshold)
        return 0.7f * mlProbability + 0.3f * thresholdResult
    }
}