package com.night.keyboard.data.di

import android.content.Context
import androidx.room.Room
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

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): KeyboardDatabase =
        Room.databaseBuilder(context, KeyboardDatabase::class.java, "keyboard.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun clipboardDao(db: KeyboardDatabase): ClipboardDao = db.clipboardDao()
    @Provides fun themeDao(db: KeyboardDatabase): ThemeDao = db.themeDao()
    @Provides fun learnedWordDao(db: KeyboardDatabase): LearnedWordDao = db.learnedWordDao()
}
