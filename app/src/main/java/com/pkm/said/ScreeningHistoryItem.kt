package com.pkm.said

data class ScreeningHistoryItem(
    val id: String,
    val timestamp: String,
    val riskStatus: String,
    val scorePercent: Int,
    val riskLevel: String,
    val formattedDate: String
)
