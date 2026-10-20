package app.yomi.reader.core

import java.io.Closeable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Transfers an opened resource to [accept], closing it if cancellation wins the dispatcher handoff. */
suspend fun <T : Closeable> loadOwnedResource(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    open: suspend () -> T,
    accept: (T) -> Unit,
) {
    var pending: T? = null
    try {
        // Do not return the stream from withContext: its prompt cancellation
        // guarantee can discard the result before the caller takes ownership.
        withContext(dispatcher) { pending = open() }
        accept(checkNotNull(pending))
        pending = null
    } finally {
        // Closing a failed/cancelled load must not mask the original exception.
        pending?.let { runCatching { it.close() } }
    }
}
