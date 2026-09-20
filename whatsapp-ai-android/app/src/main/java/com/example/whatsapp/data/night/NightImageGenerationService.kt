package com.example.whatsapp.data.night

import android.content.Context
import android.util.Base64
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class NightGeneratedImage(
    val localPath: String,
    val mimeType: String,
    val prompt: String,
)

class NightImageGenerationService private constructor(
    private val context: Context,
    private val repository: NightRepository,
    private val router: NightCapabilityRouter,
    private val secrets: NightSecretStore,
    private val http: OkHttpClient,
) {
    suspend fun generate(
        chatId: String,
        prompt: String,
        size: String = "1024x1024",
    ): Result<NightGeneratedImage> = withContext(Dispatchers.IO) {
        runCatching {
            require(prompt.isNotBlank()) { "Image prompt cannot be empty." }

            val resolved = router.resolveCapability(chatId, "image_generation")
                ?: error("No image-generation model is configured.")

            val provider = resolved.profile.providerType.lowercase()
            require(provider == "azure") {
                "The configured image model is not on a supported image-generation provider."
            }

            val key = secrets.get(resolved.profile.secretAlias)
                ?: error("The image provider API key is missing.")

            val endpoint = imageEndpoint(resolved.profile)
            val body = JSONObject()
                .put("model", resolved.model.deploymentName ?: resolved.model.modelId)
                .put("prompt", prompt.trim())
                .put("size", normalizeSize(size))
                .put("n", 1)

            val request = Request.Builder()
                .url(endpoint)
                .post(
                    body.toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .header("Content-Type", "application/json")
                .header("api-key", key)
                .build()

            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Image generation failed (" + response.code + "): " + extractError(raw))
                }

                val data = JSONObject(raw).optJSONArray("data")
                    ?: error("Image provider returned no image data.")
                val first = data.optJSONObject(0)
                    ?: error("Image provider returned an empty image result.")

                val bytes = when {
                    first.optString("b64_json").isNotBlank() ->
                        Base64.decode(first.optString("b64_json"), Base64.DEFAULT)
                    first.optString("url").isNotBlank() ->
                        download(first.optString("url"))
                    else -> error("Image provider did not return image bytes or a URL.")
                }

                require(bytes.isNotEmpty()) { "Generated image was empty." }
                require(bytes.size.toLong() <= MAX_IMAGE_BYTES) {
                    "Generated image is too large to keep safely on-device."
                }

                val dir = File(context.filesDir, "night_generated").apply { mkdirs() }
                val output = File(dir, "night_image_" + UUID.randomUUID() + ".png")
                output.writeBytes(bytes)

                NightGeneratedImage(
                    localPath = output.absolutePath,
                    mimeType = "image/png",
                    prompt = prompt.trim(),
                )
            }
        }
    }

    private fun imageEndpoint(profile: NightProviderProfileEntity): String {
        val base = requireNotNull(profile.endpoint) {
            "Azure image profile needs an endpoint."
        }.trimEnd('/')

        return when {
            base.endsWith("/openai/v1") -> base + "/images/generations"
            base.contains("/openai/v1/") ->
                base.substringBefore("/openai/v1/") + "/openai/v1/images/generations"
            else -> base + "/openai/v1/images/generations"
        }
    }

    private fun normalizeSize(value: String): String =
        when (value.trim().lowercase()) {
            "1024x1024", "1024x1536", "1536x1024", "1024x1792", "1792x1024" ->
                value.trim().lowercase()
            else -> "1024x1024"
        }

    private fun download(url: String): ByteArray {
        val safeUrl = validatePublicImageUrl(url)
        val request = Request.Builder().url(safeUrl).get().build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Could not download generated image (" + response.code + ").")
            }

            val contentType = response.header("Content-Type").orEmpty().lowercase()
            require(contentType.isBlank() || contentType.startsWith("image/")) {
                "Image provider returned a non-image download."
            }

            val body = response.body ?: error("Generated image download was empty.")
            val declaredLength = body.contentLength()
            require(declaredLength < 0L || declaredLength <= MAX_IMAGE_BYTES) {
                "Generated image download is too large."
            }

            val source = body.source()
            source.request(MAX_IMAGE_BYTES + 1L)
            require(source.buffer.size <= MAX_IMAGE_BYTES) {
                "Generated image download is too large."
            }
            source.readByteArray(source.buffer.size)
        }
    }

    private fun validatePublicImageUrl(value: String): String {
        val uri = URI(value.trim())
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Generated image downloads must use HTTPS."
        }
        val host = uri.host?.lowercase().orEmpty()
        require(host.isNotBlank() && host != "localhost" && !host.endsWith(".local")) {
            "Generated image URL has an invalid host."
        }
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }
            .getOrElse { error("Generated image host could not be resolved.") }
        require(addresses.isNotEmpty() && addresses.none { address ->
            address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress
        }) {
            "Generated image URL points to a private or local network."
        }
        return value.trim()
    }

    private fun extractError(raw: String): String =
        runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: raw.take(300)
        }.getOrElse { raw.take(300) }

    companion object {
        private const val MAX_IMAGE_BYTES = 24L * 1024L * 1024L

        @Volatile private var instance: NightImageGenerationService? = null

        fun get(context: Context): NightImageGenerationService =
            instance ?: synchronized(this) {
                val app = context.applicationContext
                val repository = NightRepository.get(app)
                instance ?: NightImageGenerationService(
                    context = app,
                    repository = repository,
                    router = NightCapabilityRouter(repository),
                    secrets = NightSecretStore.get(app),
                    http = OkHttpClient.Builder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .callTimeout(180, TimeUnit.SECONDS)
                        .build(),
                ).also { instance = it }
            }
    }
}
