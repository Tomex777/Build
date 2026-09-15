package com.night.keyboard.data.clipboard

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.night.keyboard.data.theme.ThemeDao
import com.night.keyboard.data.theme.ThemeEntity
import com.night.keyboard.model.ClipboardKind

@Database(entities = [ClipboardEntity::class, ThemeEntity::class], version = 1, exportSchema = true)
@TypeConverters(KeyboardConverters::class)
abstract class KeyboardDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
    abstract fun themeDao(): ThemeDao
}

class KeyboardConverters {
    @TypeConverter fun clipboardKindToString(value: ClipboardKind): String = value.name
    @TypeConverter fun stringToClipboardKind(value: String): ClipboardKind = ClipboardKind.valueOf(value)
}
