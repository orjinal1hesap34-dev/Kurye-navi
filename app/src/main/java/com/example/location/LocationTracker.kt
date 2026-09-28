package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.example.data.model.NavLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationTracker(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Default starting point: Istanbul Kadıköy (popular courier hub) if GPS not yet fixed
    private val _currentLocation = MutableStateFlow(
        NavLocation(
            latitude = 40.9904,
            longitude = 29.0292,
            bearing = 45f,
            speedKmh = 0f,
            accuracy = 10f
        )
    )
    val currentLocation: StateFlow<NavLocation> = _currentLocation.asStateFlow()

    private val _isGpsActive = MutableStateFlow(false)
    val isGpsActive: StateFlow<Boolean> = _isGpsActive.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (_isSimulating.value) return // Don't override simulation if running
            val location: Location = result.lastLocation ?: return
            _isGpsActive.value = true
            _currentLocation.value = NavLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                bearing = if (location.hasBearing()) location.bearing else _currentLocation.value.bearing,
                speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f,
                accuracy = if (location.hasAccuracy()) location.accuracy else 10f,
                timestamp = location.time
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500)
                .setMinUpdateIntervalMillis(1000)
                .setMinUpdateDistanceMeters(1f)
                .build()

            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Try getting last known location immediately
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && !_isSimulating.value) {
                    _isGpsActive.value = true
                    _currentLocation.value = NavLocation(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        bearing = if (loc.hasBearing()) loc.bearing else 0f,
                        speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f,
                        accuracy = if (loc.hasAccuracy()) loc.accuracy else 10f
                    )
                }
            }
        } catch (e: SecurityException) {
            _isGpsActive.value = false
        }
    }

    fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        _isGpsActive.value = false
    }

    /**
     * Simulates courier driving smoothly along route coordinates (ideal for emulator or indoor testing)
     */
    fun startRouteSimulation(routeCoordinates: List<NavLocation>, onStepPassed: (NavLocation) -> Unit = {}) {
        if (routeCoordinates.isEmpty()) return
        stopSimulation()
        _isSimulating.value = true

        simulationJob = scope.launch {
            var currentIndex = 0
            while (isActive && currentIndex < routeCoordinates.size) {
                val pt = routeCoordinates[currentIndex]
                val nextPt = if (currentIndex + 1 < routeCoordinates.size) routeCoordinates[currentIndex + 1] else pt
                val bearing = pt.bearingTo(nextPt)

                _currentLocation.value = NavLocation(
                    latitude = pt.latitude,
                    longitude = pt.longitude,
                    bearing = bearing,
                    speedKmh = 35f, // ~35 km/h urban courier scooter speed
                    accuracy = 4f
                )
                onStepPassed(pt)
                currentIndex++
                delay(1200) // update step every 1.2s
            }
            _isSimulating.value = false
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _isSimulating.value = false
    }
}
