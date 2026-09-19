package com.example.whatsapp.data.night

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NightChatEntity::class,
        NightMessageEntity::class,
        NightSummaryCheckpointEntity::class,
        NightLibraryItemEntity::class,
        NightProviderProfileEntity::class,
        NightProviderModelEntity::class,
        NightCapabilityRouteEntity::class,
        NightAppearanceEntity::class,
        NightProfileEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class NightDatabase : RoomDatabase() {
    abstract fun nightDao(): NightDao

    companion object {
        @Volatile private var instance: NightDatabase? = null

        fun get(context: Context): NightDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NightDatabase::class.java,
                    "night.db",
                ).build().also { instance = it }
            }
    }
}
