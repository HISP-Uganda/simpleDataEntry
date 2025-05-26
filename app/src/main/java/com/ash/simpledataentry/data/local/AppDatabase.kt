package com.ash.simpledataentry.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DataValueDraftEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dataValueDraftDao(): DataValueDraftDao
} 