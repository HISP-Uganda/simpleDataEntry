package com.ash.simpledataentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_combos")
data class CategoryComboEntity(
    @PrimaryKey val id: String,
    val name: String
) 