package app.nami.compat.aniyomi

import dalvik.system.PathClassLoader
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.Enumeration

/**
 * Parent-last extension classloader matching Aniyomi's ordering:
 * system classes -> extension APK -> Nami parent.
 */
internal class ChildFirstPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    private val systemLoader: ClassLoader? = getSystemClassLoader()

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        var loaded = findLoadedClass(name)

        if (loaded == null && systemLoader != null) {
            loaded = try {
                systemLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {
                null
            }
        }

        if (loaded == null) {
            loaded = try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                super.loadClass(name, resolve)
            }
        }

        if (resolve) resolveClass(loaded)
        return loaded
    }

    override fun getResource(name: String?): URL? =
        systemLoader?.getResource(name)
            ?: findResource(name)
            ?: super.getResource(name)

    override fun getResources(name: String?): Enumeration<URL> {
        val systemUrls = systemLoader?.getResources(name)
        val localUrls = findResources(name)
        val parentUrls = parent?.getResources(name)

        val urls = buildList {
            while (systemUrls?.hasMoreElements() == true) add(systemUrls.nextElement())
            while (localUrls.hasMoreElements()) add(localUrls.nextElement())
            while (parentUrls?.hasMoreElements() == true) add(parentUrls.nextElement())
        }

        return object : Enumeration<URL> {
            private val iterator = urls.iterator()
            override fun hasMoreElements(): Boolean = iterator.hasNext()
            override fun nextElement(): URL = iterator.next()
        }
    }

    override fun getResourceAsStream(name: String?): InputStream? =
        try {
            getResource(name)?.openStream()
        } catch (_: IOException) {
            null
        }
}
