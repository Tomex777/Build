package com.example.whatsapp.presentation.reader.mihon

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NightMihonPageActions {
    suspend fun resolvePageUri(
        context: Context,
        page: MihonPageSpec,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            MihonPageResolver.resolve(
                context.applicationContext,
                page,
            ).uri
        }
    }

    fun setAsCover(
        context: Context,
        readerKey: String,
        page: MihonPageSpec,
    ) {
        context.getSharedPreferences(
            "night_mihon_reader",
            Context.MODE_PRIVATE,
        )
            .edit()
            .putString(
                "cover:" + readerKey,
                page.source,
            )
            .apply()
    }

    fun copyPage(
        context: Context,
        uri: Uri,
    ) {
        val shareUri = toShareUri(context, uri)
        val clipboard =
            context.getSystemService(
                ClipboardManager::class.java,
            )
        clipboard.setPrimaryClip(
            ClipData.newUri(
                context.contentResolver,
                "Manga page",
                shareUri,
            ),
        )
    }

    fun sharePage(
        context: Context,
        uri: Uri,
    ) {
        val shareUri = toShareUri(context, uri)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("image/*")
                    .putExtra(Intent.EXTRA_STREAM, shareUri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share page",
            ),
        )
    }

    suspend fun savePage(
        context: Context,
        page: MihonPageSpec,
        pageNumber: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resolved =
                MihonPageResolver.resolve(
                    context.applicationContext,
                    page,
                )
            val extension =
                extensionFor(page.source)
            val mime =
                mimeFor(extension)
            val fileName =
                "Night-manga-" +
                    pageNumber.toString().padStart(3, '0') +
                    "." +
                    extension

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveModern(
                    context,
                    resolved.uri,
                    fileName,
                    mime,
                )
            } else {
                require(
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED,
                ) {
                    "Storage permission is required to save manga pages on this Android version."
                }
                saveLegacy(
                    context,
                    resolved.uri,
                    fileName,
                )
            }
        }
    }

    private fun saveModern(
        context: Context,
        source: Uri,
        fileName: String,
        mime: String,
    ) {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    fileName,
                )
                put(
                    MediaStore.Images.Media.MIME_TYPE,
                    mime,
                )
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES +
                        "/Night/Manga",
                )
                put(
                    MediaStore.Images.Media.IS_PENDING,
                    1,
                )
            }

        val target =
            requireNotNull(
                resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values,
                ),
            ) {
                "Could not create a Gallery entry for this page."
            }

        try {
            resolver.openInputStream(source).use { input ->
                requireNotNull(input) {
                    "Could not read this manga page."
                }
                resolver.openOutputStream(target).use { output ->
                    requireNotNull(output) {
                        "Could not write this manga page."
                    }
                    input.copyTo(output)
                }
            }
            values.clear()
            values.put(
                MediaStore.Images.Media.IS_PENDING,
                0,
            )
            resolver.update(target, values, null, null)
        } catch (failure: Throwable) {
            resolver.delete(target, null, null)
            throw failure
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        context: Context,
        source: Uri,
        fileName: String,
    ) {
        val directory =
            File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES,
                ),
                "Night/Manga",
            ).apply { mkdirs() }

        val output = File(directory, fileName)
        context.contentResolver
            .openInputStream(source)
            .use { input ->
                requireNotNull(input) {
                    "Could not read this manga page."
                }
                output.outputStream().use {
                    input.copyTo(it)
                }
            }

        context.sendBroadcast(
            Intent(
                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.fromFile(output),
            ),
        )
    }

    private fun toShareUri(
        context: Context,
        uri: Uri,
    ): Uri =
        if (uri.scheme == "file") {
            FileProvider.getUriForFile(
                context,
                context.packageName + ".files",
                File(requireNotNull(uri.path)),
            )
        } else {
            uri
        }

    private fun extensionFor(source: String): String =
        source
            .substringBefore('?')
            .substringAfterLast('.', "jpg")
            .lowercase(Locale.ROOT)
            .takeIf {
                it in setOf(
                    "jpg",
                    "jpeg",
                    "png",
                    "webp",
                    "gif",
                    "bmp",
                    "heic",
                    "heif",
                    "avif",
                )
            }
            ?: "jpg"

    private fun mimeFor(extension: String): String =
        when (extension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "avif" -> "image/avif"
            else -> "image/jpeg"
        }
}
