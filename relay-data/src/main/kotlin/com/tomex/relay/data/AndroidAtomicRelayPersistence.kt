package com.tomex.relay.data

import android.content.Context
import android.util.AtomicFile
import java.io.File

class AndroidAtomicRelayPersistence(
    context: Context,
    fileName: String = "relay-state-v1.txt",
) : RelayPersistence {
    private val file = AtomicFile(File(context.applicationContext.filesDir, fileName))

    @Synchronized
    override fun load(): RelayPersistedState? {
        if (!file.baseFile.exists()) return null
        return runCatching {
            file.openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                RelayStateCodec.decode(reader.readText())
            }
        }.getOrNull()
    }

    @Synchronized
    override fun save(state: RelayPersistedState) {
        val bytes = RelayStateCodec.encode(state).toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(bytes)
            output.flush()
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }
}
