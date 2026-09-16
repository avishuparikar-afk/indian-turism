package com.example.ui.screens

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.api.GeoapifyAddressResult
import com.example.data.api.GeoapifyService
import com.example.data.model.TouristPlace
import com.example.ui.TourismUiState
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTag
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.PeacockTeal
import com.example.ui.theme.SaffronLight
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.StarGold
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

private val regionsList = listOf("All", "North", "South", "East", "West", "North-East")

private val INDIA_CENTER = LatLng(22.5937, 78.9629)
private val REGION_COORDINATES = mapOf(
    "All" to Pair(INDIA_CENTER, 4.8f),
    "North" to Pair(LatLng(28.6139, 77.2090), 6.2f),
    "South" to Pair(LatLng(11.9404, 77.9404), 6.2f),
    "East" to Pair(LatLng(22.5726, 88.3639), 6.2f),
    "West" to Pair(LatLng(20.5937, 73.9629), 6.2f),
    "North-East" to Pair(LatLng(26.2006, 92.9376), 6.5f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    state: TourismUiState,
    onPlaceSelected: (TouristPlace) -> Unit,
    modifier: Modifier = Modifier,
    haptic: HapticFeedback = LocalHapticFeedback.current
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedRegion by remember { mutableStateOf("All") }
    var highlightedPlace by remember { mutableStateOf<TouristPlace?>(state.places.firstOrNull()) }
    var showInAppRouteSheet by remember { mutableStateOf(false) }
    var currentMapType by remember { mutableStateOf(MapType.NORMAL) }

    // Address Selection state (Native Places SDK + Geoapify)
    var addressQuery by remember { mutableStateOf("") }
    var addressPredictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    val geoapifyService = remember { GeoapifyService() }
    var geoapifyPredictions by remember { mutableStateOf<List<GeoapifyAddressResult>>(emptyList()) }
    var isSearchingAddress by remember { mutableStateOf(false) }
    var showPredictionDropdown by remember { mutableStateOf(false) }
    var selectedAddressResult by remember {
        mutableStateOf<Triple<String, String, LatLng>?>(null) // Name, Address, LatLng
    }

    // Initialize Places SDK safely with configured key
    val placesClient = remember(context) {
        if (!Places.isInitialized()) {
            val apiKey = BuildConfig.MAPS_API_KEY.ifEmpty { "AIzaSy_IndiaTourismExplorerKey" }
            try {
                Places.initialize(context.applicationContext, apiKey)
            } catch (_: Exception) {}
        }
        try {
            Places.createClient(context)
        } catch (_: Exception) {
            null
        }
    }

    // Real Google Map camera position
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(INDIA_CENTER, 4.8f)
    }

    val mapProperties = remember(currentMapType) {
        MapProperties(
            mapType = currentMapType,
            isTrafficEnabled = false,
            isIndoorEnabled = true
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = true,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            tiltGesturesEnabled = true,
            rotationGesturesEnabled = true,
            scrollGesturesEnabled = true,
            zoomGesturesEnabled = true
        )
    }

    val displayedPlaces = remember(selectedRegion, state.places) {
        if (selectedRegion == "All") {
            state.places
        } else {
            state.places.filter { it.region.equals(selectedRegion, ignoreCase = true) }
        }
    }

    // Auto animate to selected region
    LaunchedEffect(selectedRegion) {
        val target = REGION_COORDINATES[selectedRegion] ?: Pair(INDIA_CENTER, 4.8f)
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(target.first, target.second),
            durationMs = 900
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Real Google Map Composable embedded inside the app
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .testTag("google_map_view"),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapClick = {
                // Dim miss selection
            }
        ) {
            // Render Real Google Maps Markers for tourist attractions
            displayedPlaces.forEach { place ->
                val isSelected = (highlightedPlace?.id == place.id)
                Marker(
                    state = MarkerState(position = LatLng(place.latitude, place.longitude)),
                    title = place.name,
                    snippet = "${place.city}, ${place.state} • ₹${place.entryFeeIndian}",
                    onClick = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        highlightedPlace = place
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(place.latitude, place.longitude),
                                    11.5f
                                ),
                                durationMs = 700
                            )
                        }
                        false // return false so info window shows
                    }
                )
            }

            // Marker for Places SDK selected address
            selectedAddressResult?.let { (name, address, latLng) ->
                Marker(
                    state = MarkerState(position = latLng),
                    title = name,
                    snippet = address,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    onClick = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(latLng, 16f),
                                durationMs = 600
                            )
                        }
                        false
                    }
                )
            }
        }

        // Top Glass Bar: Title, Map Type Switcher, and Region Filters
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Glass Capsule with Title
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (state.isDarkTheme) Color(0xD9101827) else Color(0xEBFFFFFF)
                        )
                        .border(
                            1.dp,
                            if (state.isDarkTheme) Color(0x33FFFFFF) else Color(0x1F000000),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.isMapsKeyConfigured) EmeraldGreen else SaffronPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isMapsKeyConfigured) "Google Maps (Live)" else "Map Explorer",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Map Style Switcher (Normal / Satellite / Terrain)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (state.isDarkTheme) Color(0xD9101827) else Color(0xEBFFFFFF)
                        )
                        .border(
                            1.dp,
                            if (state.isDarkTheme) Color(0x33FFFFFF) else Color(0x1F000000),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentMapType = MapType.NORMAL
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (currentMapType == MapType.NORMAL) PeacockTeal.copy(alpha = 0.25f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Normal Map",
                            tint = if (currentMapType == MapType.NORMAL) PeacockTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentMapType = MapType.SATELLITE
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (currentMapType == MapType.SATELLITE) SaffronPrimary.copy(alpha = 0.25f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Satellite,
                            contentDescription = "Satellite Map",
                            tint = if (currentMapType == MapType.SATELLITE) SaffronPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentMapType = MapType.TERRAIN
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (currentMapType == MapType.TERRAIN) EmeraldGreen.copy(alpha = 0.25f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.AltRoute,
                            contentDescription = "Terrain Map",
                            tint = if (currentMapType == MapType.TERRAIN) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Region selector chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(regionsList, key = { it }) { region ->
                    val isSelected = (selectedRegion == region)
                    val activeBg = if (isSelected) SaffronPrimary else if (state.isDarkTheme) Color(0xD9101827) else Color(0xEBFFFFFF)
                    val activeBorder = if (isSelected) SaffronPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(activeBg)
                            .border(1.dp, activeBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedRegion = region
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = region,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Address Selection Search Bar (Native Places SDK)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (state.isDarkTheme) Color(0xD9101827) else Color(0xEBFFFFFF))
                    .border(
                        1.dp,
                        if (state.isDarkTheme) Color(0x33FFFFFF) else Color(0x1F000000),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Address",
                            tint = SaffronPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = addressQuery,
                            onValueChange = { newQuery ->
                                addressQuery = newQuery
                                if (newQuery.length >= 2) {
                                    isSearchingAddress = true
                                    showPredictionDropdown = true
                                    if (placesClient != null) {
                                        val request = FindAutocompletePredictionsRequest.builder()
                                            .setQuery(newQuery)
                                            .setCountries(listOf("IN"))
                                            .build()
                                        placesClient.findAutocompletePredictions(request)
                                            .addOnSuccessListener { response ->
                                                addressPredictions = response.autocompletePredictions
                                                isSearchingAddress = false
                                            }
                                            .addOnFailureListener {
                                                isSearchingAddress = false
                                            }
                                    }
                                    if (state.geoapifyApiKey.isNotBlank()) {
                                        coroutineScope.launch {
                                            val geoResults = geoapifyService.searchAddress(newQuery, state.geoapifyApiKey)
                                            geoapifyPredictions = geoResults
                                            isSearchingAddress = false
                                        }
                                    }
                                } else {
                                    addressPredictions = emptyList()
                                    geoapifyPredictions = emptyList()
                                    showPredictionDropdown = false
                                }
                            },
                            placeholder = {
                                Text(
                                    text = "Search address, city, or landmark...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("address_search_input"),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = SaffronPrimary
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        if (isSearchingAddress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = SaffronPrimary
                            )
                        } else if (addressQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    addressQuery = ""
                                    addressPredictions = emptyList()
                                    geoapifyPredictions = emptyList()
                                    showPredictionDropdown = false
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Autocomplete Predictions List (Google Places + Geoapify)
                    if (showPredictionDropdown && (addressPredictions.isNotEmpty() || geoapifyPredictions.isNotEmpty())) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            addressPredictions.take(3).forEach { prediction ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val placeId = prediction.placeId
                                            val primaryText = prediction.getPrimaryText(null).toString()
                                            val secondaryText = prediction.getSecondaryText(null).toString()
                                            showPredictionDropdown = false
                                            addressQuery = primaryText

                                            if (placesClient != null) {
                                                val placeFields = listOf(
                                                    Place.Field.ID,
                                                    Place.Field.NAME,
                                                    Place.Field.ADDRESS,
                                                    Place.Field.LAT_LNG
                                                )
                                                val fetchRequest = FetchPlaceRequest.newInstance(placeId, placeFields)
                                                placesClient.fetchPlace(fetchRequest)
                                                    .addOnSuccessListener { fetchResponse ->
                                                        val fetchedPlace = fetchResponse.place
                                                        val latLng = fetchedPlace.latLng
                                                        if (latLng != null) {
                                                            selectedAddressResult = Triple(
                                                                primaryText,
                                                                fetchedPlace.address ?: secondaryText,
                                                                latLng
                                                            )
                                                            coroutineScope.launch {
                                                                cameraPositionState.animate(
                                                                    CameraUpdateFactory.newLatLngZoom(latLng, 15f),
                                                                    durationMs = 700
                                                                )
                                                            }
                                                        }
                                                    }
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = SaffronPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prediction.getPrimaryText(null).toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = prediction.getSecondaryText(null).toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            geoapifyPredictions.take(3).forEach { geoResult ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showPredictionDropdown = false
                                            addressQuery = geoResult.name
                                            selectedAddressResult = Triple(
                                                geoResult.name,
                                                geoResult.formattedAddress,
                                                geoResult.latLng
                                            )
                                            coroutineScope.launch {
                                                cameraPositionState.animate(
                                                    CameraUpdateFactory.newLatLngZoom(geoResult.latLng, 15f),
                                                    durationMs = 700
                                                )
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = geoResult.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            GlassTag(text = "Geoapify", accentColor = EmeraldGreen)
                                        }
                                        Text(
                                            text = geoResult.formattedAddress,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating In-App Map Controls (Zoom In, Zoom Out, Center on India)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    coroutineScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.zoomIn(), durationMs = 400)
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (state.isDarkTheme) Color(0xE6101827) else Color(0xF2FFFFFF),
                        CircleShape
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = {
                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    coroutineScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.zoomOut(), durationMs = 400)
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (state.isDarkTheme) Color(0xE6101827) else Color(0xF2FFFFFF),
                        CircleShape
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = {
                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(INDIA_CENTER, 4.8f),
                            durationMs = 800
                        )
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (state.isDarkTheme) Color(0xE6101827) else Color(0xF2FFFFFF),
                        CircleShape
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Reset Center",
                    tint = SaffronPrimary
                )
            }
        }

        // Bottom Selected Address Info Card (Places SDK Address Selection Result)
        AnimatedVisibility(
            visible = selectedAddressResult != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = if (highlightedPlace != null) 230.dp else 96.dp)
        ) {
            val (name, address, latLng) = selectedAddressResult ?: return@AnimatedVisibility

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PeacockTeal.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Selected Location",
                            tint = PeacockTeal,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format("%.4f°N, %.4f°E", latLng.latitude, latLng.longitude),
                            style = MaterialTheme.typography.labelSmall,
                            color = PeacockTeal,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            selectedAddressResult = null
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss address selection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Bottom In-App Landmark Preview Card (DOES NOT redirect to external app)
        AnimatedVisibility(
            visible = highlightedPlace != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
        ) {
            val place = highlightedPlace ?: return@AnimatedVisibility

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            AsyncImage(
                                model = place.imageUrl,
                                contentDescription = place.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassTag(
                                    text = place.category,
                                    accentColor = SaffronPrimary
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = StarGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${place.rating}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = place.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )

                            Text(
                                text = "${place.city}, ${place.state} • ₹${place.entryFeeIndian}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // In-App Action Buttons: "In-App Route & Transit" and "Place Details"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showInAppRouteSheet = true
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PeacockTeal),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsTransit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("In-App Route", style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = {
                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPlaceSelected(place)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Place Details", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // In-App Route & Transit Details Bottom Sheet (keeps the user 100% inside this app!)
        if (showInAppRouteSheet && highlightedPlace != null) {
            val place = highlightedPlace!!
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            // Approximate distance calculation from New Delhi reference coordinates
            val distanceKm = remember(place.latitude, place.longitude) {
                val results = FloatArray(1)
                Location.distanceBetween(28.6139, 77.2090, place.latitude, place.longitude, results)
                (results[0] / 1000).toInt()
            }

            ModalBottomSheet(
                onDismissRequest = { showInAppRouteSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "In-App Route & Transit",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SaffronPrimary
                            )
                            Text(
                                text = place.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = { showInAppRouteSheet = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Geographic & Distance Info Card
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Coordinates",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format("%.2f°N, %.2f°E", place.latitude, place.longitude),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PeacockTeal
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(30.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "From New Delhi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$distanceKm km",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SaffronPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Transit & Navigation Recommendations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Train & Transit Item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PeacockTeal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Train,
                                contentDescription = null,
                                tint = PeacockTeal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Rail Connectivity",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Nearest hub in ${place.city}. Fast Vande Bharat and Superfast Express trains available via IRCTC.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Local Transport Item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SaffronPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = SaffronPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Local Sightseeing & Commute",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "App-based cabs (Ola/Uber), pre-paid government auto-rickshaws, and tourist bus shuttles available.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Close & In-App Details button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(place.latitude, place.longitude),
                                            15f
                                        ),
                                        durationMs = 600
                                    )
                                }
                                showInAppRouteSheet = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Zoom In Map")
                        }

                        Button(
                            onClick = {
                                showInAppRouteSheet = false
                                onPlaceSelected(place)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Full Details")
                        }
                    }
                }
            }
        }
    }
}
