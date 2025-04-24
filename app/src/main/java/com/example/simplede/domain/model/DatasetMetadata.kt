package com.example.simplede.domain.model

import java.util.Date

data class DatasetMetadata(
    val datasetId: String,
    val name: String,
    val description: String?,
    val periodType: String,
    val canCreateNew: Boolean,
    val lastSync: Date?
)