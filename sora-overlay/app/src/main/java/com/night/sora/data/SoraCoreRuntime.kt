package com.night.sora.data

import android.content.Context
import com.night.sora.download.MusicDownloadManager
import com.night.sora.extension.ExtensionManager

/**
 * One set of app-owned state and music transfers per Android process.
 *
 * A Compose Activity may be recreated during a download (rotation, theme
 * change or Android's "Don't keep activities" developer option). If the
 * repository or download worker were only remembered by that Activity, the
 * previous worker would keep writing to a stale repository and another worker
 * could overwrite the same partial file. This runtime keeps both alive until
 * the process itself ends.
 */
object SoraCoreRuntime {
    private var repositoryInstance: CoreRepository? = null
    private var extensionsInstance: ExtensionManager? = null
    private var downloadsInstance: MusicDownloadManager? = null

    @Synchronized
    fun repository(context: Context): CoreRepository =
        repositoryInstance ?: CoreRepository(context.applicationContext).also {
            repositoryInstance = it
        }

    @Synchronized
    fun extensions(context: Context): ExtensionManager =
        extensionsInstance ?: ExtensionManager(context.applicationContext).also {
            extensionsInstance = it
        }

    @Synchronized
    fun downloads(context: Context): MusicDownloadManager =
        downloadsInstance ?: MusicDownloadManager(
            context.applicationContext,
            repository(context),
            extensions(context),
        ).also { downloadsInstance = it }
}
