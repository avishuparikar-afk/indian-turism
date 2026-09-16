package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tourist_places")
data class TouristPlace(
    @PrimaryKey val id: String,
    val name: String,
    val city: String,
    val state: String,
    val region: String, // North, South, East, West, Central, North-East
    val category: String, // Historical, Forts, Spiritual, Nature, Beaches, UNESCO, Hill Station, Culture
    val rating: Float,
    val reviewCount: Int,
    val shortDescription: String,
    val historicalDescription: String,
    val culturalSignificance: String,
    val timings: String,
    val entryFeeIndian: String,
    val entryFeeForeigner: String,
    val bestSeason: String,
    val weatherHint: String,
    val temperatureC: Int,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String,
    val highlights: String, // Comma-separated or bullet list
    val nearestTransport: String,
    val isFavorite: Boolean = false
)
