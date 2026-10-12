package dev.velvet.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * A separate online account/pairing surface. The existing chat remains a LOCAL
 * preview until messages, media and offline sync have been integrated.
 *
 * Only the Supabase publishable key is in BuildConfig. Tokens are encrypted by
 * Android Keystore; no service-role secret, password or partner ID is stored.
 */
internal class VelvetPairClient(private val context: Context) {
    private val endpoint = BuildConfig.VELVET_SUPABASE_URL.trimEnd('/')
    private val publishable = BuildConfig.VELVET_SUPABASE_PUBLISHABLE_KEY
    val configured: Boolean = endpoint.startsWith("https://") &&
        publishable.isNotBlank() && runCatching { URL(endpoint).host.isNotBlank() }.getOrDefault(false)
    private val vault = RefreshTokenVault(context)
    private var accessToken: String? = null
    private var expiresAtMs: Long = 0L

    private fun sendRaw(path: String, body: JSONObject, bearer: String? = null): String {
        check(configured) { "Online pairing has not been configured in this build." }
        val connection = URL(endpoint + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("apikey", publishable)
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if(status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching {
                    val json=JSONObject(response)
                    json.optString("msg").takeIf { it.isNotBlank() }
                        ?: json.optString("message").takeIf { it.isNotBlank() }
                        ?: json.optString("error_description").takeIf { it.isNotBlank() }
                }.getOrNull()
                throw IllegalStateException(when (status) {
                    401 -> "Session expired. Please sign in again."
                    429 -> "Too many attempts. Please try again later."
                    else -> detail ?: "Connection failed (HTTP $status)."
                })
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun send(path: String, body: JSONObject, bearer: String? = null): JSONObject {
        val raw=sendRaw(path,body,bearer)
        return if(raw.trim().startsWith("{")) JSONObject(raw) else JSONObject()
    }

    private fun activeToken(): String {
        accessToken?.takeIf {System.currentTimeMillis()<expiresAtMs}?.let {return it}
        val refresh=vault.read() ?: throw IllegalStateException("Please sign in again.")
        val result=send("/auth/v1/token?grant_type=refresh_token",JSONObject().put("refresh_token",refresh))
        if(!acceptAuth(result)) throw IllegalStateException("Please sign in again.")
        return accessToken ?: throw IllegalStateException("Please sign in again.")
    }

    private fun acceptAuth(body: JSONObject): Boolean {
        val newAccess = body.optString("access_token")
        val newRefresh = body.optString("refresh_token")
        if (newAccess.isBlank() || newRefresh.isBlank()) return false
        accessToken = newAccess
        expiresAtMs = System.currentTimeMillis() + (body.optLong("expires_in",3600L)-60L).coerceAtLeast(1L)*1000L
        vault.write(newRefresh)
        return true
    }

    suspend fun hasSession(): Boolean = withContext(Dispatchers.IO) {
        if (accessToken!=null && System.currentTimeMillis()<expiresAtMs) true
        else restore()
    }

    suspend fun restore(): Boolean = withContext(Dispatchers.IO) {
        val refresh = vault.read() ?: return@withContext false
        runCatching {
            acceptAuth(send("/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", refresh)))
        }.getOrElse { error ->
            val revoked=error.message?.let {message ->
                message.contains("Invalid Refresh Token",true) ||
                    message.contains("Refresh Token Not Found",true) ||
                    message.contains("Session expired",true) ||
                    message.contains("Invalid JWT",true)
            } ?: false
            if(revoked) {
                vault.clear()
                accessToken=null
                false
            } else {
                // Connectivity failures and HTTP 5xx must NOT be interpreted as
                // "signed out"; the encrypted refresh token remains on device.
                throw IllegalStateException(
                    "Can't verify your saved session right now. Your account wasn't signed out.",
                    error
                )
            }
        }
    }

    suspend fun signUp(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val response = send("/auth/v1/signup",JSONObject().put("email",email.trim())
            .put("password",password))
        // Email-confirmation projects do not return tokens at sign-up.
        acceptAuth(response)
    }

    suspend fun signIn(email: String,password: String): Unit = withContext(Dispatchers.IO) {
        val response = send("/auth/v1/token?grant_type=password",
            JSONObject().put("email",email.trim()).put("password",password))
        check(acceptAuth(response)) { "Sign-in did not return an active session." }
    }

    suspend fun myPair(): JSONObject? = withContext(Dispatchers.IO) {
        val data = rpc("velvet_my_pair")
        if(data.length()==0) null else data
    }
    suspend fun getAnniversary(): JSONObject? = withContext(Dispatchers.IO) {
        val result=rpc("velvet_get_anniversary")
        if(result.length()==0) null else result
    }

    suspend fun setAnniversary(date:String): JSONObject = withContext(Dispatchers.IO) {
        rpc("velvet_set_anniversary",JSONObject().put("p_date",date))
    }

    suspend fun getMyProfile(): JSONObject = withContext(Dispatchers.IO) {
        rpc("velvet_get_my_profile")
    }

    suspend fun updateMyProfile(name:String,pronouns:String): JSONObject =
        withContext(Dispatchers.IO) {
            rpc("velvet_update_my_profile",JSONObject()
                .put("p_display_name",name.trim())
                .put("p_pronouns",if(pronouns.isBlank()) JSONObject.NULL else pronouns.trim()))
        }

    suspend fun createInvite(): JSONObject = withContext(Dispatchers.IO) {
        rpc("velvet_create_pair_invite")
    }
    suspend fun joinInvite(code: String): JSONObject = withContext(Dispatchers.IO) {
        rpc("velvet_join_pair_invite",JSONObject().put("p_code",code))
    }
    suspend fun revokeInvite(): Boolean = withContext(Dispatchers.IO) {
        rpcBoolean("velvet_revoke_pair_invite")
    }

    /** Upload binary only to the caller's scoped PRIVATE storage path. */
    suspend fun sendImage(
        coupleId:String,me:String,uri:android.net.Uri,
        caption:String,replyTo:String?=null
    ): JSONObject = withContext(Dispatchers.IO) {
        require(configured) {"Online image sharing isn't configured."}
        require(UUID.fromString(coupleId).toString()==coupleId)
        require(UUID.fromString(me).toString()==me)
        val mime=context.contentResolver.getType(uri)?.substringBefore(';')?.lowercase()
        val ext=when(mime) {
            "image/jpeg"->"jpg"
            "image/png"->"png"
            "image/webp"->"webp"
            "image/gif"->"gif"
            else->throw IllegalArgumentException("Choose a JPEG, PNG, WebP or GIF image.")
        }
        require(caption.length<=1200) {"Image caption is too long."}
        val buffer=java.io.ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { source->
            val chunk=ByteArray(8192)
            while(true) {
                val read=source.read(chunk)
                if(read<0) break
                buffer.write(chunk,0,read)
                require(buffer.size()<=8*1024*1024) {"Image exceeds the 8 MB limit."}
            }
        } ?: throw IllegalStateException("The chosen image can't be opened.")
        val bytes=buffer.toByteArray()
        require(bytes.isNotEmpty()) {"The image file is empty."}
        val path="$coupleId/$me/${UUID.randomUUID()}.$ext"
        val upload=URL("$endpoint/storage/v1/object/velvet-chat-media/$path")
            .openConnection() as HttpURLConnection
        try {
            upload.requestMethod="POST"
            upload.connectTimeout=25000
            upload.readTimeout=30000
            upload.doOutput=true
            upload.setFixedLengthStreamingMode(bytes.size)
            upload.setRequestProperty("apikey",publishable)
            upload.setRequestProperty("Authorization","Bearer ${activeToken()}")
            upload.setRequestProperty("Content-Type",mime)
            upload.setRequestProperty("x-upsert","false")
            upload.outputStream.use {it.write(bytes)}
            if(upload.responseCode !in 200..299) {
                val detail=upload.errorStream?.bufferedReader()?.use {it.readText()}
                    ?.take(260).orEmpty()
                throw IllegalStateException("Private upload rejected (HTTP ${upload.responseCode}). $detail")
            }
        } finally {upload.disconnect()}
        val params=JSONObject().put("p_id",UUID.randomUUID().toString())
            .put("p_storage_path",path).put("p_mime",mime)
            .put("p_bytes",bytes.size).put("p_caption",caption.trim())
        if(!replyTo.isNullOrBlank()) params.put("p_reply_to",replyTo)
        rpc("velvet_send_image",params)
    }

    /** MP4 only: strict 20 MiB upload, with no re-encoding or background queue. */
    suspend fun sendVideo(
        coupleId:String,me:String,uri:android.net.Uri,
        caption:String,replyTo:String?=null
    ): JSONObject = withContext(Dispatchers.IO) {
        require(configured) {"Online video sharing isn't configured."}
        require(UUID.fromString(coupleId).toString()==coupleId)
        require(UUID.fromString(me).toString()==me)
        val mime=context.contentResolver.getType(uri)
            ?.substringBefore(';')?.lowercase()
        require(mime=="video/mp4") {
            "Choose an MP4 video. Other video formats aren't supported yet."
        }
        require(caption.length<=1200) {"Video caption must be under 1,200 characters."}
        val retriever=android.media.MediaMetadataRetriever()
        val durationMs=try {
            retriever.setDataSource(context,uri)
            retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toIntOrNull() ?: 0
        } finally {
            retriever.release()
        }
        require(durationMs in 100..120000) {
            "Choose a video between 0.1 and 120 seconds."
        }
        val stream=java.io.ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use {source->
            val chunk=ByteArray(8192)
            while(true) {
                val n=source.read(chunk)
                if(n<0)break
                stream.write(chunk,0,n)
                require(stream.size()<=20971520) {
                    "Video is larger than 20 MB. Choose a shorter clip."
                }
            }
        } ?: throw IllegalStateException("The selected video can't be opened.")
        val bytes=stream.toByteArray()
        require(bytes.isNotEmpty()) {"The chosen video is empty."}
        val path="$coupleId/$me/${UUID.randomUUID()}.mp4"
        val upload=URL("$endpoint/storage/v1/object/velvet-chat-video/$path")
            .openConnection() as HttpURLConnection
        try {
            upload.requestMethod="POST"
            upload.connectTimeout=25000
            upload.readTimeout=90000
            upload.doOutput=true
            upload.setFixedLengthStreamingMode(bytes.size)
            upload.setRequestProperty("apikey",publishable)
            upload.setRequestProperty("Authorization","Bearer ${activeToken()}")
            upload.setRequestProperty("Content-Type","video/mp4")
            upload.setRequestProperty("x-upsert","false")
            upload.outputStream.use{it.write(bytes)}
            if(upload.responseCode !in 200..299) {
                throw IllegalStateException(
                    "Private video upload rejected (HTTP ${upload.responseCode})."
                )
            }
        } finally {upload.disconnect()}
        val params=JSONObject().put("p_id",UUID.randomUUID().toString())
            .put("p_storage_path",path).put("p_bytes",bytes.size)
            .put("p_duration_ms",durationMs)
            .put("p_caption",caption.trim())
        if(!replyTo.isNullOrBlank())params.put("p_reply_to",replyTo)
        rpc("velvet_send_video",params)
    }

    /** Stream authenticated MP4 into an app-private temporary file. Caller deletes it. */
    suspend fun downloadVideo(storagePath:String, destination:java.io.File):java.io.File =
        withContext(Dispatchers.IO) {
            val downloadContext=currentCoroutineContext()
            require(storagePath.matches(Regex(
                "[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\\.mp4"
            ))) {"Invalid private video path."}
            val file=destination
            val connection=URL(
                "$endpoint/storage/v1/object/authenticated/velvet-chat-video/$storagePath"
            ).openConnection() as HttpURLConnection
            try {
                connection.requestMethod="GET"
                connection.connectTimeout=20000
                connection.readTimeout=90000
                connection.setRequestProperty("apikey",publishable)
                connection.setRequestProperty("Authorization","Bearer ${activeToken()}")
                if(connection.responseCode !in 200..299) {
                    throw IllegalStateException(
                        "Private video unavailable (HTTP ${connection.responseCode})."
                    )
                }
                downloadContext.ensureActive()
                connection.inputStream.use {input->
                    downloadContext.ensureActive()
                    file.outputStream().use {output->
                        val chunk=ByteArray(8192)
                        var count=0L
                        while(true) {
                            downloadContext.ensureActive()
                            val n=input.read(chunk)
                            if(n<0)break
                            count+=n
                            require(count<=20971520) {
                                "Video exceeds the private 20 MB limit."
                            }
                            output.write(chunk,0,n)
                        }
                        check(count>0) {"The video file was empty."}
                    }
                }
                downloadContext.ensureActive()
                file
            } catch(e:Exception) {
                file.delete()
                throw e
            } finally {
                connection.disconnect()
            }
        }

    /** Send an AAC voice note to the PRIVATE couple Storage bucket. */
    suspend fun sendVoice(
        coupleId:String,me:String,recording:java.io.File,
        durationMs:Int,waveform:List<Int>,replyTo:String?=null
    ): JSONObject = withContext(Dispatchers.IO) {
        require(configured) {"Online voice sharing isn't configured."}
        require(UUID.fromString(coupleId).toString()==coupleId)
        require(UUID.fromString(me).toString()==me)
        require(durationMs in 500..120000) {"Voice notes must be 0.5–120 seconds."}
        require(waveform.size<=42 && waveform.all{it in 0..100})
        require(recording.isFile && recording.length() in 1..5242880) {
            "The voice note is empty or exceeds 5 MB."
        }
        val path="$coupleId/$me/${UUID.randomUUID()}.m4a"
        val bytes=recording.readBytes()
        val upload=URL("$endpoint/storage/v1/object/velvet-chat-voice/$path")
            .openConnection() as HttpURLConnection
        try {
            upload.requestMethod="POST"
            upload.connectTimeout=20000
            upload.readTimeout=30000
            upload.doOutput=true
            upload.setFixedLengthStreamingMode(bytes.size)
            upload.setRequestProperty("apikey",publishable)
            upload.setRequestProperty("Authorization","Bearer ${activeToken()}")
            upload.setRequestProperty("Content-Type","audio/mp4")
            upload.setRequestProperty("x-upsert","false")
            upload.outputStream.use {it.write(bytes)}
            if(upload.responseCode !in 200..299) {
                throw IllegalStateException(
                    "Voice upload rejected (HTTP ${upload.responseCode})."
                )
            }
        } finally {upload.disconnect()}
        val samples=JSONArray().apply{waveform.forEach{put(it)}}
        val args=JSONObject().put("p_id",UUID.randomUUID().toString())
            .put("p_storage_path",path).put("p_bytes",bytes.size)
            .put("p_duration_ms",durationMs).put("p_waveform",samples)
        if(!replyTo.isNullOrBlank())args.put("p_reply_to",replyTo)
        rpc("velvet_send_voice",args)
    }

    /** Authenticated GET; the Storage SELECT policy validates pairing + visibility. */
    suspend fun downloadVoice(storagePath:String):ByteArray = withContext(Dispatchers.IO) {
        require(storagePath.matches(Regex(
            "[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\\.m4a"
        ))) {"Invalid voice path."}
        val con=URL("$endpoint/storage/v1/object/authenticated/velvet-chat-voice/$storagePath")
            .openConnection() as HttpURLConnection
        try {
            con.requestMethod="GET"
            con.connectTimeout=15000
            con.readTimeout=30000
            con.setRequestProperty("apikey",publishable)
            con.setRequestProperty("Authorization","Bearer ${activeToken()}")
            if(con.responseCode !in 200..299) {
                throw IllegalStateException(
                    "Voice note unavailable (HTTP ${con.responseCode})."
                )
            }
            val out=java.io.ByteArrayOutputStream()
            con.inputStream.use {source->
                val chunk=ByteArray(8192)
                while(true) {
                    val n=source.read(chunk)
                    if(n<0)break
                    out.write(chunk,0,n)
                    require(out.size()<=5242880) {"Voice note exceeds 5 MB."}
                }
            }
            out.toByteArray()
        } finally {con.disconnect()}
    }

    /** Never create a public URL: private downloads carry the authenticated JWT. */
    suspend fun downloadImage(storagePath:String):ByteArray = withContext(Dispatchers.IO) {
        require(storagePath.matches(Regex(
            "[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\\.(jpg|png|webp|gif)"
        ))) {"Invalid private image path."}
        val con=URL("$endpoint/storage/v1/object/authenticated/velvet-chat-media/$storagePath")
            .openConnection() as HttpURLConnection
        try {
            con.requestMethod="GET"
            con.connectTimeout=15000
            con.readTimeout=30000
            con.setRequestProperty("apikey",publishable)
            con.setRequestProperty("Authorization","Bearer ${activeToken()}")
            if(con.responseCode !in 200..299) {
                throw IllegalStateException("Photo unavailable (HTTP ${con.responseCode}).")
            }
            val out=java.io.ByteArrayOutputStream()
            con.inputStream.use {src->
                val chunk=ByteArray(8192)
                while(true) {
                    val count=src.read(chunk)
                    if(count<0)break
                    out.write(chunk,0,count)
                    require(out.size()<=8*1024*1024) {"Photo exceeds the 8 MB limit."}
                }
            }
            out.toByteArray()
        } finally {con.disconnect()}
    }

    /** Acknowledges the newest partner-authored Chat bubble actually on screen. */
    suspend fun markChatRead(messageId:String): JSONObject = withContext(Dispatchers.IO) {
        rpc("velvet_mark_chat_read",JSONObject().put("p_message_id",messageId))
    }

    /** Partner cursor is returned only to the signed-in member of this pair. */
    suspend fun chatReadState(): JSONObject = withContext(Dispatchers.IO) {
        rpc("velvet_chat_read_state")
    }

    suspend fun editText(id:String,body:String): JSONObject = withContext(Dispatchers.IO) {
        rpc("velvet_edit_text",JSONObject().put("p_id",id).put("p_body",body))
    }

    suspend fun deleteText(id:String,everyone:Boolean): JSONObject = withContext(Dispatchers.IO) {
        rpc("velvet_delete_text",JSONObject()
            .put("p_id",id).put("p_for_everyone",everyone))
    }

    /** Private Chat Info gallery, newest photos first; never an Our Story copy. */
    suspend fun chatPhotos(
        beforeAt:String?=null,beforeId:String?=null,limit:Int=30
    ): JSONArray = withContext(Dispatchers.IO) {
        val payload=JSONObject().put("p_limit",limit.coerceIn(1,60))
        if(!beforeAt.isNullOrBlank() && !beforeId.isNullOrBlank()) {
            payload.put("p_before_at",beforeAt).put("p_before_id",beforeId)
        }
        JSONArray(sendRaw("/rest/v1/rpc/velvet_chat_photos",payload,activeToken()))
    }

    /** Video gallery items are scoped to the signed-in member's current couple. */
    suspend fun chatVideos(
        beforeAt:String?=null,beforeId:String?=null,limit:Int=18
    ): JSONArray = withContext(Dispatchers.IO) {
        val payload=JSONObject().put("p_limit",limit.coerceIn(1,60))
        if(!beforeAt.isNullOrBlank() && !beforeId.isNullOrBlank()) {
            payload.put("p_before_at",beforeAt).put("p_before_id",beforeId)
        }
        JSONArray(sendRaw("/rest/v1/rpc/velvet_chat_videos",payload,activeToken()))
    }

    /** Newest private voice notes in this exact paired Chat (not Our Story). */
    suspend fun chatAudio(
        beforeAt:String?=null,beforeId:String?=null,limit:Int=20
    ): JSONArray = withContext(Dispatchers.IO) {
        val payload=JSONObject().put("p_limit",limit.coerceIn(1,60))
        if(!beforeAt.isNullOrBlank() && !beforeId.isNullOrBlank()) {
            payload.put("p_before_at",beforeAt).put("p_before_id",beforeId)
        }
        JSONArray(sendRaw("/rest/v1/rpc/velvet_chat_audio",payload,activeToken()))
    }

    /** Search literal text/captions in the caller's current paired space. */
    suspend fun searchChat(
        query:String,beforeAt:String?=null,beforeId:String?=null,limit:Int=40
    ): JSONArray = withContext(Dispatchers.IO) {
        require(query.trim().length in 2..64) {"Use 2–64 characters to search."}
        val payload=JSONObject().put("p_query",query.trim())
            .put("p_limit",limit.coerceIn(1,60))
        if(!beforeAt.isNullOrBlank() && !beforeId.isNullOrBlank()) {
            payload.put("p_before_at",beforeAt).put("p_before_id",beforeId)
        }
        JSONArray(sendRaw("/rest/v1/rpc/velvet_search_chat",payload,activeToken()))
    }

    suspend fun listTextChanges(afterId:Long): JSONArray = withContext(Dispatchers.IO) {
        val raw=sendRaw("/rest/v1/rpc/velvet_list_text_changes",
            JSONObject().put("p_after_id",afterId).put("p_limit",100),activeToken())
        JSONArray(raw)
    }

    suspend fun sendText(id:UUID, body:String,replyTo:String?=null): JSONObject =
        withContext(Dispatchers.IO) {
            val params=JSONObject().put("p_id",id.toString()).put("p_body",body)
            if(!replyTo.isNullOrBlank()) params.put("p_reply_to",replyTo)
            rpc("velvet_send_text",params)
        }

    suspend fun pageText(beforeAt:String?,beforeId:String?,limit:Int=80): JSONArray =
        withContext(Dispatchers.IO) {
            val params=JSONObject().put("p_limit",limit.coerceIn(1,100))
            if(beforeAt!=null && beforeId!=null) {
                params.put("p_before_at",beforeAt).put("p_before_id",beforeId)
            }
            val raw=sendRaw("/rest/v1/rpc/velvet_page_chat",params,activeToken())
            JSONArray(raw)
        }

    suspend fun listText(afterAt:String?=null,afterId:String?=null): JSONArray =
        withContext(Dispatchers.IO) {
            val params=JSONObject()
            if(afterAt!=null && afterId!=null) {
                params.put("p_after_at",afterAt).put("p_after_id",afterId)
            }
            val raw=sendRaw("/rest/v1/rpc/velvet_list_chat",params,activeToken())
            JSONArray(raw)
        }

    private fun rpc(name:String, data:JSONObject=JSONObject()): JSONObject {
        return send("/rest/v1/rpc/$name",data,activeToken())
    }
    private fun rpcBoolean(name:String): Boolean {
        val bearer=activeToken()
        // Supabase PostgREST returns a primitive JSON boolean for this RPC.
        val response = URL(endpoint+"/rest/v1/rpc/$name").openConnection() as HttpURLConnection
        try {
            response.requestMethod="POST"
            response.connectTimeout=15000
            response.readTimeout=15000
            response.doOutput=true
            response.setRequestProperty("apikey",publishable)
            response.setRequestProperty("Authorization","Bearer $bearer")
            response.setRequestProperty("Content-Type","application/json")
            response.outputStream.use { it.write("{}".toByteArray()) }
            val status=response.responseCode
            if(status !in 200..299) throw IllegalStateException("Could not revoke invitation (HTTP $status).")
            return response.inputStream.bufferedReader().use {it.readText()}.trim()=="true"
        } finally {response.disconnect()}
    }
    suspend fun signOut() = withContext(Dispatchers.IO) {
        // Attempt server-side revocation, but always remove the local credential,
        // including when connectivity is unavailable during sign-out.
        try { if(configured && vault.read()!=null)
            send("/auth/v1/logout",JSONObject(),activeToken())
        } catch (_:Exception) { /* Offline logout is local-only until JWT expiry. */ }
        finally {
            accessToken=null
            expiresAtMs=0L
            vault.clear()
        }
    }
}

/** AES-GCM envelope backed by a non-exportable Android Keystore key. */
private class RefreshTokenVault(context:Context) {
    private val prefs=context.getSharedPreferences("velvet_secure_session",Context.MODE_PRIVATE)
    private val alias="velvet_refresh_v1"
    private fun key():java.security.Key {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        ks.getKey(alias,null)?.let { return it }
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    fun write(token:String) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val packet=cipher.iv+cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("token",Base64.encodeToString(packet,Base64.NO_WRAP)).apply()
    }
    fun read():String? {
        val payload=prefs.getString("token",null) ?: return null
        return runCatching {
            val bytes=Base64.decode(payload,Base64.NO_WRAP)
            require(bytes.size>28)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
        }.getOrElse { clear();null }
    }
    fun clear(){ prefs.edit().remove("token").apply() }
}

@Composable
internal fun VelvetPairingScreen(
    onBack:()->Unit,onConnectedChat:()->Unit,sharedClient:VelvetPairClient?=null
) {
    val ctx=LocalContext.current
    val rememberedApi=remember { VelvetPairClient(ctx.applicationContext) }
    val api=sharedClient ?: rememberedApi
    val scope=rememberCoroutineScope()
    val clipboard=LocalClipboardManager.current
    var loading by remember { mutableStateOf(api.configured) }
    var signedIn by remember { mutableStateOf(false) }
    var newAccount by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf("") }
    var pair by remember { mutableStateOf<JSONObject?>(null) }
    var notice by remember { mutableStateOf("") }
    val rose=Color(0xFFDDA1B7)
    val muted=Color(0xFFD1B7C1)
    fun runAction(action:suspend ()->Unit) {
        if(loading)return
        loading=true
        scope.launch {
            try { action() }
            catch(t:Exception) { notice=t.message ?: "Could not connect. Try again." }
            finally { loading=false }
        }
    }
    LaunchedEffect(Unit) {
        if(api.configured) {
            try {
                signedIn=api.hasSession()
                if(signedIn) {
                    pair=runCatching {api.myPair()}.getOrElse {error->
                        notice=error.message ?: "Can't load pairing status while offline."
                        null
                    }
                }
            } catch(error:Exception) {
                notice=error.message ?: "Could not check your saved account."
            } finally {loading=false}
        }
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF171019))
            .verticalScroll(rememberScrollState()).padding(horizontal=22.dp, vertical=18.dp)
    ) {
        TextButton(onClick=onBack) { Text("← Back to Velvet",color=rose) }
        Spacer(Modifier.height(18.dp))
        Text("Our little world,\nconnected.",fontSize=31.sp,lineHeight=37.sp,
            fontWeight=FontWeight.SemiBold,color=Color(0xFFFFE3EA))
        Spacer(Modifier.height(12.dp))
        Text("Two accounts. One private space. Invitation-only pairing.",
            color=muted,fontSize=14.sp)
        Spacer(Modifier.height(20.dp))
        if(!api.configured) {
            Text("Online pairing is not configured in this APK.",color=rose,fontSize=17.sp)
            Spacer(Modifier.height(10.dp))
            Text("An administrator needs to deploy the Velvet migrations and set the Supabase URL and publishable key in the build. Offline preview remains available.",
                color=muted)
        } else if(!signedIn) {
            Text(if(newAccount) "Create your account" else "Sign in",
                color=rose,fontSize=21.sp,fontWeight=FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(email,{ email=it },label={Text("Email")},singleLine=true,
                modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(password,{password=it},label={Text("Password")},
                singleLine=true,visualTransformation=PasswordVisualTransformation(),
                modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Button(enabled=!loading && email.isNotBlank() && password.length>=6,
                onClick={
                    runAction {
                        if(newAccount) {
                            signedIn=api.signUp(email,password)
                            password=""
                            notice=if(signedIn) "Account created. You are signed in."
                                   else "Check your email to confirm your account, then sign in."
                        } else {
                            api.signIn(email,password)
                            signedIn=true
                            pair=api.myPair()
                            password=""
                            notice="Signed in. Your online space is ready to pair."
                        }
                    }
                },modifier=Modifier.fillMaxWidth()){
                Text(if(loading) "Connecting…" else if(newAccount) "Create account" else "Sign in")
            }
            TextButton(onClick={newAccount=!newAccount},enabled=!loading) {
                Text(if(newAccount) "Already registered? Sign in" else "New to Velvet? Create an account",color=rose)
            }
        } else {
            val count=pair?.optInt("member_count",0) ?: 0
            val partner=pair?.optString("partner_name","")?.takeUnless {it.isBlank() || it=="null"}
            if(count==2) {
                Text("♥ You're paired",color=rose,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(9.dp))
                Text("Your space now has two members"+(partner?.let{": $it"} ?: "."),
                    color=muted)
                Spacer(Modifier.height(7.dp))
                Text("Pairing is ready. Connected Chat supports text, photos, voice notes and MP4 clips. Offline demos remain separate.",
                    color=muted,fontSize=12.sp)
                Spacer(Modifier.height(15.dp))
                Button(onClick=onConnectedChat,enabled=!loading,modifier=Modifier.fillMaxWidth()) {
                    Text("Open connected chat →")
                }
            } else {
                Text("Connect with your person",color=rose,fontSize=21.sp)
                Spacer(Modifier.height(8.dp))
                Text("Create a one-time code and send it privately, or enter the code they sent you. Codes expire after 20 minutes.",
                    color=muted,fontSize=13.sp)
                Spacer(Modifier.height(13.dp))
                Button(onClick={
                    runAction {
                        val result=api.createInvite()
                        invitation=result.optString("code")
                        pair=api.myPair()
                        notice="Fresh code generated. Any previous code is invalid."
                    }
                },enabled=!loading,modifier=Modifier.fillMaxWidth()){
                    Text(if(loading) "Working…" else "Generate invitation code")
                }
                if(invitation.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(invitation,color=Color.White,fontSize=23.sp,letterSpacing=1.5.sp)
                    TextButton(onClick={clipboard.setText(AnnotatedString(invitation))}){
                        Text("Copy code",color=rose)
                    }
                    TextButton(onClick={
                        runAction {
                            api.revokeInvite()
                            invitation=""
                            notice="Invitation revoked."
                        }
                    },enabled=!loading){Text("Revoke this code",color=muted)}
                }
                Spacer(Modifier.height(18.dp))
                Text("Have their code?",color=muted)
                OutlinedTextField(code,{code=it.uppercase()},label={Text("Invitation code")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                Spacer(Modifier.height(9.dp))
                OutlinedButton(onClick={
                    runAction {
                        val outcome=api.joinInvite(code)
                        if(outcome.optBoolean("ok")) {
                            pair=api.myPair()
                            invitation=""
                            code=""
                            notice="You've connected your two accounts ♥"
                        } else notice=when(outcome.optString("error")) {
                            "rate_limited" -> "Too many guesses. Try again in an hour."
                            "already_paired" -> "This account is already in a couple."
                            else -> "That code is invalid, expired or already used."
                        }
                    }
                },enabled=!loading && code.isNotBlank(),modifier=Modifier.fillMaxWidth()){
                    Text("Join their space")
                }
            }
            Spacer(Modifier.height(22.dp))
            TextButton(onClick={
                runAction {pair=api.myPair();notice="Pairing status refreshed."}
            },enabled=!loading){Text("Refresh pairing status",color=rose)}
            TextButton(onClick={
                runAction {
                    api.signOut();signedIn=false;pair=null;invitation="";code=""
                    notice="Signed out of this phone."
                }
            },enabled=!loading){Text("Sign out",color=muted)}
        }
        if(notice.isNotEmpty()) {
            Spacer(Modifier.height(17.dp))
            Text(notice,color=rose,fontSize=13.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text("Send through connected Chat to share with your partner. Offline Chat, games and Our Story stay on this phone; they are not automatically shared.",
            color=muted,fontSize=11.sp,lineHeight=17.sp)
        Spacer(Modifier.height(20.dp))
    }
}
