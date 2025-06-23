package com.ash.simpledataentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_elements")
data class DataElementEntity(
    @PrimaryKey val id: String,
    val name: String,
    val valueType: String,
    val categoryComboId: String?,
    val description: String?
) 