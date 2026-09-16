package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.CuratedTourismData
import com.example.data.model.ItineraryItem
import com.example.data.model.TouristPlace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TourismRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val placeDao = database.touristPlaceDao()
    private val itineraryDao = database.itineraryDao()

    suspend fun checkAndSeedDatabase() = withContext(ioDispatcher) {
        if (placeDao.getCount() == 0) {
            placeDao.insertPlaces(CuratedTourismData.places)
        }
        if (itineraryDao.getCount() == 0) {
            CuratedTourismData.sampleItineraries.forEach {
                itineraryDao.insertItinerary(it)
            }
        }
    }

    fun getAllPlaces(): Flow<List<TouristPlace>> = placeDao.getAllPlaces()

    fun getFavoritePlaces(): Flow<List<TouristPlace>> = placeDao.getFavoritePlaces()

    fun getPlacesByCategory(category: String): Flow<List<TouristPlace>> {
        return if (category == "All") {
            placeDao.getAllPlaces()
        } else {
            placeDao.getPlacesByCategory(category)
        }
    }

    fun searchPlaces(query: String): Flow<List<TouristPlace>> {
        return if (query.isBlank()) {
            placeDao.getAllPlaces()
        } else {
            placeDao.searchPlaces(query.trim())
        }
    }

    suspend fun getPlaceById(id: String): TouristPlace? = withContext(ioDispatcher) {
        placeDao.getPlaceById(id)
    }

    suspend fun toggleFavorite(placeId: String, isFavorite: Boolean) = withContext(ioDispatcher) {
        placeDao.updateFavorite(placeId, isFavorite)
    }

    fun getAllItineraries(): Flow<List<ItineraryItem>> = itineraryDao.getAllItineraries()

    suspend fun addItinerary(itinerary: ItineraryItem): Long = withContext(ioDispatcher) {
        itineraryDao.insertItinerary(itinerary)
    }

    suspend fun deleteItinerary(itinerary: ItineraryItem) = withContext(ioDispatcher) {
        itineraryDao.deleteItinerary(itinerary)
    }
}
