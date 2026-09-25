package app.nami.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import app.nami.domain.ResolvedMedia

/**
 * Keeps external-player handoff at the Android boundary. Source adapters only resolve media;
 * they never know about Android intents or player packages.
 */
object ExternalPlayerLauncher {

    fun buildIntent(media: ResolvedMedia): Intent {
        val uri = Uri.parse(media.url)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, media.mimeType ?: "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (media.headers.isNotEmpty()) {
                val headers = Bundle().apply {
                    media.headers.forEach { (name, value) ->
                        if (name.isNotBlank() && value.isNotBlank()) {
                            putString(name, value)
                        }
                    }
                }
                putExtra(Browser.EXTRA_HEADERS, headers)
            }

            media.headers.entries
                .firstOrNull { it.key.equals("referer", ignoreCase = true) }
                ?.value
                ?.takeIf { it.isNotBlank() }
                ?.let { putExtra("http-referrer", it) }

            media.headers.entries
                .firstOrNull { it.key.equals("user-agent", ignoreCase = true) }
                ?.value
                ?.takeIf { it.isNotBlank() }
                ?.let { putExtra("user-agent", it) }

            media.subtitles.firstOrNull()?.url?.takeIf { it.isNotBlank() }?.let {
                putExtra("subtitles_location", it)
            }
        }
    }

    fun open(context: Context, media: ResolvedMedia) {
        context.startActivity(
            Intent.createChooser(buildIntent(media), "Play with"),
        )
    }
}
