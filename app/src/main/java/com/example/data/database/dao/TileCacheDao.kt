package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.CachedTileEntity

@Dao
interface TileCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTile(tile: CachedTileEntity)

    @Query("SELECT * FROM cached_tiles WHERE tileKey = :tileKey LIMIT 1")
    suspend fun getTile(tileKey: String): CachedTileEntity?

    @Query("UPDATE cached_tiles SET lastAccessedAt = :time WHERE tileKey = :tileKey")
    suspend fun updateAccessTime(tileKey: String, time: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM cached_tiles")
    suspend fun getTileCount(): Int

    @Query("DELETE FROM cached_tiles WHERE tileKey IN (SELECT tileKey FROM cached_tiles ORDER BY lastAccessedAt ASC LIMIT :count)")
    suspend fun evictOldestTiles(count: Int)

    @Query("DELETE FROM cached_tiles")
    suspend fun clearAll()
}
