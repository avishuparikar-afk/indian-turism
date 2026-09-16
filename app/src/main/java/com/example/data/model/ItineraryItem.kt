package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "itineraries")
data class ItineraryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val destination: String,
    val durationDays: Int,
    val dayWisePlan: String, // Formatted text breakdown
    val estimatedBudgetInr: String,
    val bestTimeToVisit: String,
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val userEmail: String = "avishuparikar@gmail.com"
)
