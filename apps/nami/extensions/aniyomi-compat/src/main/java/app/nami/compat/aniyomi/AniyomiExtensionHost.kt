package app.nami.compat.aniyomi

import android.app.Application
import dev.mihon.injekt.patchInjekt
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton

object AniyomiExtensionHost {
    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize(application: Application) {
        if (initialized) return

        // Aniyomi patches the original Injekt registrar before importing host modules.
        // The same step is required here so extension bytecode sees the registry behavior it expects.
        patchInjekt()

        val network = NetworkHelper(application)
        Injekt.importModule(
            object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    addSingleton(application)
                    addSingleton(network)
                }
            },
        )
        initialized = true
    }
}
