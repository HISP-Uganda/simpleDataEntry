package com.ash.simpledataentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_value_drafts")
data class DataValueDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val datasetId: String,
    val period: String,
    val orgUnit: String,
    val attributeOptionCombo: String,
    val dataElement: String,
    val categoryOptionCombo: String,
    val value: String?,
    val comment: String?,
    val lastModified: Long = System.currentTimeMillis()
) 