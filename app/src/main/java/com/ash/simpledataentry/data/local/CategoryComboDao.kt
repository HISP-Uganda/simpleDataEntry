package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CategoryComboDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categoryCombos: List<CategoryComboEntity>)

    @Query("SELECT * FROM category_combos")
    suspend fun getAll(): List<CategoryComboEntity>

    @Query("DELETE FROM category_combos")
    suspend fun clearAll()
} 