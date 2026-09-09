package com.night.mirrorchess.data

import android.util.AtomicFile
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/** Process-death-safe UTF-8 storage backed by Android's rollback-capable AtomicFile. */
internal class AtomicTextFile(file: File) {
    private val storage = AtomicFile(file)

    fun readTextOrNull(): String? {
        val backup = File(storage.baseFile.path + ".bak")
        if (!storage.baseFile.isFile && !backup.isFile) return null
        return storage.openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    fun writeText(text: String) {
        val output = storage.startWrite()
        try {
            val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)
            writer.write(text)
            writer.flush()
            storage.finishWrite(output)
        } catch (error: Throwable) {
            storage.failWrite(output)
            throw error
        }
    }

    fun delete() = storage.delete()
}
