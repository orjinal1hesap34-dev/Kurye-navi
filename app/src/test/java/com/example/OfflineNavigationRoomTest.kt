package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.entity.CachedRouteEntity
import com.example.data.database.entity.CachedTileEntity
import com.example.data.model.NavLocation
import com.example.data.model.RouteModel
import com.example.data.model.RouteStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineNavigationRoomTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndRetrieveCachedRoute() = runBlocking {
        val routeDao = db.routeCacheDao()

        val entity = CachedRouteEntity(
            routeKey = "40.990,29.029->40.995,29.035",
            originLatitude = 40.9904,
            originLongitude = 29.0292,
            destLatitude = 40.9950,
            destLongitude = 29.0350,
            destinationTitle = "Kadıköy Çarşı",
            destinationHouseNumber = "24",
            totalDistanceMeters = 850.0,
            totalDurationSeconds = 180.0,
            coordinatesJson = "[[29.0292,40.9904],[29.0350,40.9950]]",
            stepsJson = "[{\"instruction\":\"Düz devam edin\",\"maneuverType\":\"straight\",\"lat\":40.9904,\"lon\":29.0292,\"dist\":850,\"dur\":180,\"street\":\"Moda Cd\"}]",
            cachedAt = System.currentTimeMillis()
        )

        routeDao.insertRoute(entity)

        val retrieved = routeDao.getRouteByKey("40.990,29.029->40.995,29.035")
        assertNotNull(retrieved)
        assertEquals("Kadıköy Çarşı", retrieved?.destinationTitle)
        assertEquals("24", retrieved?.destinationHouseNumber)
        assertEquals(850.0, retrieved!!.totalDistanceMeters, 0.001)

        val recentList = routeDao.getAllRecentRoutes().first()
        assertEquals(1, recentList.size)
        assertEquals("Kadıköy Çarşı", recentList[0].destinationTitle)
    }

    @Test
    fun testNearbyCachedRouteLookup() = runBlocking {
        val routeDao = db.routeCacheDao()

        val entity = CachedRouteEntity(
            routeKey = "40.990,29.029->40.995,29.035",
            originLatitude = 40.9904,
            originLongitude = 29.0292,
            destLatitude = 40.9951,
            destLongitude = 29.0352,
            destinationTitle = "Moda Sahil",
            destinationHouseNumber = "15",
            totalDistanceMeters = 1200.0,
            totalDurationSeconds = 300.0,
            coordinatesJson = "[]",
            stepsJson = "[]"
        )
        routeDao.insertRoute(entity)

        // Lookup nearby with small bounding box
        val nearby = routeDao.findNearbyCachedRoute(
            destLatMin = 40.9940,
            destLatMax = 40.9960,
            destLonMin = 29.0340,
            destLonMax = 29.0360
        )
        assertNotNull(nearby)
        assertEquals("Moda Sahil", nearby?.destinationTitle)
    }

    @Test
    fun testInsertAndRetrieveTileCache() = runBlocking {
        val tileDao = db.tileCacheDao()

        val dummyTileData = byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00) // GZIP / PBF magic header
        val tileEntity = CachedTileEntity(
            tileKey = "https://api.mapbox.com/v4/mapbox.mapbox-streets-v8/14/9514/6132.vector.pbf",
            contentType = "application/x-protobuf",
            data = dummyTileData,
            etag = "W/\"abcd123\"",
            contentLength = dummyTileData.size.toLong()
        )

        tileDao.insertTile(tileEntity)

        val retrievedTile = tileDao.getTile("https://api.mapbox.com/v4/mapbox.mapbox-streets-v8/14/9514/6132.vector.pbf")
        assertNotNull(retrievedTile)
        assertEquals("application/x-protobuf", retrievedTile?.contentType)
        assertEquals(4, retrievedTile?.data?.size)
        assertEquals(1, tileDao.getTileCount())
    }
}
