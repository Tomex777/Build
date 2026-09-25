package app.nami.android

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import app.nami.compat.aniyomi.AniyomiExtensionRegistry
import app.nami.data.local.NamiDatabase
import app.nami.runtime.CachingNamiSourceRegistry
import app.nami.runtime.EnabledNamiSourceRegistry
import app.nami.runtime.NamiSourceRegistry

class NamiApplication : Application() {

    lateinit var installedSourceRegistry: CachingNamiSourceRegistry
        private set

    lateinit var sourceEnablementStore: NamiSourceEnablementStore
        private set

    lateinit var sourceRegistry: NamiSourceRegistry
        private set

    lateinit var database: NamiDatabase
        private set

    lateinit var downloadManager: NamiDownloadManager
        private set

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            installedSourceRegistry.invalidate()
        }
    }

    override fun onCreate() {
        super.onCreate()

        installedSourceRegistry = CachingNamiSourceRegistry(
            delegate = AniyomiExtensionRegistry(this),
            ttlMillis = SOURCE_SNAPSHOT_TTL_MILLIS,
        )
        sourceEnablementStore = NamiSourceEnablementStore(this)
        sourceRegistry = EnabledNamiSourceRegistry(
            installedRegistry = installedSourceRegistry,
            enablementStore = sourceEnablementStore,
        )
        database = NamiDatabase(this)
        downloadManager = NamiDownloadManager(
            context = this,
            database = database,
            sourceRegistry = sourceRegistry,
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
