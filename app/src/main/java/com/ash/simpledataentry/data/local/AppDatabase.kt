package com.ash.simpledataentry.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ash.simpledataentry.data.local.DatasetEntity
import com.ash.simpledataentry.data.local.DataElementEntity
import com.ash.simpledataentry.data.local.CategoryComboEntity
import com.ash.simpledataentry.data.local.CategoryOptionComboEntity
import com.ash.simpledataentry.data.local.OrganisationUnitEntity
import com.ash.simpledataentry.data.local.DataValueEntity
import com.ash.simpledataentry.data.local.DataValueDao

@Database(
    entities = [
        DataValueDraftEntity::class,
        DataValueEntity::class,
        DatasetEntity::class,
        DataElementEntity::class,
        CategoryComboEntity::class,
        CategoryOptionComboEntity::class,
        OrganisationUnitEntity::class,
        CachedUrlEntity::class,
        SavedAccountEntity::class,
        TrackerProgramEntity::class,
        EventProgramEntity::class,
        TrackerEnrollmentEntity::class,
        EventInstanceEntity::class
    ],
    version = 9,  // Incremented for index additions on metadata tables
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dataValueDraftDao(): DataValueDraftDao
    abstract fun datasetDao(): DatasetDao
    abstract fun dataElementDao(): DataElementDao
    abstract fun categoryComboDao(): CategoryComboDao
    abstract fun categoryOptionComboDao(): CategoryOptionComboDao
    abstract fun organisationUnitDao(): OrganisationUnitDao
    abstract fun dataValueDao(): DataValueDao
    abstract fun cachedUrlDao(): CachedUrlDao
    abstract fun savedAccountDao(): SavedAccountDao
    abstract fun trackerProgramDao(): TrackerProgramDao
    abstract fun eventProgramDao(): EventProgramDao
    abstract fun trackerEnrollmentDao(): TrackerEnrollmentDao
    abstract fun eventInstanceDao(): EventInstanceDao
}
