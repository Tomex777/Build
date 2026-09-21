package com.night.keyboard.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.night.keyboard.data.clipboard.ClipboardDao
import com.night.keyboard.data.clipboard.KeyboardDatabase
import com.night.keyboard.data.prediction.LearnedWordDao
import com.night.keyboard.data.theme.ThemeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS learned_words (
                word TEXT NOT NULL PRIMARY KEY,
                frequency INTEGER NOT NULL,
                lastUsed INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): KeyboardDatabase =
        Room.databaseBuilder(context, KeyboardDatabase::class.java, "keyboard.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun clipboardDao(db: KeyboardDatabase): ClipboardDao = db.clipboardDao()

    @Provides
    fun themeDao(db: KeyboardDatabase): ThemeDao = db.themeDao()

    @Provides
    fun learnedWordDao(db: KeyboardDatabase): LearnedWordDao = db.learnedWordDao()
}
