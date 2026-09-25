package app.nami.android

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import app.nami.compat.aniyomi.AniyomiExtensionHost
import app.nami.compat.aniyomi.AniyomiExtensionRegistry
import app.nami.runtime.CachingNamiSourceRegistry

class NamiApplication : Application() {

    lateinit var sourceRegistry: CachingNamiSourceRegistry
        private set

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            sourceRegistry.invalidate()
        }
    }

    override fun onCreate() {
        super.onCreate()

        AniyomiExtensionHost.initialize(this)
        sourceRegistry = CachingNamiSourceRegistry(
            delegate = AniyomiExtensionRegistry(this),
            ttlMillis = SOURCE_SNAPSHOT_TTL_MILLIS,
        )

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                packageChangeReceiver,
                packageFilter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageChangeReceiver, packageFilter)
        }
    }

    companion object {
        private const val SOURCE_SNAPSHOT_TTL_MILLIS = 60_000L
    }
}
