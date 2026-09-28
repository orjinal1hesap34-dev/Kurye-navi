package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MapboxConfig
import com.example.data.api.BuildingService
import com.example.data.api.GeocodingService
import com.example.data.api.MultiStopRouteService
import com.example.data.api.RouteResult
import com.example.data.api.RoutingService
import com.example.data.database.OfflineNavigationRepository
import com.example.data.database.entity.CachedRouteEntity
import com.example.data.model.BuildingInfo
import com.example.data.model.DeliveryPoint
import com.example.data.model.MultiStopRoutePlan
import com.example.data.model.MultiStopRouteResult
import com.example.data.model.NavLocation
import com.example.data.model.NavigationState
import com.example.data.model.RouteModel
import com.example.data.model.RouteStep
import com.example.data.model.SearchResult
import com.example.location.LocationTracker
import com.example.navigation.VoiceNavigator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class MapStatus {
    object Loading : MapStatus()
    object Loaded : MapStatus()
    data class Error(val title: String, val message: String, val isAuthError: Boolean) : MapStatus()
}

class CourierViewModel(application: Application) : AndroidViewModel(application) {

    val offlineRepository = OfflineNavigationRepository(application)
    private val geocodingService = GeocodingService()
    private val routingService = RoutingService(offlineRepository = offlineRepository)
    private val buildingService = BuildingService()
    val multiStopRouteService = MultiStopRouteService(offlineRepository = offlineRepository)

    val locationTracker = LocationTracker(application)
    val voiceNavigator = VoiceNavigator(application)

    // Room Database Cached Routes
    val recentCachedRoutes: StateFlow<List<CachedRouteEntity>> = offlineRepository.recentRoutes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Multi-stop Delivery Routing State
    private val _deliveryPoints = MutableStateFlow<List<DeliveryPoint>>(emptyList())
    val deliveryPoints: StateFlow<List<DeliveryPoint>> = _deliveryPoints.asStateFlow()

    private val _multiStopPlan = MutableStateFlow<MultiStopRoutePlan?>(null)
    val multiStopPlan: StateFlow<MultiStopRoutePlan?> = _multiStopPlan.asStateFlow()

    private val _currentLegIndex = MutableStateFlow(0)
    val currentLegIndex: StateFlow<Int> = _currentLegIndex.asStateFlow()

    private val _isMultiStopMode = MutableStateFlow(false)
    val isMultiStopMode: StateFlow<Boolean> = _isMultiStopMode.asStateFlow()

    // Navigation State
    private val _navigationState = MutableStateFlow(NavigationState.IDLE)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    // Map Status & Errors
    private val _mapStatus = MutableStateFlow<MapStatus>(MapStatus.Loading)
    val mapStatus: StateFlow<MapStatus> = _mapStatus.asStateFlow()

    private val _routeError = MutableStateFlow<String?>(null)
    val routeError: StateFlow<String?> = _routeError.asStateFlow()

    private val _forceFallbackStyle = MutableStateFlow(!MapboxConfig.isConfigured)
    val forceFallbackStyle: StateFlow<Boolean> = _forceFallbackStyle.asStateFlow()

    // Event to request camera bounding box fit on route
    private val _fitRouteEvent = MutableSharedFlow<List<NavLocation>>()
    val fitRouteEvent: SharedFlow<List<NavLocation>> = _fitRouteEvent.asSharedFlow()

    // Destination & Target Building
    private val _destination = MutableStateFlow<NavLocation?>(null)
    val destination: StateFlow<NavLocation?> = _destination.asStateFlow()

    private val _destinationTitle = MutableStateFlow("Hedef Konum")
    val destinationTitle: StateFlow<String> = _destinationTitle.asStateFlow()

    private val _targetBuilding = MutableStateFlow<BuildingInfo?>(null)
    val targetBuilding: StateFlow<BuildingInfo?> = _targetBuilding.asStateFlow()

    // Active Route
    private val _currentRoute = MutableStateFlow<RouteModel?>(null)
    val currentRoute: StateFlow<RouteModel?> = _currentRoute.asStateFlow()

    private val _activeStepIndex = MutableStateFlow(0)
    val activeStepIndex: StateFlow<Int> = _activeStepIndex.asStateFlow()

    private val _currentStep = MutableStateFlow<RouteStep?>(null)
    val currentStep: StateFlow<RouteStep?> = _currentStep.asStateFlow()

