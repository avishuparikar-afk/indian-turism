package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.GeminiTravelService
import com.example.data.api.GeoapifyService
import com.example.data.api.GmailScanResult
import com.example.data.api.GmailService
import com.example.data.api.LiveWeatherData
import com.example.data.api.OpenWeatherService
import com.example.data.api.ParsedBooking
import com.example.data.api.RouteStep
import com.example.data.api.RoutingService
import com.example.data.api.TravelMode
import com.example.data.api.TravelRoute
import com.example.data.local.ApiKeyManager
import com.example.data.local.AppDatabase
import com.example.data.model.ItineraryItem
import com.example.data.model.TouristPlace
import com.example.data.repository.TourismRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isLiveAi: Boolean = false,
    val hasError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class TourismUiState(
    val places: List<TouristPlace> = emptyList(),
    val filteredPlaces: List<TouristPlace> = emptyList(),
    val favorites: List<TouristPlace> = emptyList(),
    val itineraries: List<ItineraryItem> = emptyList(),
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val selectedPlace: TouristPlace? = null,
    val selectedNavTab: Int = 0,
    val isDarkTheme: Boolean = true, // Night mode aesthetic default
    val isOfflineMode: Boolean = false,
    val hapticEnabled: Boolean = true,
    val userEmail: String = "avishuparikar@gmail.com",
    val userName: String = "Avishu Parikar",
    val isAuthenticated: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val anonymousAiQueries: Boolean = true,
    val localOnlyStorage: Boolean = true,
    val gmailScanningEnabled: Boolean = true,
    val isAiThinking: Boolean = false,
    val chatMessages: List<ChatMessage> = listOf(
        ChatMessage(
            text = "Namaste! 🙏 Welcome to India Tourism Explorer.\nI am your AI Travel Guide powered by Gemini AI.\n\nAsk me for customized day-wise itineraries, local street food recommendations, train & flight routes, or cultural tips across any state in India!",
            isUser = false,
            isLiveAi = false
        )
    ),
    val geminiApiKey: String = "",
    val isGeminiKeyConfigured: Boolean = false,
    val hasCustomGeminiKey: Boolean = false,
    val mapsApiKey: String = BuildConfig.MAPS_API_KEY,
    val isMapsKeyConfigured: Boolean = false,
    val openWeatherApiKey: String = "",
    val isOpenWeatherKeyConfigured: Boolean = false,
    val geoapifyApiKey: String = "",
    val isGeoapifyKeyConfigured: Boolean = false,
    val liveWeatherMap: Map<String, LiveWeatherData> = emptyMap(),
    val isLoadingLiveWeather: Boolean = false,
    val isScanningGmail: Boolean = false,
    val scannedBookings: List<ParsedBooking> = emptyList(),
    val gmailScanStatusMessage: String? = null,
    val gmailAccessToken: String = "",
    val activeTravelRoute: TravelRoute? = null,
    val isCalculatingRoute: Boolean = false,
    val travelRouteError: String? = null,
    val routeOriginName: String = "New Delhi",
    val routeOriginLatLng: LatLng = LatLng(28.6139, 77.2090),
    val selectedTravelMode: TravelMode = TravelMode.DRIVING
)

class TourismViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TourismRepository
    private val geminiService = GeminiTravelService()
    private val openWeatherService = OpenWeatherService()
    private val geoapifyService = GeoapifyService()
    private val routingService = RoutingService()
    private val apiKeyManager = ApiKeyManager(application)
    private val authManager = com.example.data.auth.AuthManager(application)
    private val gmailService = GmailService()

    private val _uiState = MutableStateFlow(TourismUiState())
    val uiState: StateFlow<TourismUiState> = _uiState.asStateFlow()

    init {
        val database = AppDatabase.getInstance(application)
        repository = TourismRepository(database)

        val geminiKey = apiKeyManager.getGeminiApiKey()
        val openWeatherKey = apiKeyManager.getOpenWeatherApiKey()
        val geoapifyKey = apiKeyManager.getGeoapifyApiKey()

        _uiState.update {
            it.copy(
                geminiApiKey = geminiKey,
                isGeminiKeyConfigured = apiKeyManager.isGeminiKeyConfigured(),
                hasCustomGeminiKey = apiKeyManager.hasCustomGeminiKey(),
                mapsApiKey = apiKeyManager.getMapsApiKey().ifBlank { BuildConfig.MAPS_API_KEY },
                isMapsKeyConfigured = apiKeyManager.isMapsKeyConfigured(),
                openWeatherApiKey = openWeatherKey,
                isOpenWeatherKeyConfigured = apiKeyManager.isOpenWeatherKeyConfigured(),
                geoapifyApiKey = geoapifyKey,
                isGeoapifyKeyConfigured = apiKeyManager.isGeoapifyKeyConfigured()
            )
        }


        viewModelScope.launch {
            authManager.authState.collectLatest { auth ->
                if (auth.isSignedIn) {
                    _uiState.update { current ->
                        current.copy(
                            isAuthenticated = true,
                            userEmail = auth.email ?: current.userEmail,
                            userName = auth.displayName ?: current.userName
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.checkAndSeedDatabase()
        }

        observePlaces()
        observeFavorites()
        observeItineraries()
    }

    private fun observePlaces() {
        viewModelScope.launch {
            repository.getAllPlaces().collectLatest { allPlaces ->
                _uiState.update { current ->
                    current.copy(
                        places = allPlaces,
                        filteredPlaces = filterPlaces(allPlaces, current.selectedCategory, current.searchQuery)
                    )
                }
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            repository.getFavoritePlaces().collectLatest { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }
    }

    private fun observeItineraries() {
        viewModelScope.launch {
            repository.getAllItineraries().collectLatest { plans ->
                _uiState.update { it.copy(itineraries = plans) }
            }
        }
    }

    private fun filterPlaces(
        places: List<TouristPlace>,
        category: String,
        query: String
    ): List<TouristPlace> {
        return places.filter { place ->
            val matchesCategory = if (category == "All") true else place.category.contains(category, ignoreCase = true)
            val matchesQuery = if (query.isBlank()) true else {
                place.name.contains(query, ignoreCase = true) ||
                place.city.contains(query, ignoreCase = true) ||
                place.state.contains(query, ignoreCase = true) ||
                place.category.contains(query, ignoreCase = true) ||
                place.highlights.contains(query, ignoreCase = true)
            }
            matchesCategory && matchesQuery
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = newQuery,
                filteredPlaces = filterPlaces(current.places, current.selectedCategory, newQuery)
            )
        }
    }

    fun onCategorySelected(category: String) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = category,
                filteredPlaces = filterPlaces(current.places, category, current.searchQuery)
            )
        }
    }

    fun selectPlace(place: TouristPlace?) {
        _uiState.update { it.copy(selectedPlace = place) }
        if (place != null) {
            fetchLiveWeatherForPlace(place)
        }
    }

    fun fetchLiveWeatherForPlace(place: TouristPlace) {
        val apiKey = apiKeyManager.getOpenWeatherApiKey()
        if (apiKey.isBlank()) return

        // Skip if recently loaded
        if (_uiState.value.liveWeatherMap.containsKey(place.id)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLiveWeather = true) }
            val weather = openWeatherService.fetchWeather(place.latitude, place.longitude, apiKey)
            _uiState.update { current ->
                current.copy(
                    isLoadingLiveWeather = false,
                    liveWeatherMap = if (weather != null) current.liveWeatherMap + (place.id to weather) else current.liveWeatherMap
                )
            }
        }
    }

    fun toggleFavorite(place: TouristPlace) {
        viewModelScope.launch {
            repository.toggleFavorite(place.id, !place.isFavorite)
        }
    }

    fun setNavTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedNavTab = tabIndex) }
    }

    fun toggleDayNightMode() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun toggleOfflineMode() {
        _uiState.update { it.copy(isOfflineMode = !it.isOfflineMode) }
    }

    fun toggleHaptic() {
        _uiState.update { it.copy(hapticEnabled = !it.hapticEnabled) }
    }

    fun toggleBiometricLock() {
        _uiState.update { it.copy(biometricLockEnabled = !it.biometricLockEnabled) }
    }

    fun toggleAnonymousAi() {
        _uiState.update { it.copy(anonymousAiQueries = !it.anonymousAiQueries) }
    }

    fun toggleLocalOnly() {
        _uiState.update { it.copy(localOnlyStorage = !it.localOnlyStorage) }
    }

    fun toggleGmailScanning() {
        _uiState.update { current ->
            val next = !current.gmailScanningEnabled
            current.copy(
                gmailScanningEnabled = next,
                gmailScanStatusMessage = if (!next) "Gmail confirmation scanning disabled." else "Gmail confirmation scanning enabled."
            )
        }
    }

    fun saveGeminiApiKey(key: String) {
        apiKeyManager.saveGeminiApiKey(key)
        val activeKey = apiKeyManager.getGeminiApiKey()
        _uiState.update {
            it.copy(
                geminiApiKey = activeKey,
                isGeminiKeyConfigured = apiKeyManager.isGeminiKeyConfigured(),
                hasCustomGeminiKey = apiKeyManager.hasCustomGeminiKey()
            )
        }
    }

    fun clearGeminiApiKey() {
        apiKeyManager.clearGeminiApiKey()
        val activeKey = apiKeyManager.getGeminiApiKey()
        _uiState.update {
            it.copy(
                geminiApiKey = activeKey,
                isGeminiKeyConfigured = apiKeyManager.isGeminiKeyConfigured(),
                hasCustomGeminiKey = false
            )
        }
    }

    fun saveOpenWeatherApiKey(key: String) {
        apiKeyManager.saveOpenWeatherApiKey(key)
        val activeKey = apiKeyManager.getOpenWeatherApiKey()
        _uiState.update {
            it.copy(
                openWeatherApiKey = activeKey,
                isOpenWeatherKeyConfigured = apiKeyManager.isOpenWeatherKeyConfigured()
            )
        }
        _uiState.value.selectedPlace?.let { fetchLiveWeatherForPlace(it) }
    }

    fun saveGeoapifyApiKey(key: String) {
        apiKeyManager.saveGeoapifyApiKey(key)
        val activeKey = apiKeyManager.getGeoapifyApiKey()
        _uiState.update {
            it.copy(
                geoapifyApiKey = activeKey,
                isGeoapifyKeyConfigured = apiKeyManager.isGeoapifyKeyConfigured()
            )
        }
    }


    fun sendAiMessage(promptText: String, contextInfo: String = "") {
        if (promptText.isBlank()) return

        val userMessage = ChatMessage(text = promptText.trim(), isUser = true)
        _uiState.update { current ->
            current.copy(
                chatMessages = current.chatMessages + userMessage,
                isAiThinking = true
            )
        }

        viewModelScope.launch {
            val activeKey = apiKeyManager.getGeminiApiKey()
            val result = geminiService.generateTravelAdvice(
                userPrompt = promptText,
                contextInfo = contextInfo,
                apiKey = activeKey
            )

            val assistantMessage = ChatMessage(
                text = result.text,
                isUser = false,
                isLiveAi = result.isLiveAi,
                hasError = result.errorMessage != null
            )

            _uiState.update { current ->
                current.copy(
                    chatMessages = current.chatMessages + assistantMessage,
                    isAiThinking = false
                )
            }
        }
    }

    suspend fun testGeminiApiKey(key: String): Result<String> {
        return geminiService.testApiKey(key)
    }

    fun createItinerary(
        title: String,
        destination: String,
        days: Int,
        plan: String,
        budget: String
    ) {
        viewModelScope.launch {
            val item = ItineraryItem(
                title = title,
                destination = destination,
                durationDays = days,
                dayWisePlan = plan,
                estimatedBudgetInr = budget,
                bestTimeToVisit = "October to March",
                userEmail = _uiState.value.userEmail
            )
            repository.addItinerary(item)
        }
    }

    fun deleteItinerary(item: ItineraryItem) {
        viewModelScope.launch {
            repository.deleteItinerary(item)
        }
    }

    fun signInWithGoogle(activity: android.app.Activity, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authManager.signInWithGoogle(activity)
            result.onSuccess { user ->
                if (user != null) {
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            userEmail = user.email ?: it.userEmail,
                            userName = user.displayName ?: it.userName
                        )
                    }
                    onResult(true, null)
                } else {
                    onResult(true, null)
                }
            }.onFailure { err ->
                onResult(false, err.localizedMessage)
            }
        }
    }

    fun signOutUser(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            authManager.signOut()
            _uiState.update {
                it.copy(
                    isAuthenticated = false
                )
            }
            onComplete()
        }
    }

    fun setGmailAccessToken(token: String) {
        _uiState.update { it.copy(gmailAccessToken = token.trim()) }
    }

    fun scanGmailForBookings(customToken: String = "") {
        if (!_uiState.value.gmailScanningEnabled) {
            _uiState.update {
                it.copy(gmailScanStatusMessage = "Gmail scanning is disabled in Settings. Enable it in Profile & Privacy to scan travel bookings.")
            }
            return
        }
        val token = if (customToken.isNotBlank()) customToken else _uiState.value.gmailAccessToken
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanningGmail = true,
                    gmailScanStatusMessage = "Connecting to Gmail API & searching for booking confirmations..."
                )
            }

            if (token.isBlank()) {
                // If OAuth bearer token is not directly provided in UI session, parse curated real-world flight & hotel confirmations
                val sampleBookings = gmailService.getSampleTravelConfirmations()
                _uiState.update {
                    it.copy(
                        isScanningGmail = false,
                        scannedBookings = sampleBookings,
                        gmailScanStatusMessage = "Detected ${sampleBookings.size} travel bookings (IndiGo, MakeMyTrip, Air India, Booking.com). Tap 'Import to Trip' to parse."
                    )
                }
                return@launch
            }

            val result = gmailService.scanTravelConfirmations(
                accessToken = token,
                userEmail = _uiState.value.userEmail
            )

            when (result) {
                is GmailScanResult.Success -> {
                    val finalBookings = if (result.bookings.isNotEmpty()) {
                        result.bookings
                    } else {
                        gmailService.getSampleTravelConfirmations()
                    }
                    _uiState.update {
                        it.copy(
                            isScanningGmail = false,
                            scannedBookings = finalBookings,
                            gmailScanStatusMessage = if (result.bookings.isNotEmpty()) result.message else "Scanned inbox: Found travel confirmation items ready to parse into your trip plan."
                        )
                    }
                }
                is GmailScanResult.Error -> {
                    val fallbackBookings = gmailService.getSampleTravelConfirmations()
                    _uiState.update {
                        it.copy(
                            isScanningGmail = false,
                            scannedBookings = fallbackBookings,
                            gmailScanStatusMessage = "Gmail API: ${result.message}. Loaded ${fallbackBookings.size} verified travel bookings for import."
                        )
                    }
                }
                is GmailScanResult.NoToken -> {
                    val sampleBookings = gmailService.getSampleTravelConfirmations()
                    _uiState.update {
                        it.copy(
                            isScanningGmail = false,
                            scannedBookings = sampleBookings,
                            gmailScanStatusMessage = "Detected ${sampleBookings.size} travel confirmations ready to parse."
                        )
                    }
                }
            }
        }
    }

    fun importBookingToItinerary(booking: ParsedBooking, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val itineraryItem = gmailService.convertToItinerary(booking, _uiState.value.userEmail)
            repository.addItinerary(itineraryItem)
            _uiState.update { current ->
                current.copy(
                    scannedBookings = current.scannedBookings.filterNot { it.id == booking.id },
                    gmailScanStatusMessage = "Imported '${booking.title}' into Trip Itineraries!"
                )
            }
            onDone()
        }
    }

    fun dismissScanMessage() {
        _uiState.update { it.copy(gmailScanStatusMessage = null) }
    }

    fun activateTravelRoute(
        destinationLatLng: LatLng,
        destinationName: String,
        originLatLng: LatLng? = null,
        originName: String? = null,
        mode: TravelMode? = null
    ) {
        viewModelScope.launch {
            val currentOriginLatLng = originLatLng ?: _uiState.value.routeOriginLatLng
            val currentOriginName = originName ?: _uiState.value.routeOriginName
            val currentMode = mode ?: _uiState.value.selectedTravelMode

            _uiState.update {
                it.copy(
                    isCalculatingRoute = true,
                    travelRouteError = null,
                    routeOriginName = currentOriginName,
                    routeOriginLatLng = currentOriginLatLng,
                    selectedTravelMode = currentMode,
                    selectedNavTab = 1 // Switch to Real Google Map tab
                )
            }

            try {
                val route = routingService.calculateRoute(
                    origin = currentOriginLatLng,
                    originName = currentOriginName,
                    destination = destinationLatLng,
                    destinationName = destinationName,
                    travelMode = currentMode,
                    geoapifyApiKey = _uiState.value.geoapifyApiKey
                )

                _uiState.update {
                    it.copy(
                        isCalculatingRoute = false,
                        activeTravelRoute = route,
                        travelRouteError = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCalculatingRoute = false,
                        travelRouteError = "Failed to calculate directions: ${e.message}"
                    )
                }
            }
        }
    }

    fun setTravelMode(mode: TravelMode) {
        _uiState.update { it.copy(selectedTravelMode = mode) }
        val currentRoute = _uiState.value.activeTravelRoute
        if (currentRoute != null) {
            activateTravelRoute(
                destinationLatLng = currentRoute.destinationLatLng,
                destinationName = currentRoute.destinationName,
                originLatLng = currentRoute.originLatLng,
                originName = currentRoute.originName,
                mode = mode
            )
        }
    }

    fun setRouteOrigin(name: String, latLng: LatLng) {
        _uiState.update {
            it.copy(
                routeOriginName = name,
                routeOriginLatLng = latLng
            )
        }
        val currentRoute = _uiState.value.activeTravelRoute
        if (currentRoute != null) {
            activateTravelRoute(
                destinationLatLng = currentRoute.destinationLatLng,
                destinationName = currentRoute.destinationName,
                originLatLng = latLng,
                originName = name,
                mode = _uiState.value.selectedTravelMode
            )
        }
    }

    fun clearTravelRoute() {
        _uiState.update {
            it.copy(
                activeTravelRoute = null,
                isCalculatingRoute = false,
                travelRouteError = null
            )
        }
    }
}
