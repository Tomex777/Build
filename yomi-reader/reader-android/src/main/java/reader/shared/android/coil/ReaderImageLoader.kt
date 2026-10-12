package reader.shared.android.coil

import android.content.Context
import coil3.ImageLoader

/** One Coil loader for local reader pages, with Mihon's BufferedSource input boundary. */
internal object ReaderImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context.applicationContext)
            .components { add(BufferedSourceFetcher.Factory()) }
            .build()
            .also { instance = it }
    }
}
