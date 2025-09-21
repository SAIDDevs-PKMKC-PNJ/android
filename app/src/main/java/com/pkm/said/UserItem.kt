package com.pkm.said

data class UserItem(
    val uid: String = "",
    val name: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val loginMethod: String? = null,
    val emailVerified: Boolean = false,
    val age: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val emergency: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
