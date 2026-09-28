package com.example.ui.map

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.data.MapboxConfig
import com.example.data.model.BuildingInfo
import com.example.data.model.NavLocation
import com.example.data.model.NavigationState
import com.example.data.model.RouteModel
import kotlinx.coroutines.flow.SharedFlow
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val ROUTE_SOURCE_ID = "route-source"
private const val ROUTE_CASING_LAYER = "route-casing-layer"
private const val ROUTE_INNER_LAYER = "route-inner-layer"

private const val BUILDING_SOURCE_ID = "building-source"
private const val BUILDING_FILL_LAYER = "building-fill-layer"
private const val BUILDING_STROKE_LAYER = "building-stroke-layer"

private const val ENTRANCE_SOURCE_ID = "entrance-source"
private const val ENTRANCE_CIRCLE_LAYER = "entrance-circle-layer"
private const val ENTRANCE_LABEL_LAYER = "entrance-label-layer"

private const val HOUSE_NUM_SOURCE_ID = "house-num-source"
private const val HOUSE_NUM_LAYER = "house-num-layer"

private const val USER_PUCK_SOURCE_ID = "user-puck-source"
private const val USER_PUCK_OUTER_LAYER = "user-puck-outer-layer"
private const val USER_PUCK_INNER_LAYER = "user-puck-inner-layer"

private const val DESTINATION_SOURCE_ID = "destination-source"
private const val DESTINATION_CIRCLE_LAYER = "destination-circle-layer"

/**
 * MapLibre map component that initializes using the API key from BuildConfig,
 * enabling the visualization of the map interface for the courier.
 */
