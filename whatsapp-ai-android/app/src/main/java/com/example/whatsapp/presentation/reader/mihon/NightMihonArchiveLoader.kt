package com.example.whatsapp.presentation.reader.mihon

import android.content.Context
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MihonLoadedChapter(
    val mangaTitle: String,
    val chapterTitle: String,
    val pages: List<MihonPageSpec>,
    val progressKey: String,
)

object NightMihonArchiveLoader {
    private const val MAX_PAGES = 1_000
    private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 768L * 1024L * 1024L

    private val supportedExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp",
        "heic", "heif", "avif",
    )

    fun isSupportedArchive(
        fileName: String,
        mimeType: String? = null,
    ): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = mimeType.orEmpty().lowercase(Locale.ROOT)
        return ext == "cbz" ||
            ext == "zip" ||
            mime == "application/zip" ||
            mime == "application/x-cbz" ||
            mime == "application/vnd.comicbook+zip"
    }

    suspend fun load(
        context: Context,
        archivePath: String,
        displayName: String? = null,
    ): Result<MihonLoadedChapter> = withContext(Dispatchers.IO) {
        runCatching {
            val archive = File(archivePath)
            require(archive.exists() && archive.isFile) {
                "This manga archive is no longer available."
            }
            require(isSupportedArchive(archive.name)) {
                "Night's Mihon reader currently opens CBZ and ZIP manga archives."
            }

            val fingerprint = sha256(
                archive.canonicalPath + "|" + archive.length() + "|" + archive.lastModified(),
            )
            val root = File(context.cacheDir, "night_mihon_archives")
            val chapterDir = File(root, fingerprint)
            val marker = File(chapterDir, ".complete")

            if (!marker.exists()) {
                chapterDir.deleteRecursively()
                chapterDir.mkdirs()
                extractArchive(archive, chapterDir)
                marker.writeText(fingerprint)
            }

            var files = chapterDir
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.name != marker.name && isImageName(it.name) }
                .sortedWith { left, right -> naturalCompare(left.name, right.name) }

            if (files.isEmpty()) {
                chapterDir.deleteRecursively()
                chapterDir.mkdirs()
                extractArchive(archive, chapterDir)
                marker.writeText(fingerprint)
                files = chapterDir
                    .listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.name != marker.name && isImageName(it.name) }
                    .sortedWith { left, right -> naturalCompare(left.name, right.name) }
            }

            require(files.isNotEmpty()) {
                "No readable manga pages were found in this archive."
            }

            val cleanName = displayName
                ?.substringBeforeLast('.', displayName)
                ?.trim()
                .orEmpty()
                .ifBlank { archive.nameWithoutExtension }

            MihonLoadedChapter(
                mangaTitle = cleanName,
                chapterTitle = "",
                pages = files.mapIndexed { index, file ->
                    MihonPageSpec(index = index, source = file.absolutePath)
                },
                progressKey = "archive:$fingerprint",
            )
        }
    }

    private fun extractArchive(
        archive: File,
        outputDir: File,
    ) {
        ZipFile(archive).use { zip ->
            val entries = zip.entries().toList()
                .filter { !it.isDirectory && isImageName(it.name) }
                .sortedWith { left, right -> naturalCompare(left.name, right.name) }

            require(entries.isNotEmpty()) {
                "No readable manga pages were found in this archive."
            }
            require(entries.size <= MAX_PAGES) {
                "This archive has too many pages for one chapter."
            }

            var totalBytes = 0L
            entries.forEachIndexed { index, entry ->
                if (entry.size >= 0L) {
                    require(entry.size <= MAX_ENTRY_BYTES) {
                        "A manga page is too large to extract safely."
                    }
                }

                val ext = entry.name.substringAfterLast('.', "img").lowercase(Locale.ROOT)
                val target = File(
                    outputDir,
                    index.toString().padStart(5, '0') + "." + ext,
                )

                zip.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var entryBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            entryBytes += read.toLong()
                            totalBytes += read.toLong()
                            require(entryBytes <= MAX_ENTRY_BYTES) {
                                "A manga page is too large to extract safely."
                            }
                            require(totalBytes <= MAX_TOTAL_BYTES) {
                                "This manga archive expands to too much data."
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
    }

    private fun isImageName(name: String): Boolean =
        name.substringAfterLast('.', "")
            .lowercase(Locale.ROOT) in supportedExtensions

    private fun naturalCompare(
        left: String,
        right: String,
    ): Int {
        val leftParts = tokenRegex.findAll(left.lowercase(Locale.ROOT)).map { it.value }.toList()
        val rightParts = tokenRegex.findAll(right.lowercase(Locale.ROOT)).map { it.value }.toList()
        val count = minOf(leftParts.size, rightParts.size)

        for (index in 0 until count) {
            val a = leftParts[index]
            val b = rightParts[index]
            val cmp = if (a.firstOrNull()?.isDigit() == true && b.firstOrNull()?.isDigit() == true) {
                BigInteger(a).compareTo(BigInteger(b))
            } else {
                a.compareTo(b)
            }
            if (cmp != 0) return cmp
        }

        return leftParts.size.compareTo(rightParts.size).takeIf { it != 0 }
            ?: left.compareTo(right, ignoreCase = true)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private val tokenRegex = Regex("\\d+|\\D+")
}
