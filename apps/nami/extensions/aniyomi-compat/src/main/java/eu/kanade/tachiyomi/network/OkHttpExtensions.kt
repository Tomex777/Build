/*
 * Nami host implementation for extensions-lib coroutine helpers.
 */
package eu.kanade.tachiyomi.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Response

class HttpException(val code: Int) : IllegalStateException("HTTP error $code")

suspend fun Call.await(): Response = withContext(Dispatchers.IO) {
    execute()
}

suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (!response.isSuccessful) {
        val code = response.code
        response.close()
        throw HttpException(code)
    }
    return response
}
