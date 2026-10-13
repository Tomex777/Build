package reader.shared.android.coil

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder

/** One Coil loader for local reader pages, with Mihon's BufferedSource input boundary. */
internal object ReaderImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context.applicationContext)
            .components {
                add(BufferedSourceFetcher.Factory())
                // Keep animated pages active through the custom random-access source boundary.
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
            .also { instance = it }
    }
}