    private val _distanceToNextManeuver = MutableStateFlow(0.0)
    val distanceToNextManeuver: StateFlow<Double> = _distanceToNextManeuver.asStateFlow()

    private val _remainingDistance = MutableStateFlow(0.0)
    val remainingDistance: StateFlow<Double> = _remainingDistance.asStateFlow()

    private val _remainingDurationSeconds = MutableStateFlow(0.0)
    val remainingDurationSeconds: StateFlow<Double> = _remainingDurationSeconds.asStateFlow()

    // Camera Lock & Arrival Mode
    private val _isCameraLocked = MutableStateFlow(true)
    val isCameraLocked: StateFlow<Boolean> = _isCameraLocked.asStateFlow()

    private val _shouldTriggerArrivalZoom = MutableStateFlow(false)
    val shouldTriggerArrivalZoom: StateFlow<Boolean> = _shouldTriggerArrivalZoom.asStateFlow()

    // UI Controls
    private val _isNightMode = MutableStateFlow(false)
    val isNightMode: StateFlow<Boolean> = _isNightMode.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _isMapboxInfoOpen = MutableStateFlow(false)
    val isMapboxInfoOpen: StateFlow<Boolean> = _isMapboxInfoOpen.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingRoute = MutableStateFlow(false)
    val isLoadingRoute: StateFlow<Boolean> = _isLoadingRoute.asStateFlow()

    private var searchJob: Job? = null
    private var lastSpokenStepIndex = -1
    private var hasAnnouncedArrivalZone = false

    init {
        locationTracker.startTracking()

        viewModelScope.launch {
            locationTracker.currentLocation.collect { loc ->
                processLocationUpdate(loc)
            }
        }
    }

    fun onMapStyleLoaded() {
        _mapStatus.value = MapStatus.Loaded
    }

    fun onMapLoadFailed(errorMessage: String) {
        val isAuth = errorMessage.contains("401") ||
                errorMessage.contains("403") ||
                errorMessage.contains("Unauthorized", ignoreCase = true) ||
                errorMessage.contains("Forbidden", ignoreCase = true)

        if (isAuth && MapboxConfig.isConfigured && !_forceFallbackStyle.value) {
            // Mapbox token failed auth, switch to open vector style automatically
            _forceFallbackStyle.value = true
            _mapStatus.value = MapStatus.Error(
                title = "Mapbox Yetkilendirme Hatası",
                message = "Girilen Mapbox Public Token geçersiz veya yetkisiz (401/403). Açık kaynaklı harita moduna geçildi. Lütfen token'ı kontrol edin.",
                isAuthError = true
            )
        } else {
            _mapStatus.value = MapStatus.Error(
                title = "Harita Yükleme Hatası",
                message = "Harita verileri indirilemedi. İnternet bağlantınızı kontrol edip Tekrar Dene'ye basın.",
                isAuthError = false
            )
        }
    }

    fun retryMapLoad() {
        _mapStatus.value = MapStatus.Loading
        _forceFallbackStyle.value = !MapboxConfig.isConfigured
    }

    fun clearRouteError() {
        _routeError.value = null
    }

    fun selectDestination(lat: Double, lon: Double, fallbackTitle: String? = null) {
        val target = NavLocation(lat, lon)
        _destination.value = target
        _destinationTitle.value = fallbackTitle ?: "Seçilen Konum"
        _navigationState.value = NavigationState.DESTINATION_SELECTED
        _currentRoute.value = null
        _routeError.value = null
        _targetBuilding.value = null
        hasAnnouncedArrivalZone = false

        viewModelScope.launch {
            // 1. Fetch real building polygon, door numbers and entrance points
            val building = buildingService.getBuildingAtLocation(target)
            _targetBuilding.value = building

            // 2. Reverse geocode if title not provided
            if (fallbackTitle == null) {
                val reverseResult = geocodingService.reverseGeocode(lat, lon)
                if (reverseResult != null) {
                    val fullTitle = when {
                        !building?.houseNumber.isNullOrBlank() && !reverseResult.road.isNullOrBlank() ->
                            "${reverseResult.road} No: ${building?.houseNumber}"
                        else -> reverseResult.title
                    }
                    _destinationTitle.value = fullTitle
                }
            } else if (!building?.houseNumber.isNullOrBlank() && !fallbackTitle.contains("No:")) {
                _destinationTitle.value = "$fallbackTitle No: ${building?.houseNumber}"
            }
        }
    }

