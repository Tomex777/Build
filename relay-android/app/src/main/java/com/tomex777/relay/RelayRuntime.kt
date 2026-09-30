package com.tomex777.relay

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.tomex.relay.data.AndroidAtomicRelayPersistence
import com.tomex.relay.data.DefaultRelayDataSource
import com.tomex.relay.data.RelayDataSource
import com.tomex.relay.data.RelaySnapshot
import com.tomex.relay.data.RelaySubscription
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-scoped Relay data runtime.
 *
 * Persistence work must outlive an individual Activity so a configuration change cannot cancel
 * a queued or in-flight AtomicFile write. Activities own only their UI subscriptions.
 */
internal object RelayRuntime {
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sourceLock = Any()

    @Volatile
    private var source: RelayDataSource? = null

    fun connect(
        context: Context,
        onSnapshot: (RelaySnapshot) -> Unit,
        onError: (Throwable) -> Unit,
    ): Connection {
        val closed = AtomicBoolean(false)
        val subscription = AtomicReference<RelaySubscription?>()
        val appContext = context.applicationContext

        worker.execute {
            runCatching {
                source(appContext).observe { snapshot ->
                    if (!closed.get()) {
                        mainHandler.post {
                            if (!closed.get()) {
                                onSnapshot(snapshot)
                            }
                        }
                    }
                }
            }.onSuccess { activeSubscription ->
                if (closed.get()) {
                    activeSubscription.close()
                } else {
                    subscription.set(activeSubscription)
                    if (closed.get()) {
                        subscription.getAndSet(null)?.close()
                    }
                }
            }.onFailure { error ->
                if (!closed.get()) {
                    mainHandler.post {
                        if (!closed.get()) {
                            onError(error)
                        }
                    }
                }
            }
        }

        return Connection(closed, subscription)
    }

    fun mutate(
        context: Context,
        onError: (Throwable) -> Unit,
        block: RelayDataSource.() -> Unit,
    ) {
        val appContext = context.applicationContext
        worker.execute {
            runCatching {
                source(appContext).block()
            }.onFailure { error ->
                mainHandler.post { onError(error) }
            }
        }
    }

    private fun source(context: Context): RelayDataSource {
        source?.let { return it }
        return synchronized(sourceLock) {
            source ?: DefaultRelayDataSource(
                persistence = AndroidAtomicRelayPersistence(context.applicationContext),
            ).also { created ->
                source = created
            }
        }
    }

    internal class Connection(
        private val closed: AtomicBoolean,
        private val subscription: AtomicReference<RelaySubscription?>,
    ) {
        fun close() {
            if (closed.compareAndSet(false, true)) {
                subscription.getAndSet(null)?.close()
            }
        }
    }
}
