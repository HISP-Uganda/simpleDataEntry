package com.ash.simpledataentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_option_combos")
data class CategoryOptionComboEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryComboId: String,
    val optionUids: String // comma-separated
) 