    fun searchAddress(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _isSearching.value = true
            val results = geocodingService.searchAddress(query)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun selectSearchResult(result: SearchResult) {
        _isSearchOpen.value = false
        _searchResults.value = emptyList()
        selectDestination(
            lat = result.latitude,
            lon = result.longitude,
            fallbackTitle = result.title + if (!result.houseNumber.isNullOrBlank()) " No: ${result.houseNumber}" else ""
        )
    }

    fun createRoute() {
        val dest = _destination.value ?: run {
            _routeError.value = "Lütfen önce haritadan veya aramadan bir hedef seçin."
            return
        }
        val current = locationTracker.currentLocation.value

        viewModelScope.launch {
            _isLoadingRoute.value = true
            _routeError.value = null
            _navigationState.value = NavigationState.ROUTING

            val result = routingService.calculateRoute(
                origin = current,
                destination = dest,
                destinationTitle = _destinationTitle.value,
                destinationHouseNumber = _targetBuilding.value?.houseNumber
            )

            _isLoadingRoute.value = false
            when (result) {
                is RouteResult.Success -> {
                    val route = result.route
                    _currentRoute.value = route
                    _activeStepIndex.value = 0
                    _currentStep.value = route.steps.firstOrNull()
                    _remainingDistance.value = route.totalDistanceMeters
                    _remainingDurationSeconds.value = route.totalDurationSeconds
                    _navigationState.value = NavigationState.DESTINATION_SELECTED
                    _routeError.value = null

                    // Fit camera to show the entire route
                    _fitRouteEvent.emit(route.coordinates)

                    voiceNavigator.announceRouteSummary(route.formattedDistance, route.formattedDuration)
                }
                is RouteResult.Failure -> {
                    _currentRoute.value = null
                    _routeError.value = result.errorMessage
                    _navigationState.value = NavigationState.DESTINATION_SELECTED
                    voiceNavigator.speak("Rota hesaplanamadı.")
                }
            }
        }
    }

    /**
     * Adds a delivery point to the courier's multi-stop delivery queue.
     */
    fun addDeliveryPoint(point: DeliveryPoint) {
        val updated = _deliveryPoints.value.toMutableList().apply {
            add(point.copy(stopOrder = size + 1))
        }
        _deliveryPoints.value = updated
        _isMultiStopMode.value = true
    }

    /**
     * Removes a delivery point from the list.
     */
    fun removeDeliveryPoint(pointId: String) {
        val updated = _deliveryPoints.value.filter { it.id != pointId }.mapIndexed { idx, p ->
            p.copy(stopOrder = idx + 1)
        }
        _deliveryPoints.value = updated
        if (updated.isEmpty()) {
            _isMultiStopMode.value = false
            _multiStopPlan.value = null
        }
    }

    /**
     * Clears all delivery points.
     */
    fun clearDeliveryPoints() {
        _deliveryPoints.value = emptyList()
        _multiStopPlan.value = null
        _isMultiStopMode.value = false
        _currentLegIndex.value = 0
    }

    /**
     * Calculates an efficient multi-stop delivery route using MapLibre/Mapbox directions & optimization.
     */
    fun calculateMultiStopRoute(optimizeOrder: Boolean = true) {
        val points = _deliveryPoints.value.filter { !it.isCompleted }
        if (points.isEmpty()) {
            _routeError.value = "Teslimat listesinde aktif teslimat noktası bulunmuyor."
            return
        }

        val current = locationTracker.currentLocation.value
        viewModelScope.launch {
            _isLoadingRoute.value = true
            _routeError.value = null
            _navigationState.value = NavigationState.ROUTING

            val result = multiStopRouteService.calculateMultiStopRoute(
                origin = current,
                deliveryPoints = points,
                optimizeOrder = optimizeOrder
            )

            _isLoadingRoute.value = false
            when (result) {
                is MultiStopRouteResult.Success -> {
                    val plan = result.plan
                    _multiStopPlan.value = plan
                    _deliveryPoints.value = plan.deliveryPoints
                    _currentLegIndex.value = 0
                    _isMultiStopMode.value = true

                    // Activate the first delivery leg for turn-by-turn navigation
                    val activeRoute = multiStopRouteService.createActiveNavigationRoute(plan, 0)
                    if (activeRoute != null) {
                        _currentRoute.value = activeRoute
                        _destination.value = activeRoute.destination
                        _destinationTitle.value = activeRoute.destinationTitle
                        _activeStepIndex.value = 0
                        _currentStep.value = activeRoute.steps.firstOrNull()
                        _remainingDistance.value = plan.totalDistanceMeters
                        _remainingDurationSeconds.value = plan.totalDurationSeconds
                        _navigationState.value = NavigationState.DESTINATION_SELECTED

                        // Query building footprint for the first delivery stop
                        viewModelScope.launch {
                            _targetBuilding.value = buildingService.getBuildingAtLocation(activeRoute.destination)
                        }

                        _fitRouteEvent.emit(plan.allCoordinates)
                        voiceNavigator.announceRouteSummary(plan.formattedDistance, plan.formattedDuration)
                    }
                }
                is MultiStopRouteResult.Failure -> {
                    _routeError.value = result.errorMessage
                    _navigationState.value = NavigationState.DESTINATION_SELECTED
                    voiceNavigator.speak("Çoklu teslimat rotası hesaplanamadı.")
                }
            }
        }
    }

    /**
     * Marks the current delivery point as completed and advances to the next stop.
     */
    fun completeCurrentDeliveryPoint() {
        val plan = _multiStopPlan.value ?: return
        val currentIdx = _currentLegIndex.value
        val points = _deliveryPoints.value.toMutableList()

        if (currentIdx in points.indices) {
            points[currentIdx] = points[currentIdx].copy(isCompleted = true)
            _deliveryPoints.value = points
        }

        val nextLegIdx = currentIdx + 1
        if (nextLegIdx < plan.legs.size) {
            _currentLegIndex.value = nextLegIdx
            val nextRoute = multiStopRouteService.createActiveNavigationRoute(plan, nextLegIdx)
            if (nextRoute != null) {
                _currentRoute.value = nextRoute
                _destination.value = nextRoute.destination
                _destinationTitle.value = nextRoute.destinationTitle
                _activeStepIndex.value = 0
                _currentStep.value = nextRoute.steps.firstOrNull()
                _remainingDistance.value = nextRoute.totalDistanceMeters
                _remainingDurationSeconds.value = nextRoute.totalDurationSeconds
                _navigationState.value = NavigationState.NAVIGATING
                viewModelScope.launch {
                    _targetBuilding.value = buildingService.getBuildingAtLocation(nextRoute.destination)
                }
                voiceNavigator.announceNavigationStarted(nextRoute.steps.firstOrNull()?.instruction)
            }
        } else {
            // All delivery points completed
            _navigationState.value = NavigationState.ARRIVED
            voiceNavigator.speak("Tüm teslimat noktaları başarıyla tamamlandı!", isPriority = true)
        }
    }

    fun startNavigation() {
        val route = _currentRoute.value
        if (route == null) {
            createRoute()
            return
        }
        _navigationState.value = NavigationState.NAVIGATING
        _isCameraLocked.value = true
        _activeStepIndex.value = 0
        lastSpokenStepIndex = -1
        hasAnnouncedArrivalZone = false

        val firstStep = route.steps.firstOrNull()
        _currentStep.value = firstStep
        voiceNavigator.announceNavigationStarted(firstStep?.instruction)
    }

    fun stopNavigation() {
        locationTracker.stopSimulation()
        voiceNavigator.stop()
        _navigationState.value = if (_destination.value != null) NavigationState.DESTINATION_SELECTED else NavigationState.IDLE
        _isCameraLocked.value = true
        _shouldTriggerArrivalZoom.value = false
        hasAnnouncedArrivalZone = false
    }

    fun recenterCamera() {
        _isCameraLocked.value = true
    }

    fun onCameraManuallyMoved() {
        _isCameraLocked.value = false
    }

    fun toggleNightMode() {
        _isNightMode.value = !_isNightMode.value
    }

    fun toggleVoiceMute() {
        voiceNavigator.toggleMute()
    }

    fun toggleSimulation() {
        val route = _currentRoute.value ?: return
        if (locationTracker.isSimulating.value) {
            locationTracker.stopSimulation()
        } else {
            if (_navigationState.value != NavigationState.NAVIGATING) {
                startNavigation()
            }
            locationTracker.startRouteSimulation(route.coordinates)
        }
    }

    fun openSearchSheet() {
        _isSearchOpen.value = true
    }

    fun closeSearchSheet() {
        _isSearchOpen.value = false
        _searchResults.value = emptyList()
    }

    fun openMapboxInfoDialog() {
        _isMapboxInfoOpen.value = true
    }

    fun closeMapboxInfoDialog() {
        _isMapboxInfoOpen.value = false
    }

    /**
     * Loads a cached route from Room database to continue navigation when offline.
     */
    fun loadCachedRoute(entity: CachedRouteEntity) {
        viewModelScope.launch {
            val route = offlineRepository.findCachedRoute(
                origin = NavLocation(entity.originLatitude, entity.originLongitude),
                destination = NavLocation(entity.destLatitude, entity.destLongitude)
            )
            if (route != null) {
                _currentRoute.value = route
                _destination.value = route.destination
                _destinationTitle.value = route.destinationTitle
                _activeStepIndex.value = 0
                _currentStep.value = route.steps.firstOrNull()
                _remainingDistance.value = route.totalDistanceMeters
                _remainingDurationSeconds.value = route.totalDurationSeconds
                _navigationState.value = NavigationState.DESTINATION_SELECTED
                _fitRouteEvent.emit(route.coordinates)
                voiceNavigator.speak("Kayıtlı çevrimdışı rota yüklendi.")
            }
        }
    }

    private fun processLocationUpdate(loc: NavLocation) {
        val route = _currentRoute.value ?: return
        if (_navigationState.value != NavigationState.NAVIGATING) return

        val dest = route.destination
        val distToDest = loc.distanceTo(dest)

        // 1. Arrived (< 15 meters)
        if (distToDest < 15.0) {
            _navigationState.value = NavigationState.ARRIVED
            val houseNum = _targetBuilding.value?.houseNumber
            val entranceNote = if (_targetBuilding.value?.entranceLocation != null) "Ana kapı girişi haritada işaretlendi" else null
            voiceNavigator.announceArrival(houseNum, entranceNote)
            return
        }

        // 2. Entered close arrival zone (< 90 meters)
        if (distToDest < 90.0 && !hasAnnouncedArrivalZone) {
            hasAnnouncedArrivalZone = true
            _shouldTriggerArrivalZoom.value = true

            val houseNum = _targetBuilding.value?.houseNumber
            val arrivalPrompt = if (!houseNum.isNullOrBlank()) {
                "Hedefe yaklaşıyorsunuz. Dış kapı numarası $houseNum için yakın görünüme geçildi."
            } else {
                "Hedefe yaklaşıyorsunuz. Yakın görünüme geçildi."
            }
            voiceNavigator.speak(arrivalPrompt, isPriority = true)
        }

        // 3. Step advancement & Maneuver tracking
        val steps = route.steps
        val currentIndex = _activeStepIndex.value
        if (currentIndex < steps.size) {
            val currentStepObj = steps[currentIndex]
            val distToStep = loc.distanceTo(currentStepObj.location)
            _distanceToNextManeuver.value = distToStep
            _currentStep.value = currentStepObj

            if (distToStep < 180 && distToStep > 60 && lastSpokenStepIndex != currentIndex) {
                lastSpokenStepIndex = currentIndex
                voiceNavigator.announceManeuver(currentStepObj, distToStep)
            }

            if (distToStep < 20.0 && currentIndex + 1 < steps.size) {
                val nextIdx = currentIndex + 1
                _activeStepIndex.value = nextIdx
                _currentStep.value = steps[nextIdx]
                voiceNavigator.announceManeuver(steps[nextIdx], 0.0)
            }
        }

        // 4. Update remaining total distance
        _remainingDistance.value = distToDest

        // 5. Off-route detection: if distance to closest route segment > 45 meters, recalculate
        val minDistanceToPolyline = findMinDistanceToRoute(loc, route.coordinates)
        if (minDistanceToPolyline > 45.0 && !_isLoadingRoute.value) {
            voiceNavigator.announceReroute()
            createRoute()
        }
    }

    private fun findMinDistanceToRoute(loc: NavLocation, coords: List<NavLocation>): Double {
        if (coords.isEmpty()) return 0.0
        var minDistance = Double.MAX_VALUE
        for (i in 0 until coords.size - 1) {
            val dist = loc.distanceTo(coords[i])
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stopTracking()
        voiceNavigator.destroy()
    }
}
