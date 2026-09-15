package com.night.keyboard.data.clipboard

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.night.keyboard.model.ClipboardItem
import com.night.keyboard.model.ClipboardKind

@Entity(tableName = "clipboard_items")
data class ClipboardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val kind: ClipboardKind,
    val createdAt: Long,
    val expiresAt: Long?,
    val pinned: Boolean,
    val orderIndex: Long,
) {
    fun toModel(): ClipboardItem = ClipboardItem(id, text, kind, createdAt, expiresAt, pinned, orderIndex)
}
