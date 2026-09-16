package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ItineraryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ItineraryDao {
    @Query("SELECT * FROM itineraries ORDER BY createdAt DESC")
    fun getAllItineraries(): Flow<List<ItineraryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItinerary(itinerary: ItineraryItem): Long

    @Delete
    suspend fun deleteItinerary(itinerary: ItineraryItem)

    @Query("SELECT COUNT(*) FROM itineraries")
    suspend fun getCount(): Int
}