@Composable
fun MapLibreMapComponent(
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
    modifier: Modifier = Modifier,
    apiKey: String = try { BuildConfig.MAPBOX_ACCESS_TOKEN.trim() } catch (e: Exception) { "" }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapLibre engine using the API key from BuildConfig
    remember(apiKey) {
        try {
            val isKeyConfigured = apiKey.isNotBlank() &&
                    apiKey.startsWith("pk.") &&
                    !apiKey.contains("placeholder", ignoreCase = true)

            if (isKeyConfigured) {
                MapLibre.getInstance(context, apiKey, WellKnownTileServer.Mapbox)
                MapLibre.setApiKey(apiKey)
            } else {
                MapLibre.getInstance(context, null, WellKnownTileServer.MapLibre)
            }
        } catch (e: UnsatisfiedLinkError) {
            // Guard against JVM unit test environments without native libraries
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }
    val mapInstance = remember { mutableStateOf<MapLibreMap?>(null) }

    // Bind MapView lifecycle to Android Activity / Compose lifecycle
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // Resolve Style URL dynamically based on theme and token availability
    val resolveStyleUrl = {
        val hasValidKey = apiKey.isNotBlank() &&
                apiKey.startsWith("pk.") &&
                !apiKey.contains("placeholder", ignoreCase = true)

        if (hasValidKey && !forceFallbackStyle) {
            val styleName = if (isNightMode) "navigation-night-v1" else "streets-v12"
            "https://api.mapbox.com/styles/v1/mapbox/$styleName?access_token=$apiKey"
        } else {
            if (isNightMode) {
                "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
            } else {
                "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"
            }
        }
    }

    // Reload style when Day/Night mode or Fallback mode changes
    LaunchedEffect(isNightMode, forceFallbackStyle, apiKey) {
        val styleUrl = resolveStyleUrl()
        mapInstance.value?.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            setupMapLayers(style)
            customizeHousenumLayer(style)
            onMapStyleLoaded()
        }
    }

    // Camera Bounding Box Fitting when a new route is calculated
    LaunchedEffect(fitRouteEvent) {
        fitRouteEvent.collect { coords ->
            val map = mapInstance.value ?: return@collect
            if (coords.size >= 2) {
                try {
                    val boundsBuilder = LatLngBounds.Builder()
                    coords.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120), 1200)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Update active route polylines
    LaunchedEffect(currentRoute, mapInstance.value) {
        val map = mapInstance.value ?: return@LaunchedEffect
        map.getStyle { style ->
            updateRouteLayer(style, currentRoute)
        }
    }

    // Update building polygons and entrance points
    LaunchedEffect(targetBuilding, destination, mapInstance.value) {
        val map = mapInstance.value ?: return@LaunchedEffect
        map.getStyle { style ->
            updateBuildingLayers(style, targetBuilding, destination)
        }
    }

    // Update courier user location puck
    LaunchedEffect(currentLocation, mapInstance.value) {
        val map = mapInstance.value ?: return@LaunchedEffect
        map.getStyle { style ->
            updateUserPuck(style, currentLocation)
        }
    }

    // Automatic camera tracking & arrival auto-zoom
    LaunchedEffect(currentLocation, isCameraLocked, shouldTriggerArrivalZoom, navigationState) {
        val map = mapInstance.value ?: return@LaunchedEffect
        if (!isCameraLocked) return@LaunchedEffect

        val targetLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
        val cameraPositionBuilder = CameraPosition.Builder().target(targetLatLng)

        when {
            shouldTriggerArrivalZoom -> {
                cameraPositionBuilder
                    .zoom(18.5)
                    .tilt(45.0)
                    .bearing(currentLocation.bearing.toDouble())
            }
            navigationState == NavigationState.NAVIGATING -> {
                cameraPositionBuilder
                    .zoom(17.5)
                    .tilt(38.0)
                    .bearing(currentLocation.bearing.toDouble())
            }
            else -> {
                cameraPositionBuilder
                    .zoom(15.5)
                    .tilt(0.0)
            }
        }

        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPositionBuilder.build()), 1000)
    }

    AndroidView(
        factory = {
            mapView.apply {
                addOnDidFailLoadingMapListener { errorMessage ->
                    onMapLoadFailed(errorMessage ?: "Harita verisi indirilemedi")
                }
                addOnDidFinishLoadingStyleListener {
                    onMapStyleLoaded()
                }

                getMapAsync { map ->
                    mapInstance.value = map

                    map.uiSettings.isCompassEnabled = true
                    map.uiSettings.isRotateGesturesEnabled = true
                    map.uiSettings.isTiltGesturesEnabled = true
                    map.uiSettings.isZoomGesturesEnabled = true
                    map.uiSettings.isScrollGesturesEnabled = true

                    val initialLat = if (currentLocation.latitude != 0.0) currentLocation.latitude else 40.9904
                    val initialLon = if (currentLocation.longitude != 0.0) currentLocation.longitude else 29.0292

                    val initPos = CameraPosition.Builder()
                        .target(LatLng(initialLat, initialLon))
                        .zoom(15.5)
                        .build()
                    map.cameraPosition = initPos

                    val styleUrl = resolveStyleUrl()
                    map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                        setupMapLayers(style)
                        customizeHousenumLayer(style)
                        onMapStyleLoaded()
                    }

                    map.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            onCameraManualMove()
                        }
                    }

                    map.addOnMapLongClickListener { point ->
                        onMapLongClick(point)
                        true
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

private fun setupMapLayers(style: Style) {
    // 1. Route layers
    if (style.getSource(ROUTE_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))

        val routeCasing = LineLayer(ROUTE_CASING_LAYER, ROUTE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#0F172A")),
                PropertyFactory.lineWidth(8.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        style.addLayer(routeCasing)

        val routeInner = LineLayer(ROUTE_INNER_LAYER, ROUTE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#2563EB")),
                PropertyFactory.lineWidth(5.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        style.addLayer(routeInner)
    }

    // 2. Target Building polygon layers
    if (style.getSource(BUILDING_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(BUILDING_SOURCE_ID))

        val buildingFill = FillLayer(BUILDING_FILL_LAYER, BUILDING_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.fillColor(Color.parseColor("#F59E0B")),
                PropertyFactory.fillOpacity(0.48f)
            )
        }
        style.addLayer(buildingFill)

        val buildingStroke = LineLayer(BUILDING_STROKE_LAYER, BUILDING_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#D97706")),
                PropertyFactory.lineWidth(3.5f)
            )
        }
        style.addLayer(buildingStroke)
    }

    // 3. Entrance door point layer
    if (style.getSource(ENTRANCE_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(ENTRANCE_SOURCE_ID))

        val entranceCircle = CircleLayer(ENTRANCE_CIRCLE_LAYER, ENTRANCE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.circleRadius(8.5f),
                PropertyFactory.circleColor(Color.parseColor("#10B981")),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        }
        style.addLayer(entranceCircle)

        val entranceLabel = SymbolLayer(ENTRANCE_LABEL_LAYER, ENTRANCE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.textField("🚪 GİRİŞ"),
                PropertyFactory.textSize(12.5f),
                PropertyFactory.textColor(Color.WHITE),
                PropertyFactory.textHaloColor(Color.BLACK),
                PropertyFactory.textHaloWidth(2.0f),
                PropertyFactory.textOffset(arrayOf(0f, -1.8f)),
                PropertyFactory.textAllowOverlap(true)
            )
        }
        style.addLayer(entranceLabel)
    }

    // 4. House Number layer
    if (style.getSource(HOUSE_NUM_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(HOUSE_NUM_SOURCE_ID))

        val houseNumLayer = SymbolLayer(HOUSE_NUM_LAYER, HOUSE_NUM_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.textField(get("house_num")),
                PropertyFactory.textSize(16f),
                PropertyFactory.textColor(Color.WHITE),
                PropertyFactory.textHaloColor(Color.parseColor("#B45309")),
                PropertyFactory.textHaloWidth(3.0f),
                PropertyFactory.textAllowOverlap(false)
            )
        }
        style.addLayer(houseNumLayer)
    }

    // 5. Destination Marker layer
    if (style.getSource(DESTINATION_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(DESTINATION_SOURCE_ID))

        val destCircle = CircleLayer(DESTINATION_CIRCLE_LAYER, DESTINATION_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(Color.parseColor("#EF4444")),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        }
        style.addLayer(destCircle)
    }

    // 6. User Location Puck
    if (style.getSource(USER_PUCK_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(USER_PUCK_SOURCE_ID))

        val userPuckOuter = CircleLayer(USER_PUCK_OUTER_LAYER, USER_PUCK_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.circleRadius(12f),
                PropertyFactory.circleColor(Color.parseColor("#0284C7")),
                PropertyFactory.circleOpacity(0.35f)
            )
        }
        style.addLayer(userPuckOuter)

        val userPuckInner = CircleLayer(USER_PUCK_INNER_LAYER, USER_PUCK_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(Color.parseColor("#0284C7")),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        }
        style.addLayer(userPuckInner)
    }
}

private fun customizeHousenumLayer(style: Style) {
    try {
        val houseLayer = style.getLayer("housenum_label") as? SymbolLayer
        houseLayer?.setProperties(
            PropertyFactory.textSize(15f),
            PropertyFactory.textColor(Color.parseColor("#0F172A")),
            PropertyFactory.textHaloColor(Color.WHITE),
            PropertyFactory.textHaloWidth(2.5f),
            PropertyFactory.textAllowOverlap(false)
        )
    } catch (e: Exception) {
        // Layer not present in this style, fallback layers handle house numbers
    }
}

private fun updateRouteLayer(style: Style, route: RouteModel?) {
    val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) ?: return
    if (route != null && route.coordinates.size >= 2) {
        val points = route.coordinates.map { Point.fromLngLat(it.longitude, it.latitude) }
        val lineString = LineString.fromLngLats(points)
        source.setGeoJson(Feature.fromGeometry(lineString))
    } else {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }
}

