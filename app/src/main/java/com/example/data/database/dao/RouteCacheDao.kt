package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.CachedRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: CachedRouteEntity)

    @Query("SELECT * FROM cached_routes WHERE routeKey = :key LIMIT 1")
    suspend fun getRouteByKey(key: String): CachedRouteEntity?

    @Query("""
        SELECT * FROM cached_routes 
        WHERE destLatitude BETWEEN :destLatMin AND :destLatMax
          AND destLongitude BETWEEN :destLonMin AND :destLonMax
        ORDER BY cachedAt DESC
        LIMIT 1
    """)
    suspend fun findNearbyCachedRoute(
        destLatMin: Double,
        destLatMax: Double,
        destLonMin: Double,
        destLonMax: Double
    ): CachedRouteEntity?

    @Query("SELECT * FROM cached_routes ORDER BY cachedAt DESC LIMIT 25")
    fun getAllRecentRoutes(): Flow<List<CachedRouteEntity>>

    @Query("DELETE FROM cached_routes WHERE cachedAt < :thresholdTime")
    suspend fun deleteOldRoutes(thresholdTime: Long)

    @Query("DELETE FROM cached_routes")
    suspend fun clearAll()
}
