package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TouristPlace
import kotlinx.coroutines.flow.Flow

@Dao
interface TouristPlaceDao {
    @Query("SELECT * FROM tourist_places ORDER BY rating DESC")
    fun getAllPlaces(): Flow<List<TouristPlace>>

    @Query("SELECT * FROM tourist_places WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoritePlaces(): Flow<List<TouristPlace>>

    @Query("SELECT * FROM tourist_places WHERE id = :id LIMIT 1")
    suspend fun getPlaceById(id: String): TouristPlace?

    @Query("SELECT * FROM tourist_places WHERE category = :category ORDER BY rating DESC")
    fun getPlacesByCategory(category: String): Flow<List<TouristPlace>>

    @Query("SELECT * FROM tourist_places WHERE state = :state ORDER BY rating DESC")
    fun getPlacesByState(state: String): Flow<List<TouristPlace>>

    @Query("""
        SELECT * FROM tourist_places 
        WHERE name LIKE '%' || :query || '%' 
           OR city LIKE '%' || :query || '%' 
           OR state LIKE '%' || :query || '%' 
           OR category LIKE '%' || :query || '%'
           OR highlights LIKE '%' || :query || '%'
        ORDER BY rating DESC
    """)
    fun searchPlaces(query: String): Flow<List<TouristPlace>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<TouristPlace>)

    @Query("UPDATE tourist_places SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM tourist_places")
    suspend fun getCount(): Int
}