private fun updateBuildingLayers(style: Style, building: BuildingInfo?, destination: NavLocation?) {
    val bSource = style.getSourceAs<GeoJsonSource>(BUILDING_SOURCE_ID)
    val eSource = style.getSourceAs<GeoJsonSource>(ENTRANCE_SOURCE_ID)
    val hSource = style.getSourceAs<GeoJsonSource>(HOUSE_NUM_SOURCE_ID)
    val dSource = style.getSourceAs<GeoJsonSource>(DESTINATION_SOURCE_ID)

    if (destination != null) {
        dSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(destination.longitude, destination.latitude)))
    } else {
        dSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    if (building != null && building.polygon.size >= 3) {
        val polygonPoints = building.polygon.map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList()
        if (polygonPoints.first() != polygonPoints.last()) {
            polygonPoints.add(polygonPoints.first())
        }
        val polygon = Polygon.fromLngLats(listOf(polygonPoints))
        bSource?.setGeoJson(Feature.fromGeometry(polygon))

        if (building.entranceLocation != null) {
            val entPt = Point.fromLngLat(building.entranceLocation.longitude, building.entranceLocation.latitude)
            eSource?.setGeoJson(Feature.fromGeometry(entPt))
        } else {
            eSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        }

        if (!building.houseNumber.isNullOrBlank()) {
            val center = building.centroid
            val centerPt = Point.fromLngLat(center.longitude, center.latitude)
            val feature = Feature.fromGeometry(centerPt).apply {
                addStringProperty("house_num", "NO: " + building.houseNumber)
            }
            hSource?.setGeoJson(feature)
        } else {
            hSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        }
    } else {
        bSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        eSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        hSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }
}

private fun updateUserPuck(style: Style, loc: NavLocation) {
    val source = style.getSourceAs<GeoJsonSource>(USER_PUCK_SOURCE_ID) ?: return
    val pt = Point.fromLngLat(loc.longitude, loc.latitude)
    source.setGeoJson(Feature.fromGeometry(pt))
}
