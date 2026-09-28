package com.example.data.database

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * OkHttp Interceptor that intercepts MapLibre / Mapbox tile and style requests,
 * storing successful responses into Room database and serving cached tiles
 * when the courier has poor or lost internet connectivity.
 */
class OfflineTileCacheInterceptor(
    private val repository: OfflineNavigationRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // Determine if this is a map tile, style, glyph, or sprite resource
        val isMapResource = url.contains(".pbf") ||
                url.contains(".mvt") ||
                url.contains(".json") ||
                url.contains(".png") ||
                url.contains("/styles/v1") ||
                url.contains("/tiles/") ||
                url.contains("cartocdn.com") ||
                url.contains("mapbox.com")

        if (!isMapResource) {
            return chain.proceed(request)
        }

        try {
            val response = chain.proceed(request)

            if (response.isSuccessful && response.body != null) {
                val contentType = response.header("Content-Type") ?: "application/octet-stream"
                val etag = response.header("ETag")
                val bytes = response.peekBody(10 * 1024 * 1024).bytes() // Peek up to 10MB safely

                // Cache in Room in background
                runBlocking {
                    repository.cacheTile(
                        url = url,
                        data = bytes,
                        contentType = contentType,
                        etag = etag
                    )
                }
            }

            return response
        } catch (e: IOException) {
            // Internet is lost or timeout occurred — attempt to serve from Room database
            val cachedTile = runBlocking {
                repository.getCachedTile(url)
            }

            if (cachedTile != null) {
                val mediaType = cachedTile.contentType.toMediaTypeOrNull()
                val responseBody = cachedTile.data.toResponseBody(mediaType)

                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK (Served from Room Offline Tile Cache)")
                    .header("Content-Type", cachedTile.contentType)
                    .header("X-Offline-Cached", "true")
                    .body(responseBody)
                    .build()
            }

            // Propagate exception if not found in cache
            throw e
        }
    }
}
