package com.tomex777.annie

import android.content.Context
import android.content.Intent
import android.content.ClipData
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Each share owns immutable bytes, so a later share cannot replace its contents. */
internal fun studioShareIntent(context: Context, name: String, source: String): Intent {
    val baseName = name.substringAfterLast('/').substringAfterLast('\\')
    require(baseName.isNotBlank() && baseName != "." && baseName != "..") { "Invalid filename" }
    val directory = File(context.cacheDir, "shared-scripts/${UUID.randomUUID()}")
    check(directory.mkdirs()) { "Couldn't prepare shared file" }
    val file = File(directory, baseName).apply { writeText(source) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.script-files", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = when (baseName.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "css" -> "text/css"
            "js" -> "text/javascript"
            "json" -> "application/json"
            else -> "text/plain"
        }
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, baseName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
