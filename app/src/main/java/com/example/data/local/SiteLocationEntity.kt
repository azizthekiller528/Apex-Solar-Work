package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.SiteLocation

@Entity(tableName = "site_locations")
data class SiteLocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val siteType: String,
    val addressOrVillage: String,
    val latitude: Double?,
    val longitude: Double?,
    val googleMapsUrl: String,
    val notes: String,
    val isPinned: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toModel(): SiteLocation = SiteLocation(
        id = id,
        name = name,
        siteType = siteType,
        addressOrVillage = addressOrVillage,
        latitude = latitude,
        longitude = longitude,
        googleMapsUrl = googleMapsUrl,
        notes = notes,
        isPinned = isPinned,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromModel(model: SiteLocation): SiteLocationEntity = SiteLocationEntity(
            id = model.id,
            name = model.name,
            siteType = model.siteType,
            addressOrVillage = model.addressOrVillage,
            latitude = model.latitude,
            longitude = model.longitude,
            googleMapsUrl = model.googleMapsUrl,
            notes = model.notes,
            isPinned = model.isPinned,
            createdAtMillis = model.createdAtMillis,
            updatedAtMillis = model.updatedAtMillis
        )
    }
}
