package com.ash.simpledataentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "datasets")
data class DatasetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val periodType: String
) 