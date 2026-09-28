package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing cached vector map tiles, style specifications,
 * and basemap assets to render map graphics when offline.
 */
@Entity(
    tableName = "cached_tiles",
    indices = [
        Index(value = ["tileKey"], unique = true),
        Index(value = ["lastAccessedAt"])
    ]
)
data class CachedTileEntity(
    @PrimaryKey
    val tileKey: String, // URL or hash of the tile request
    val contentType: String,
    val data: ByteArray,
    val etag: String? = null,
    val contentLength: Long = 0L,
    val lastAccessedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CachedTileEntity
        if (tileKey != other.tileKey) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tileKey.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
