package com.example.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.model.BuildingInfo
import com.example.data.model.NavLocation
import com.example.data.model.NavigationState
import com.example.data.model.RouteModel
import kotlinx.coroutines.flow.SharedFlow
import org.maplibre.android.geometry.LatLng

/**
 * Courier Map View Composable delegating to the production MapLibreMapComponent.
 */
@Composable
fun CourierMapView(
    currentLocation: NavLocation,
    destination: NavLocation?,
    targetBuilding: BuildingInfo?,
    currentRoute: RouteModel?,
    navigationState: NavigationState,
    isCameraLocked: Boolean,
    isNightMode: Boolean,
    forceFallbackStyle: Boolean,
    shouldTriggerArrivalZoom: Boolean,
    fitRouteEvent: SharedFlow<List<NavLocation>>,
    onMapLongClick: (LatLng) -> Unit,
    onCameraManualMove: () -> Unit,
    onMapStyleLoaded: () -> Unit,
    onMapLoadFailed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    MapLibreMapComponent(
        currentLocation = currentLocation,
        destination = destination,
        targetBuilding = targetBuilding,
        currentRoute = currentRoute,
        navigationState = navigationState,
        isCameraLocked = isCameraLocked,
        isNightMode = isNightMode,
        forceFallbackStyle = forceFallbackStyle,
        shouldTriggerArrivalZoom = shouldTriggerArrivalZoom,
        fitRouteEvent = fitRouteEvent,
        onMapLongClick = onMapLongClick,
        onCameraManualMove = onCameraManualMove,
        onMapStyleLoaded = onMapStyleLoaded,
        onMapLoadFailed = onMapLoadFailed,
        modifier = modifier
    )
}
