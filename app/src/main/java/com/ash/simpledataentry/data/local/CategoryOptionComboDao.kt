package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CategoryOptionComboDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categoryOptionCombos: List<CategoryOptionComboEntity>)

    @Query("SELECT * FROM category_option_combos")
    suspend fun getAll(): List<CategoryOptionComboEntity>

    @Query("DELETE FROM category_option_combos")
    suspend fun clearAll()
} 