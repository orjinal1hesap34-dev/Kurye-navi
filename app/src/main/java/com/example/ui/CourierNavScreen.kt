package com.example.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.NavigationState
import com.example.location.LocationPermissionBanner
import com.example.location.rememberLocationPermissionHandler
import com.example.ui.components.AddressSearchSheet
import com.example.ui.components.CourierBottomPanel
import com.example.ui.components.CourierManeuverCard
import com.example.ui.components.CourierTopBar
import com.example.ui.components.MapboxTokenDialog
import com.example.ui.map.CourierMapView

@Composable
fun CourierNavScreen(
    viewModel: CourierViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val navigationState by viewModel.navigationState.collectAsState()
    val mapStatus by viewModel.mapStatus.collectAsState()
    val routeError by viewModel.routeError.collectAsState()
    val forceFallbackStyle by viewModel.forceFallbackStyle.collectAsState()

    val currentLocation by viewModel.locationTracker.currentLocation.collectAsState()
    val isGpsActive by viewModel.locationTracker.isGpsActive.collectAsState()
    val isSimulating by viewModel.locationTracker.isSimulating.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val destinationTitle by viewModel.destinationTitle.collectAsState()
    val targetBuilding by viewModel.targetBuilding.collectAsState()
    val currentRoute by viewModel.currentRoute.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val distanceToManeuver by viewModel.distanceToNextManeuver.collectAsState()
    val remainingDistance by viewModel.remainingDistance.collectAsState()
    val remainingDuration by viewModel.remainingDurationSeconds.collectAsState()
    val isCameraLocked by viewModel.isCameraLocked.collectAsState()
    val isNightMode by viewModel.isNightMode.collectAsState()
    val shouldTriggerArrivalZoom by viewModel.shouldTriggerArrivalZoom.collectAsState()
    val isSearchOpen by viewModel.isSearchOpen.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isMapboxInfoOpen by viewModel.isMapboxInfoOpen.collectAsState()
    val isLoadingRoute by viewModel.isLoadingRoute.collectAsState()
    val isMuted by viewModel.voiceNavigator.isMuted.collectAsState()
    val multiStopPlan by viewModel.multiStopPlan.collectAsState()

    // Manage location permission and interaction with map user location
    val locationPermissionState = rememberLocationPermissionHandler(
        onPermissionGranted = {
            viewModel.locationTracker.startTracking()
            viewModel.recenterCamera()
        }
    )

    // Hardware back button handling
    BackHandler(enabled = isSearchOpen || navigationState == NavigationState.NAVIGATING) {
        when {
            isSearchOpen -> viewModel.closeSearchSheet()
            navigationState == NavigationState.NAVIGATING -> viewModel.stopNavigation()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Full Screen Map with Building & Door Numbers
            CourierMapView(
                currentLocation = currentLocation,
                destination = destination,
                targetBuilding = targetBuilding,
                currentRoute = currentRoute,
                navigationState = navigationState,
                isCameraLocked = isCameraLocked,
                isNightMode = isNightMode,
                forceFallbackStyle = forceFallbackStyle,
                shouldTriggerArrivalZoom = shouldTriggerArrivalZoom,
                fitRouteEvent = viewModel.fitRouteEvent,
                onMapLongClick = { point ->
                    viewModel.selectDestination(point.latitude, point.longitude)
                },
                onCameraManualMove = {
                    viewModel.onCameraManuallyMoved()
                },
                onMapStyleLoaded = {
                    viewModel.onMapStyleLoaded()
                },
                onMapLoadFailed = { error ->
                    viewModel.onMapLoadFailed(error)
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Top Header Overlay (TopBar or Turn Maneuver Card)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                if (navigationState == NavigationState.NAVIGATING) {
                    CourierManeuverCard(
                        currentStep = currentStep,
                        distanceToManeuverMeters = distanceToManeuver,
                        isMuted = isMuted,
                        onToggleMute = { viewModel.toggleVoiceMute() }
                    )
                } else {
                    CourierTopBar(
                        isNightMode = isNightMode,
                        isGpsActive = isGpsActive,
                        isSimulating = isSimulating,
                        hasRoute = currentRoute != null,
                        onOpenSearch = { viewModel.openSearchSheet() },
                        onToggleNightMode = { viewModel.toggleNightMode() },
                        onOpenMapboxInfo = { viewModel.openMapboxInfoDialog() },
                        onToggleSimulation = { viewModel.toggleSimulation() }
                    )
                }

                // Map Error Banner with Retry button if style or network fails
                val currentMapStatus = mapStatus
                if (currentMapStatus is MapStatus.Error) {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (currentMapStatus.isAuthError) Color(0xFFF59E0B) else Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentMapStatus.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentMapStatus.message,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.retryMapLoad() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("retry_map_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Tekrar Dene", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                if (currentMapStatus.isAuthError) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.openMapboxInfoDialog() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(text = "Anahtar Rehberi", color = Color(0xFF0F172A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Location Permission Banner (Informative, manages user location on map)
                LocationPermissionBanner(permissionState = locationPermissionState)
            }

            // 3. Bottom Control & Delivery Info Panel
            CourierBottomPanel(
                navigationState = navigationState,
                destination = destination,
                destinationTitle = destinationTitle,
                targetBuilding = targetBuilding,
                currentRoute = currentRoute,
                currentLocation = currentLocation,
                remainingDistanceMeters = remainingDistance,
                remainingDurationSeconds = remainingDuration,
                isLoadingRoute = isLoadingRoute,
                routeError = routeError,
                isCameraLocked = isCameraLocked,
                isNightMode = isNightMode,
                onCreateRoute = { viewModel.createRoute() },
                onStartNavigation = { viewModel.startNavigation() },
                onStopNavigation = { viewModel.stopNavigation() },
                onRecenterCamera = { viewModel.recenterCamera() },
                onClearRouteError = { viewModel.clearRouteError() },
                multiStopPlan = multiStopPlan,
                onCompleteCurrentStop = { viewModel.completeCurrentDeliveryPoint() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // 4. Address Search Sheet Modal
            AddressSearchSheet(
                isOpen = isSearchOpen,
                isSearching = isSearching,
                searchResults = searchResults,
                onQueryChange = { query -> viewModel.searchAddress(query) },
                onSelectResult = { result -> viewModel.selectSearchResult(result) },
                onDismiss = { viewModel.closeSearchSheet() }
            )

            // 5. Mapbox Token Instructions Dialog
            MapboxTokenDialog(
                isOpen = isMapboxInfoOpen,
                onDismiss = { viewModel.closeMapboxInfoDialog() }
            )
        }
    }
}
