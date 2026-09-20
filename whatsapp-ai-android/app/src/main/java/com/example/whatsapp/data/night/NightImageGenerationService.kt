package com.example.whatsapp.data.night

import android.content.Context
import android.util.Base64
import java.io.File
import java.util.UUID
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
        val request = Request.Builder().url(url).get().build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Could not download generated image (" + response.code + ").")
            }
            response.body?.bytes() ?: error("Generated image download was empty.")
        }
    }

    private fun extractError(raw: String): String =
        runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: raw.take(300)
        }.getOrElse { raw.take(300) }

    companion object {
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
                    http = OkHttpClient.Builder().build(),
                ).also { instance = it }
            }
    }
}
