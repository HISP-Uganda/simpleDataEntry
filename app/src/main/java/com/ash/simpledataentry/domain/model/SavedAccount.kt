package com.ash.simpledataentry.domain.model

data class SavedAccount(
    val id: String,
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val encryptedPassword: String, // Encrypted using Android Keystore
    val lastUsed: Long, // Timestamp in milliseconds
    val isActive: Boolean = false, // Whether this is the currently active account
    val createdAt: Long = System.currentTimeMillis()
)