package app.nami.android

import android.app.Application
import app.nami.compat.aniyomi.AniyomiExtensionHost

class NamiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AniyomiExtensionHost.initialize(this)
    }
}
