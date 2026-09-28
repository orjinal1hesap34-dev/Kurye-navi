package com.example.data

import android.content.Context
import com.example.BuildConfig
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

object MapboxConfig {
    /**
     * Reads Mapbox Public Access Token from BuildConfig (injected via .env / Secrets panel)
     */
    val accessToken: String
        get() = try {
            BuildConfig.MAPBOX_ACCESS_TOKEN.trim()
        } catch (e: Exception) {
            ""
        }

    val isConfigured: Boolean
        get() = accessToken.isNotBlank() &&
                accessToken.startsWith("pk.") &&
                !accessToken.contains("placeholder", ignoreCase = true) &&
                accessToken.length > 25

    /**
     * Initializes MapLibre with the appropriate TileServer and API Key
     */
    fun initMapLibre(context: Context) {
        try {
            if (isConfigured) {
                MapLibre.getInstance(context, accessToken, WellKnownTileServer.Mapbox)
            } else {
                MapLibre.getInstance(context, null, WellKnownTileServer.MapLibre)
            }
        } catch (e: UnsatisfiedLinkError) {
            // JVM / Robolectric environment guard
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Returns the appropriate vector map style URL.
     * When Mapbox token is configured, uses Mapbox Streets v12.
     * Otherwise, uses Carto vector tiles with full road network and building data.
     */
    fun getStyleUrl(isNightMode: Boolean = false, forceFallback: Boolean = false): String {
        return if (isConfigured && !forceFallback) {
            "https://api.mapbox.com/styles/v1/mapbox/${if (isNightMode) "navigation-night-v1" else "streets-v12"}?access_token=$accessToken"
        } else {
            if (isNightMode) {
                "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
            } else {
                "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"
            }
        }
    }
}
