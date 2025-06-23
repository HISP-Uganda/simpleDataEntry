package com.ash.simpledataentry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "organisation_units")
data class OrganisationUnitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?
) 