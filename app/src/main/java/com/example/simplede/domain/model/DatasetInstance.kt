package com.example.simplede.domain.model

data class DatasetInstance(
    val id: String,
    val datasetId: String,
    val period: Period,
    val organisationUnit: OrganisationUnit,
    val attributeOptionCombo: String,
    val state: DatasetInstanceState
)

enum class DatasetInstanceState {
    OPEN,
    COMPLETE,
    APPROVED,
    LOCKED
}

data class Period(
    val id: String
)

data class OrganisationUnit(
    val id: String,
    val name: String
)
