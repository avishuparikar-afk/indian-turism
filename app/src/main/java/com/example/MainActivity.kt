package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.TouristPlace
import com.example.ui.TourismUiState
import com.example.ui.TourismViewModel
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.ItineraryScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.PlaceDetailSheet
import com.example.ui.screens.ProfilePrivacyScreen
import com.example.ui.theme.IndiaTourismTheme
import com.example.ui.theme.PeacockTeal
import com.example.ui.theme.SaffronPrimary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: TourismViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()

            IndiaTourismTheme(darkTheme = uiState.isDarkTheme) {
                IndiaTourismApp(
                    state = uiState,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun IndiaTourismApp(
    state: TourismUiState,
    viewModel: TourismViewModel
) {
    val haptic = LocalHapticFeedback.current

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            GlassBottomNavigationBar(
                selectedTab = state.selectedNavTab,
                onTabSelected = { tab ->
                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setNavTab(tab)
                },
                isDark = state.isDarkTheme
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = state.selectedNavTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen_transition"
            ) { tab ->
                when (tab) {
                    0 -> ExploreScreen(
                        state = state,
                        onSearchChange = viewModel::onSearchQueryChanged,
                        onCategorySelected = viewModel::onCategorySelected,
                        onPlaceClick = viewModel::selectPlace,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onToggleTheme = viewModel::toggleDayNightMode,
                        onToggleOffline = viewModel::toggleOfflineMode,
                        modifier = Modifier.testTag("explore_screen")
                    )
                    1 -> MapScreen(
                        state = state,
                        onPlaceSelected = viewModel::selectPlace,
                        modifier = Modifier.testTag("map_screen")
                    )
                    2 -> AiAssistantScreen(
                        state = state,
                        onSendMessage = { prompt ->
                            viewModel.sendAiMessage(prompt)
                        },
                        onSaveApiKey = viewModel::saveGeminiApiKey,
                        onClearApiKey = viewModel::clearGeminiApiKey,
                        modifier = Modifier.testTag("ai_assistant_screen")
                    )
                    3 -> ItineraryScreen(
                        state = state,
                        onCreateItinerary = { title, dest, days, plan, budget ->
                            viewModel.createItinerary(title, dest, days, plan, budget)
                        },
                        onDeleteItinerary = viewModel::deleteItinerary,
                        onScanGmail = {
                            viewModel.scanGmailForBookings()
                        },
                        onImportBooking = { booking ->
                            viewModel.importBookingToItinerary(booking)
                        },
                        onDismissScanMessage = {
                            viewModel.dismissScanMessage()
                        },
                        modifier = Modifier.testTag("itinerary_screen")
                    )
                    4 -> {
                        val activity = LocalContext.current as? android.app.Activity
                        ProfilePrivacyScreen(
                            state = state,
                            onToggleTheme = viewModel::toggleDayNightMode,
                            onToggleOffline = viewModel::toggleOfflineMode,
                            onToggleHaptic = viewModel::toggleHaptic,
                            onToggleBiometric = viewModel::toggleBiometricLock,
                            onToggleAnonymousAi = viewModel::toggleAnonymousAi,
                            onToggleLocalOnly = viewModel::toggleLocalOnly,
                            onToggleGmailScanning = viewModel::toggleGmailScanning,
                            onSyncGmail = {
                                viewModel.scanGmailForBookings()
                            },
                            onDismissScanMessage = {
                                viewModel.dismissScanMessage()
                            },
                            onSignInGoogle = {
                                activity?.let { act ->
                                    viewModel.signInWithGoogle(act) { success, msg ->
                                        if (!success && msg != null) {
                                            android.widget.Toast.makeText(act, "Sign-in: $msg", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onSignOut = {
                                viewModel.signOutUser()
                            },
                            modifier = Modifier.testTag("profile_privacy_screen")
                        )
                    }
                }
            }

            // Place Detail Modal Bottom Sheet
            state.selectedPlace?.let { selected ->
                PlaceDetailSheet(
                    place = selected,
                    liveWeather = state.liveWeatherMap[selected.id],
                    isLoadingWeather = state.isLoadingLiveWeather,
                    onRefreshWeather = { viewModel.fetchLiveWeatherForPlace(selected) },
                    onDismiss = { viewModel.selectPlace(null) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onAskAi = { place ->
                        viewModel.selectPlace(null)
                        viewModel.setNavTab(2) // Switch to AI Guide tab
                        viewModel.sendAiMessage(
                            promptText = "Tell me insider travel tips, best photo spots, local delicacies, and cultural etiquette for visiting ${place.name} in ${place.city}.",
                            contextInfo = "${place.name}, ${place.city}, ${place.state} (${place.category})"
                        )
                    },
                    onViewOnMap = { place ->
                        viewModel.selectPlace(null)
                        viewModel.setNavTab(1) // Switch to Real Google Map tab
                    }
                )
            }
        }
    }
}

@Composable
fun GlassBottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isDark: Boolean
) {
    val navItems = listOf(
        NavItem("Explore", Icons.Default.Explore, 0),
        NavItem("Map", Icons.Default.Map, 1),
        NavItem("AI Guide", Icons.Default.AutoAwesome, 2),
        NavItem("Trips", Icons.Default.CalendarMonth, 3),
        NavItem("Profile", Icons.Default.Person, 4)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val glassBg = if (isDark) Color(0xCC131D33) else Color(0xEBFFFFFF)
        val glassBorder = if (isDark) Color(0x33FFFFFF) else Color(0x1F000000)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(glassBg)
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(glassBorder, Color.Transparent)),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { item ->
                    val isSelected = (selectedTab == item.index)
                    val activeColor = if (item.index == 2) SaffronPrimary else PeacockTeal

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .clickable { onTabSelected(item.index) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            if (isSelected) {
                                Text(
                                    text = " ${item.label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = activeColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class NavItem(val label: String, val icon: ImageVector, val index: Int)

// Kept for Robolectric screenshot & unit tests
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
