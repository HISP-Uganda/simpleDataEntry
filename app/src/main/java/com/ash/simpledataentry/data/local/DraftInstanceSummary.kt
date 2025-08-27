package com.ash.simpledataentry.data.local

data class DraftInstanceSummary(
    val datasetId: String,
    val period: String,
    val orgUnit: String,
    val attributeOptionCombo: String,
    val lastModified: Long
)