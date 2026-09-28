package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.database.dao.RouteCacheDao
import com.example.data.database.dao.TileCacheDao
import com.example.data.database.entity.CachedRouteEntity
import com.example.data.database.entity.CachedTileEntity

/**
 * Main Room database providing persistent local offline cache for map tiles
 * and courier navigation routes.
 */
@Database(
    entities = [
        CachedRouteEntity::class,
        CachedTileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun routeCacheDao(): RouteCacheDao
    abstract fun tileCacheDao(): TileCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kurye_offline_navigation.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
