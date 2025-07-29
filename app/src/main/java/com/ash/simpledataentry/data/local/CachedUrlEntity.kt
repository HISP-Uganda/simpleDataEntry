package com.ash.simpledataentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_urls")
data class CachedUrlEntity(
    @PrimaryKey
    val url: String,
    val lastUsed: Long, // Timestamp in milliseconds
    val frequency: Int = 1, // Number of times this URL has been used
    val isValid: Boolean = true // Whether the URL format is valid
)
